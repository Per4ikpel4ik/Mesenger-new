import Foundation
import AVFoundation
import WebRTC
import Shared

// Нативный движок голосового звонка. Общий код (CallController) управляет им через
// протокол CallEngine; все колбэки в Kotlin отправляются на главном потоке.
final class WebRTCCallEngine: NSObject, CallEngine, RTCPeerConnectionDelegate {
    private let factory = WebRTCBootstrap.factory
    private var pc: RTCPeerConnection?
    private var audioTrack: RTCAudioTrack?
    private var listener: CallEngineListener?
    private var pendingCandidates: [RTCIceCandidate] = []
    private var remoteDescriptionSet = false

    // MARK: - CallEngine

    func attach(listener: CallEngineListener) {
        self.listener = listener
    }

    func startCall(isVideo: Bool, turnUsername: String, turnPassword: String) {
        closeConnection()

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
    }

    func makeOffer() {
        guard let conn = pc else { return }
        let constraints = RTCMediaConstraints(
            mandatoryConstraints: ["OfferToReceiveAudio": "true", "OfferToReceiveVideo": "false"],
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
            }
            let constraints = RTCMediaConstraints(
                mandatoryConstraints: ["OfferToReceiveAudio": "true", "OfferToReceiveVideo": "false"],
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

    func stopCall() {
        closeConnection()
    }

    // MARK: - Внутреннее

    private func flushPendingCandidates() {
        guard let conn = pc else { return }
        for c in pendingCandidates { conn.add(c) { _ in } }
        pendingCandidates.removeAll()
    }

    private func closeConnection() {
        audioTrack?.isEnabled = false
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
        DispatchQueue.main.async { self.listener?.onConnectionState(state: name) }
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
