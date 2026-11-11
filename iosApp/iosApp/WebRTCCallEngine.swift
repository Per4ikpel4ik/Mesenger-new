import Foundation
import AVFoundation
import CoreMedia
import UIKit
import WebRTC
import Shared

// Нативный движок голосового и видеозвонка. Общий код (CallController) управляет им через
// протокол CallEngine; все колбэки в Kotlin отправляются на главном потоке.
// Контейнер для видео: отражение (зеркало своей камеры) вешаем на внутренний view и размер
// задаём через bounds/center, иначе Compose, меняя frame у отражённого view, его ломает.
final class VideoContainer: UIView {
    let inner = RTCMTLVideoView(frame: .zero)

    init(mirror: Bool) {
        super.init(frame: .zero)
        backgroundColor = .black
        clipsToBounds = true
        inner.videoContentMode = .scaleAspectFill
        addSubview(inner)
        setMirrored(mirror)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func setMirrored(_ mirrored: Bool) {
        inner.transform = mirrored ? CGAffineTransform(scaleX: -1, y: 1) : .identity
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        inner.bounds = CGRect(origin: .zero, size: bounds.size)
        inner.center = CGPoint(x: bounds.midX, y: bounds.midY)
    }
}

// Сообщает, что от собеседника пошли кадры видео.
final class FrameWatcher: NSObject, RTCVideoViewDelegate {
    var onFrame: (() -> Void)?
    func videoView(_ videoView: RTCVideoRenderer, didChangeVideoSize size: CGSize) {
        if size.width > 0 { onFrame?() }
    }
}

final class WebRTCCallEngine: NSObject, CallEngine, CallVideoViews, RTCPeerConnectionDelegate {
    private let factory = WebRTCBootstrap.factory
    private var pc: RTCPeerConnection?
    private var audioTrack: RTCAudioTrack?
    private var videoSource: RTCVideoSource?
    private var capturer: RTCCameraVideoCapturer?
    private var localVideoTrack: RTCVideoTrack?
    private var remoteVideoTrack: RTCVideoTrack?
    private var usingFront = true
    private var wantVideo = false

    // Эти view отдаются Compose (UIKitView). Создаются один раз и живут весь запуск приложения.
    private lazy var localVideo = VideoContainer(mirror: true)
    private lazy var remoteVideo = VideoContainer(mirror: false)
    private let frameWatcher = FrameWatcher()

    // MARK: - CallVideoViews
    func localView() -> UIView { localVideo }
    func remoteView() -> UIView { remoteVideo }
    private var listener: CallEngineListener?
    private var pendingCandidates: [RTCIceCandidate] = []
    private var remoteDescriptionSet = false

    // MARK: - CallEngine

    func attach(listener: CallEngineListener) {
        self.listener = listener
        frameWatcher.onFrame = { [weak self] in
            DispatchQueue.main.async { self?.listener?.onRemoteVideo(active: true) }
        }
        remoteVideo.inner.delegate = frameWatcher
    }

    func startCall(isVideo: Bool, turnUsername: String, turnPassword: String) {
        closeConnection()
        wantVideo = isVideo

        AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
            if !granted {
                DispatchQueue.main.async { self?.listener?.onError(message: "Нет доступа к микрофону. Разрешите его в настройках iPhone.") }
            }
        }

        var servers: [RTCIceServer] = [
            RTCIceServer(urlStrings: ["stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"])
        ]
        if !turnUsername.isEmpty && !turnPassword.isEmpty {
            servers.append(RTCIceServer(
                urlStrings: [
                    "turn:global.relay.metered.ca:80",
                    "turn:global.relay.metered.ca:80?transport=tcp",
                    "turn:global.relay.metered.ca:443",
                    "turns:global.relay.metered.ca:443?transport=tcp"
                ],
                username: turnUsername,
                credential: turnPassword
            ))
        }

        let config = RTCConfiguration()
        config.iceServers = servers
        config.sdpSemantics = .unifiedPlan
        config.continualGatheringPolicy = .gatherContinually

