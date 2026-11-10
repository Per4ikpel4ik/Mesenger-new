package com.rosechat.data.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.rosechat.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "CallManager"

// Хлебные крошки в Crashlytics — если крашится нативный код WebRTC (а не Kotlin,
// который try/catch уже видел бы), это единственный способ понять, на каком именно
// шаге это случилось: последняя записанная крошка в отчёте Crashlytics и есть ответ.
fun callCrumb(msg: String) {
    Log.d(TAG, msg)
    runCatching { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().log(msg) }
}

/**
 * Тонкая обёртка над WebRTC (org.webrtc, библиотека io.getstream:stream-webrtc-android —
 * актуальный открытый форк гугловской библиотеки, которая сама по себе в Maven Central
 * больше не публикуется). Отвечает только за медиа (аудио/видео peer-to-peer) и SDP/ICE
 * механику; сам обмен offer/answer/кандидатами между двумя телефонами идёт через
 * Firestore — см. FirebaseRepo и CallViewModel в ui/calls/CallsScreen.kt.
 *
 * Бесплатно целиком: сама библиотека open-source, STUN — публичные сервера Google,
 * TURN (резерв на случай, если P2P не пробивается через NAT оператора) — Open Relay
 * Project, тоже бесплатный. Ни то ни другое не завязано на тариф Firebase.
 */
class CallManager(private val context: Context) {

    private val eglBase: EglBase = EglBase.create()

