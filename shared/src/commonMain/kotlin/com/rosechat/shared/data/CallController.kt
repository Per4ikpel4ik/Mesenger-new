package com.rosechat.shared.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rosechat.shared.model.SCallStatus
import com.rosechat.shared.model.SCallType
import com.rosechat.shared.model.SharedCall
import com.rosechat.shared.model.tsSeconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class CallPhase { IDLE, DIALING, INCOMING, CONNECTING, ACTIVE, ENDED }

// Единственный экземпляр на приложение (создаётся, только если платформа дала движок).
// Раньше тут был `by lazy`, который навсегда запоминал null, если к controller обратились до того,
// как Swift успел выставить CallPlatform.engine. Теперь null не кэшируется.
object CallCenter {
    private var cached: CallController? = null
    val controller: CallController?
        get() {
            cached?.let { return it }
            val e = CallPlatform.engine ?: return null
            return CallController(e).also { cached = it }
        }
}

// Логика звонка — тот же обмен через Firestore, что и у Android (коллекция calls:
// offerSdp/answerSdp в документе, кандидаты в подколлекциях callerCandidates/calleeCandidates).
// Голос и видео (тип звонка хранится в поле type документа, как у Android).
class CallController(private val engine: CallEngine) : CallEngineListener {
    var phase by mutableStateOf(CallPhase.IDLE)
        private set
    var peerName by mutableStateOf("")
        private set
    var peerPhoto by mutableStateOf("")
        private set
    var seconds by mutableStateOf(0)
        private set
    var muted by mutableStateOf(false)
        private set
    var speakerOn by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var isVideo by mutableStateOf(false)
        private set
    var cameraOff by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var callId: String? = null
    private var isCaller = false
    private var answerApplied = false
    private var observeJob: Job? = null
    private var candidatesJob: Job? = null
    private var timerJob: Job? = null
    private var timeoutJob: Job? = null
    private val handledIds = HashSet<String>()

    init { engine.attach(this) }

    // ── Исходящий ──
    fun startOutgoing(
        meUid: String, meName: String, mePhotoUrl: String, mePhotoData: String,
        peerUid: String, peerName: String, peerPhoto: String,
        video: Boolean = false,
    ) {
        if (phase != CallPhase.IDLE) return
        resetState()
        isVideo = video
        speakerOn = video
        this.peerName = peerName
        this.peerPhoto = peerPhoto
        isCaller = true
        phase = CallPhase.DIALING
        scope.launch {
            try {
                val id = SharedRepo.startCall(meUid, meName, mePhotoUrl, mePhotoData, peerUid, if (video) "VIDEO" else "AUDIO")
                callId = id
                handledIds.add(id)
                observe(id)
                engine.startCall(video, localSetting("TURN_USERNAME"), localSetting("TURN_PASSWORD"))
                if (video) engine.speaker(true)
                engine.makeOffer()
                // Если за 45 секунд никто не ответил — звонок пропущенный.
                timeoutJob?.cancel()
                timeoutJob = scope.launch {
                    delay(45_000)
                    if (phase == CallPhase.DIALING) hangUp()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("Не удалось начать звонок: ${e.message ?: "ошибка"}")
            }
        }
    }

    // ── Входящий ──
    fun onIncoming(call: SharedCall, name: String, photo: String) {
        if (phase != CallPhase.IDLE) return
        if (!handledIds.add(call.id)) return
        resetState()
        isVideo = call.data.type == SCallType.VIDEO
        speakerOn = isVideo
        callId = call.id
        isCaller = false
        peerName = name
        peerPhoto = photo
        phase = CallPhase.INCOMING
        observe(call.id)
        // Звонок, на который не ответили за минуту, просто убираем с экрана.
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(60_000)
            if (phase == CallPhase.INCOMING) finish(null)
        }
    }

    fun accept() {
        val id = callId ?: return
        if (phase != CallPhase.INCOMING) return
        timeoutJob?.cancel()
        phase = CallPhase.CONNECTING
        scope.launch {
            try {
                // offerSdp дописывается чуть позже создания документа — ждём до 8 секунд.
                val c = withTimeoutOrNull(8_000) {
                    SharedRepo.observeCall(id).filterNotNull().first { it.data.offerSdp.isNotEmpty() }
                }
                if (c == null) { fail("Звонящий не отправил данные звонка"); return@launch }
                engine.startCall(isVideo, localSetting("TURN_USERNAME"), localSetting("TURN_PASSWORD"))
                if (isVideo) engine.speaker(true)
                engine.acceptOffer(c.data.offerSdp)
                startConnectTimeout()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("Не удалось принять звонок: ${e.message ?: "ошибка"}")
            }
        }
    }

    fun decline() {
        val id = callId
        if (id != null) scope.launch { SharedRepo.declineCall(id) }
        finish(null)
    }