        let constraints = RTCMediaConstraints(
            mandatoryConstraints: nil,
            optionalConstraints: ["DtlsSrtpKeyAgreement": "true"]
        )
        guard let conn = factory.peerConnection(with: config, constraints: constraints, delegate: self) else {
            report("Не удалось создать соединение")
            return
        }
        pc = conn

        let audioSource = factory.audioSource(with: RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil))
        let track = factory.audioTrack(with: audioSource, trackId: "audio0")
        audioTrack = track
        conn.add(track, streamIds: ["stream0"])

        if isVideo { startLocalVideo(on: conn) }
    }

    func makeOffer() {
        guard let conn = pc else { return }
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: ["OfferToReceiveAudio": "true", "OfferToReceiveVideo": wantVideo ? "true" : "false"],
            optionalConstraints: nil
        )
        conn.offer(for: constraints) { [weak self] sdp, error in
            guard let self = self else { return }
            guard let sdp = sdp else { self.report(error?.localizedDescription ?? "Не удалось создать предложение звонка"); return }
            conn.setLocalDescription(sdp) { err in
                if let err = err { self.report(err.localizedDescription); return }
                DispatchQueue.main.async { self.listener?.onLocalSdp(type: "offer", sdp: sdp.sdp) }
            }
        }
    }

    func acceptOffer(sdp: String) {
        guard let conn = pc else { return }
        let offer = RTCSessionDescription(type: .offer, sdp: sdp)
        conn.setRemoteDescription(offer) { [weak self] error in
            guard let self = self else { return }
            if let error = error { self.report(error.localizedDescription); return }
            DispatchQueue.main.async {
                self.remoteDescriptionSet = true
                self.flushPendingCandidates()
                self.syncRemoteVideo()
            }
            let constraints = RTCMediaConstraints(
                mandatoryConstraints: ["OfferToReceiveAudio": "true", "OfferToReceiveVideo": wantVideo ? "true" : "false"],
                optionalConstraints: nil
            )
            conn.answer(for: constraints) { answer, err in
                guard let answer = answer else { self.report(err?.localizedDescription ?? "Не удалось создать ответ на звонок"); return }
                conn.setLocalDescription(answer) { e in
                    if let e = e { self.report(e.localizedDescription); return }
                    DispatchQueue.main.async { self.listener?.onLocalSdp(type: "answer", sdp: answer.sdp) }
                }
            }
        }
    }

    func applyAnswer(sdp: String) {
        guard let conn = pc else { return }
        let answer = RTCSessionDescription(type: .answer, sdp: sdp)
        conn.setRemoteDescription(answer) { [weak self] error in
            guard let self = self else { return }
            if let error = error { self.report(error.localizedDescription); return }
            DispatchQueue.main.async {
                self.remoteDescriptionSet = true
                self.flushPendingCandidates()
                self.syncRemoteVideo()
            }
        }
    }

    func addCandidate(sdpMid: String, sdpMLineIndex: Int32, candidate: String) {
        let c = RTCIceCandidate(sdp: candidate, sdpMLineIndex: sdpMLineIndex, sdpMid: sdpMid)
        if remoteDescriptionSet, let conn = pc {
            conn.add(c) { _ in }
        } else {
            pendingCandidates.append(c)
        }
    }

    func mute(muted: Bool) {
        audioTrack?.isEnabled = !muted
    }

    func speaker(on: Bool) {
        try? AVAudioSession.sharedInstance().overrideOutputAudioPort(on ? .speaker : .none)
    }

    func setCameraOff(off: Bool) {
        localVideoTrack?.isEnabled = !off
    }

    func switchCamera() {
        guard let cap = capturer else { return }
        usingFront.toggle()
        let front = usingFront
        cap.stopCapture { [weak self] in
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.localVideo.setMirrored(front)
                self.beginCapture(front: front)
            }
        }
    }

    func stopCall() {
        closeConnection()
    }

    // MARK: - Внутреннее

    private func startLocalVideo(on conn: RTCPeerConnection) {
        let source = factory.videoSource()
        videoSource = source
        let track = factory.videoTrack(with: source, trackId: "video0")
        localVideoTrack = track
        track.add(localVideo.inner)
        conn.add(track, streamIds: ["stream0"])
        capturer = RTCCameraVideoCapturer(delegate: source)
        usingFront = true
        localVideo.setMirrored(true)
        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            DispatchQueue.main.async {
                guard let self = self else { return }
                if granted {
                    self.beginCapture(front: true)
                } else {
                    self.listener?.onError(message: "Нет доступа к камере. Разрешите её в настройках iPhone.")
                }
            }
        }
    }

    private func beginCapture(front: Bool) {
        guard let cap = capturer else { return }
        let position: AVCaptureDevice.Position = front ? .front : .back
        let devices = RTCCameraVideoCapturer.captureDevices()
        guard let device = devices.first(where: { $0.position == position }) ?? devices.first else {
            report("Камера не найдена")
            return
        }
        let formats = RTCCameraVideoCapturer.supportedFormats(for: device)
        // Берём формат, ближайший к 640 px по ширине: хватает для звонка и не греет телефон.
        func width(_ f: AVCaptureDevice.Format) -> Int32 { CMVideoFormatDescriptionGetDimensions(f.formatDescription).width }
        guard let format = formats.min(by: { abs(width($0) - 640) < abs(width($1) - 640) }) else {
            report("Камера не поддерживает нужный формат")
            return
        }
        let maxFps = format.videoSupportedFrameRateRanges.map { $0.maxFrameRate }.max() ?? 30
        cap.startCapture(with: device, format: format, fps: Int(min(maxFps, 30)))
    }

    private func attachRemote(_ track: RTCMediaStreamTrack?) {
        guard let v = track as? RTCVideoTrack else { return }
        DispatchQueue.main.async {
            if self.remoteVideoTrack === v { return }
            self.remoteVideoTrack?.remove(self.remoteVideo.inner)
            self.remoteVideoTrack = v
            v.add(self.remoteVideo.inner)
        }
    }

    // В Unified Plan у звонящего трансивер создан заранее, и колбэк «трек пришёл» может не прийти:
    // после установки remote description сами берём видеотрек из трансиверов.
    private func syncRemoteVideo() {
        guard let conn = pc else { return }
        for t in conn.transceivers where t.mediaType == .video {
            attachRemote(t.receiver.track)
        }
    }

    private func flushPendingCandidates() {
        guard let conn = pc else { return }
        for c in pendingCandidates { conn.add(c) { _ in } }
        pendingCandidates.removeAll()
    }

    private func closeConnection() {
        audioTrack?.isEnabled = false
        capturer?.stopCapture()
        localVideoTrack?.remove(localVideo.inner)
        remoteVideoTrack?.remove(remoteVideo.inner)
        localVideoTrack = nil
        remoteVideoTrack = nil
        capturer = nil
        videoSource = nil
        pc?.close()
        pc = nil
        audioTrack = nil
        pendingCandidates.removeAll()
        remoteDescriptionSet = false
        try? AVAudioSession.sharedInstance().overrideOutputAudioPort(.none)
    }

    private func report(_ message: String) {
        DispatchQueue.main.async { self.listener?.onError(message: message) }
    }

    // MARK: - RTCPeerConnectionDelegate

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd rtpReceiver: RTCRtpReceiver, streams mediaStreams: [RTCMediaStream]) {
        attachRemote(rtpReceiver.track)
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didStartReceivingOn transceiver: RTCRtpTransceiver) {
        attachRemote(transceiver.receiver.track)
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}

    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {
        let name: String
        switch newState {
        case .connected: name = "CONNECTED"
        case .completed: name = "COMPLETED"
        case .failed: name = "FAILED"
        case .disconnected: name = "DISCONNECTED"
        case .closed: name = "CLOSED"
        default: name = "OTHER"
        }
        DispatchQueue.main.async {
            if newState == .connected || newState == .completed { self.syncRemoteVideo() }
            self.listener?.onConnectionState(state: name)
        }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        let mid = candidate.sdpMid ?? ""
        let index = candidate.sdpMLineIndex
        let sdp = candidate.sdp
        DispatchQueue.main.async { self.listener?.onLocalCandidate(sdpMid: mid, sdpMLineIndex: index, candidate: sdp) }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}

    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}
}