    private var factory: PeerConnectionFactory? = null
    var peerConnection: PeerConnection? = null
        private set

    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private var released = false
    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Без явного запроса фокуса некоторые прошивки (MIUI, One UI и т.п.) не гарантируют,
    // что захват с микрофона / вывод на динамик реально включатся для голосового тракта,
    // даже когда AudioDeviceModule и mode=MODE_IN_COMMUNICATION уже настроены правильно —
    // ещё один кандидат на "звонок идёт, а звука нет ни у кого".
    private fun requestAudioFocus() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        }.onFailure { Log.e(TAG, "requestAudioFocus failed", it) }
    }

    private fun abandonAudioFocus() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        }
    }

    // STUN — публичные сервера Google, всегда бесплатно и без ключа.
    private val stunServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    // Резервные TURN-данные — общие для всех, используются только если личные не заданы
    // в local.properties. Это тот же самый бесплатный Open Relay Project, только общие
    // данные, а не привязанные к личному аккаунту — именно на них раньше и упирались
    // звонки (см. историю разработки).
    private val fallbackTurnServers = listOf(
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
            .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
    )

    // Личные TURN-данные (см. local.properties → TURN_USERNAME/TURN_PASSWORD) — та же
    // самая бессрочная пара логин/пароль, которую можно скопировать из Metered Dashboard
    // → TURN Server → "Show ICE Servers Array". Никакого сетевого запроса не нужно —
    // это статические данные, не токен с истечением срока.
    private val personalTurnServers: List<PeerConnection.IceServer> by lazy {
        val user = BuildConfig.TURN_USERNAME
        val pass = BuildConfig.TURN_PASSWORD
        if (user.isBlank() || pass.isBlank()) emptyList()
        else listOf(
            PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80")
                .setUsername(user).setPassword(pass).createIceServer(),
            PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80?transport=tcp")
                .setUsername(user).setPassword(pass).createIceServer(),
            PeerConnection.IceServer.builder("turn:global.relay.metered.ca:443")
                .setUsername(user).setPassword(pass).createIceServer(),
            PeerConnection.IceServer.builder("turns:global.relay.metered.ca:443?transport=tcp")
                .setUsername(user).setPassword(pass).createIceServer()
        )
    }

    private val iceServers: List<PeerConnection.IceServer>
        get() = stunServers + personalTurnServers.ifEmpty { fallbackTurnServers }

    fun init() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        // Без явного AudioDeviceModule фабрика не гарантированно поднимает захват с
        // микрофона и воспроизведение на динамик — именно это и было причиной полной
        // тишины (без единой ошибки где-либо, поэтому раньше не поймать ни try/catch,
        // ни Crashlytics: тут никто ничего не бросает, просто нет звука).
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(
        onIceCandidate: (IceCandidate) -> Unit,
        onIceConnectionState: (PeerConnection.IceConnectionState) -> Unit
    ) {
        callCrumb("createPeerConnection: building rtc config")
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        callCrumb("createPeerConnection: calling factory.createPeerConnection")
        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                try { state?.let(onIceConnectionState) } catch (e: Throwable) { Log.e(TAG, "onIceConnectionState handler failed", e) }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                try {
                    // "typ host/srflx/relay" в SDP кандидата — если среди всех собранных
                    // кандидатов ни разу нет relay, TURN не выдаёт кандидатов вообще (не тот
                    // пароль/сервер недоступен); если relay есть, но соединение всё равно
                    // FAILED — проблема не в TURN, а где-то в обмене кандидатами через
                    // Firestore или в сети оператора. Видно в Firebase → Crashlytics →
                    // Non-fatals при следующем открытии приложения после сбоя звонка.
                    if (candidate != null) {
                        val type = Regex("typ (\\w+)").find(candidate.sdp)?.groupValues?.get(1) ?: "?"
                        callCrumb("ICE candidate gathered: type=$type")
                    }
                    candidate?.let(onIceCandidate)
                } catch (e: Throwable) { Log.e(TAG, "onIceCandidate handler failed", e) }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                try {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        remoteVideoTrack = track
                        remoteRenderer?.let { track.addSink(it) }
                    }
                } catch (e: Throwable) { Log.e(TAG, "onAddTrack failed", e) }
            }
        })
        callCrumb("createPeerConnection: done, peerConnection=${if (peerConnection != null) "ok" else "NULL"}")
    }

    // Локальный микрофон (+ камера для видеозвонка). Выставляет режим "в разговоре" сразу,
    // чтобы звук пошёл через нужный тракт ещё до того, как собеседник ответит.
    fun addLocalTracks(isVideo: Boolean) {
        val f = factory ?: return
        callCrumb("addLocalTracks: creating audio source")
        val audioSource = f.createAudioSource(MediaConstraints())
        callCrumb("addLocalTracks: creating audio track")
        val audioTrack = f.createAudioTrack("audio_${System.currentTimeMillis()}", audioSource)
        localAudioTrack = audioTrack
        callCrumb("addLocalTracks: adding audio track to peer connection")
        peerConnection?.addTrack(audioTrack, listOf("pinggram_stream"))

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = isVideo
        requestAudioFocus()
        callCrumb("addLocalTracks: audio done, isVideo=$isVideo")

        if (isVideo) {
            callCrumb("addLocalTracks: creating camera capturer")
            val capturer = createCameraCapturer() ?: run { callCrumb("addLocalTracks: no camera capturer available"); return }
            videoCapturer = capturer
            callCrumb("addLocalTracks: creating video source")
            val videoSource = f.createVideoSource(capturer.isScreencast)
            callCrumb("addLocalTracks: creating surface texture helper")
            val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            surfaceTextureHelper = helper
            callCrumb("addLocalTracks: initializing capturer")
            capturer.initialize(helper, context, videoSource.capturerObserver)
            callCrumb("addLocalTracks: starting capture")
            capturer.startCapture(1280, 720, 30)
            callCrumb("addLocalTracks: creating video track")
            val videoTrack = f.createVideoTrack("video_${System.currentTimeMillis()}", videoSource)
            localVideoTrack = videoTrack
            localRenderer?.let { videoTrack.addSink(it) }
            callCrumb("addLocalTracks: adding video track to peer connection")
            peerConnection?.addTrack(videoTrack, listOf("pinggram_stream"))
            callCrumb("addLocalTracks: video done")
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val names = enumerator.deviceNames
        for (name in names) if (enumerator.isFrontFacing(name)) enumerator.createCapturer(name, null)?.let { return it }
        for (name in names) enumerator.createCapturer(name, null)?.let { return it }
        return null
    }

    // Рендереры можно подключать в любой момент — если трек ещё не создан, он
    // подхватит уже прикреплённый рендерер сам, как только появится.
    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setMirror(true)
        renderer.setEnableHardwareScaler(true)
        localRenderer = renderer
        localVideoTrack?.addSink(renderer)
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
        remoteRenderer = renderer
        remoteVideoTrack?.addSink(renderer)
    }

    private fun detachRenderers() {
        localRenderer?.let { r -> runCatching { localVideoTrack?.removeSink(r) }; runCatching { r.release() } }
        remoteRenderer?.let { r -> runCatching { remoteVideoTrack?.removeSink(r) }; runCatching { r.release() } }
        localRenderer = null; remoteRenderer = null
    }

    suspend fun createOffer(): SessionDescription = suspendCancellableCoroutine { cont ->
        callCrumb("createOffer: calling peerConnection.createOffer")
        try {
            peerConnection?.createOffer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    callCrumb("createOffer: onCreateSuccess")
                    try {
                        if (sdp == null) { safeFail(cont, "offer sdp null"); return }
                        callCrumb("createOffer: calling setLocalDescription")
                        peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                        callCrumb("createOffer: setLocalDescription called, resuming")
                        if (cont.isActive) cont.resume(sdp)
                    } catch (e: Throwable) { Log.e(TAG, "createOffer onCreateSuccess failed", e); safeFail(cont, e.message ?: "createOffer failed") }
                }
                override fun onCreateFailure(error: String?) { safeFail(cont, error ?: "createOffer failed") }
            }, MediaConstraints())
        } catch (e: Throwable) { Log.e(TAG, "createOffer call failed", e); safeFail(cont, e.message ?: "createOffer failed") }
    }

    suspend fun createAnswer(): SessionDescription = suspendCancellableCoroutine { cont ->
        callCrumb("createAnswer: calling peerConnection.createAnswer")
        try {
            peerConnection?.createAnswer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    callCrumb("createAnswer: onCreateSuccess")
                    try {
                        if (sdp == null) { safeFail(cont, "answer sdp null"); return }
                        callCrumb("createAnswer: calling setLocalDescription")
                        peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                        callCrumb("createAnswer: setLocalDescription called, resuming")
                        if (cont.isActive) cont.resume(sdp)
                    } catch (e: Throwable) { Log.e(TAG, "createAnswer onCreateSuccess failed", e); safeFail(cont, e.message ?: "createAnswer failed") }
                }
                override fun onCreateFailure(error: String?) { safeFail(cont, error ?: "createAnswer failed") }
            }, MediaConstraints())
        } catch (e: Throwable) { Log.e(TAG, "createAnswer call failed", e); safeFail(cont, e.message ?: "createAnswer failed") }
    }

    // На стороне звонящего (startAsCaller) обмен построен так: оффер отправляется и
    // слушатель чужих кандидатов запускается сразу, а вот setRemoteDescription (ответ)
    // вызывается заметно позже — только когда через Firestore придёт статус ACTIVE. Если
    // кандидаты собеседника долетают раньше, чем этот ответ применён, peerConnection.
    // addIceCandidate() у WebRTC в этой ситуации просто возвращает false — без исключения,
    // без единой записи в лог, кандидат тихо теряется. Похоже, это и есть настоящая причина
    // "не удалось установить соединение" при рабочем TURN (см. диагностику 1.1.3.2 — трафик
    // через TURN идёт, но сама сверка кандидатов до этого не доходит). Буферизуем такие
    // кандидаты и добавляем их сразу после того, как remote description реально применится.
    private var remoteDescriptionSet = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    suspend fun setRemoteDescription(type: SessionDescription.Type, sdp: String): Unit = suspendCancellableCoroutine { cont ->
        callCrumb("setRemoteDescription: type=$type len=${sdp.length}")
        try {
            peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    callCrumb("setRemoteDescription: onSetSuccess")
                    remoteDescriptionSet = true
                    if (pendingRemoteCandidates.isNotEmpty()) {
                        callCrumb("setRemoteDescription: flushing ${pendingRemoteCandidates.size} buffered candidates")
                        pendingRemoteCandidates.forEach { c ->
                            runCatching { peerConnection?.addIceCandidate(c) }
                                .onFailure { Log.e(TAG, "flushed addIceCandidate failed", it) }
                        }
                        pendingRemoteCandidates.clear()
                    }
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onSetFailure(error: String?) { safeFail(cont, error ?: "setRemoteDescription failed") }
            }, SessionDescription(type, sdp))
        } catch (e: Throwable) { Log.e(TAG, "setRemoteDescription call failed", e); safeFail(cont, e.message ?: "setRemoteDescription failed") }
    }

    private fun safeFail(cont: kotlinx.coroutines.CancellableContinuation<*>, message: String) {
        if (cont.isActive) cont.resumeWithException(IllegalStateException(message))
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val ice = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        if (!remoteDescriptionSet) {
            callCrumb("addRemoteIceCandidate: remote description not set yet, buffering")
            pendingRemoteCandidates.add(ice)
            return
        }
        runCatching { peerConnection?.addIceCandidate(ice) }
            .onFailure { Log.e(TAG, "addRemoteIceCandidate failed", it) }
    }

    fun setMuted(muted: Boolean) { localAudioTrack?.setEnabled(!muted) }

    fun setSpeakerphone(on: Boolean) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = on
    }

    fun switchCamera() { videoCapturer?.switchCamera(null) }

    fun release() {
        if (released) return
        released = true
        runCatching { videoCapturer?.stopCapture() }
        detachRenderers()
        videoCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        peerConnection?.close()
        peerConnection?.dispose()
        factory?.dispose()
        abandonAudioFocus()
        runCatching { audioManager.mode = AudioManager.MODE_NORMAL; audioManager.isSpeakerphoneOn = false }
        remoteDescriptionSet = false
        pendingRemoteCandidates.clear()
    }
}

private open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