    fun hangUp() {
        val id = callId
        val status = if (phase == CallPhase.ACTIVE || phase == CallPhase.CONNECTING) "ENDED" else "MISSED"
        if (id != null) scope.launch { SharedRepo.endCall(id, status) }
        finish(null)
    }

    fun toggleMute() { muted = !muted; engine.mute(muted) }
    fun toggleSpeaker() { speakerOn = !speakerOn; engine.speaker(speakerOn) }
    fun toggleCamera() { cameraOff = !cameraOff; engine.setCameraOff(cameraOff) }
    fun switchCamera() { engine.switchCamera() }

    // ── Слежение за документом звонка ──
    private fun observe(id: String) {
        observeJob?.cancel()
        observeJob = scope.launch {
            try {
                SharedRepo.observeCall(id).collect { c ->
                    if (c == null) return@collect
                    when (c.data.status) {
                        SCallStatus.ACTIVE -> {
                            if (isCaller && !answerApplied && c.data.answerSdp.isNotEmpty() && phase == CallPhase.DIALING) {
                                answerApplied = true
                                timeoutJob?.cancel()
                                phase = CallPhase.CONNECTING
                                engine.applyAnswer(c.data.answerSdp)
                                startConnectTimeout()
                            }
                        }
                        SCallStatus.DECLINED -> finish(if (isCaller) "Собеседник отклонил звонок" else null)
                        SCallStatus.MISSED -> finish(if (isCaller) "Нет ответа" else null)
                        SCallStatus.ENDED -> finish(null)
                        SCallStatus.RINGING -> {}
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("Нет связи с сервером: ${e.message ?: "ошибка"}")
            }
        }
    }

    private fun listenCandidates(id: String, fromCaller: Boolean) {
        candidatesJob?.cancel()
        candidatesJob = scope.launch {
            try {
                SharedRepo.listenIceCandidates(id, fromCaller).collect { list ->
                    list.forEach { engine.addCandidate(it.sdpMid, it.sdpMLineIndex, it.candidate) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Чаще всего PERMISSION_DENIED: правила Firestore не пускают чтение подколлекций кандидатов.
                fail("Не удалось получить данные соединения: ${e.message ?: "ошибка"}")
            }
        }
    }

    private fun startConnectTimeout() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(25_000)
            if (phase == CallPhase.CONNECTING) fail("Не удалось установить соединение")
        }
    }

    // ── События движка (приходят на главном потоке) ──
    override fun onLocalSdp(type: String, sdp: String) {
        val id = callId ?: return
        scope.launch {
            try {
                if (type == "offer") {
                    SharedRepo.setOffer(id, sdp)
                    listenCandidates(id, fromCaller = false)   // звонящий слушает кандидатов принимающего
                } else {
                    SharedRepo.setAnswer(id, sdp)
                    listenCandidates(id, fromCaller = true)    // принимающий слушает кандидатов звонящего
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail("Не удалось отправить данные звонка: ${e.message ?: "ошибка"}")
            }
        }
    }

    override fun onLocalCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val id = callId ?: return
        val fromCaller = isCaller
        scope.launch { try { SharedRepo.addIceCandidate(id, fromCaller, sdpMid, sdpMLineIndex, candidate) } catch (_: Exception) {} }
    }

    override fun onConnectionState(state: String) {
        if (phase == CallPhase.IDLE || phase == CallPhase.ENDED) return
        when (state) {
            "CONNECTED", "COMPLETED" -> if (phase != CallPhase.ACTIVE) {
                timeoutJob?.cancel()
                phase = CallPhase.ACTIVE
                timerJob?.cancel()
                timerJob = scope.launch { while (true) { delay(1000); seconds++ } }
            }
            "FAILED" -> fail("Не удалось установить соединение")
        }
    }

    override fun onError(message: String) { fail(message) }

    // ── Завершение ──
    private fun fail(reason: String) {
        val id = callId
        if (id != null && phase != CallPhase.IDLE && phase != CallPhase.ENDED) {
            scope.launch { SharedRepo.endCall(id, "ENDED") }
        }
        finish(reason)
    }

    private fun finish(reason: String?) {
        if (phase == CallPhase.IDLE || phase == CallPhase.ENDED) return
        observeJob?.cancel(); candidatesJob?.cancel(); timerJob?.cancel(); timeoutJob?.cancel()
        try { engine.stopCall() } catch (_: Exception) {}
        message = reason
        phase = CallPhase.ENDED
        scope.launch {
            delay(1500)
            if (phase == CallPhase.ENDED) { phase = CallPhase.IDLE; callId = null }
        }
    }

    private fun resetState() {
        seconds = 0
        muted = false
        speakerOn = false
        isVideo = false
        cameraOff = false
        message = null
        answerApplied = false
        callId = null
    }
}
