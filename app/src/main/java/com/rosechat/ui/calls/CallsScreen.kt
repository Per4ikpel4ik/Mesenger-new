package com.rosechat.ui.calls

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.rosechat.data.model.Call
import com.rosechat.data.model.CallStatus
import com.rosechat.data.model.CallType
import com.rosechat.data.model.IceCandidateData
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.data.webrtc.CallManager
import com.rosechat.data.webrtc.callCrumb
import com.rosechat.ui.common.SmartImage
import com.rosechat.ui.common.hasImage
import com.rosechat.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer

enum class CallPhase { DIALING, INCOMING, ACTIVE, ENDED }

// Сигналинг и медиа полностью реальные (WebRTC + Firestore, см. CallManager и
// FirebaseRepo). Чего тут сознательно нет — ConnectionService/полноэкранного будильника
// поверх заблокированного экрана при полностью убитом приложении: пока звонок доходит
// пуш-уведомлением (FcmService) и живым Firestore-листенером, пока приложение не убито.
class CallViewModel(
    private val callId: String,
    private val isCaller: Boolean,
    private val callType: CallType,
    context: Context
) : ViewModel() {

    val manager = CallManager(context.applicationContext)

    private val _phase = MutableStateFlow(if (isCaller) CallPhase.DIALING else CallPhase.INCOMING)
    val phase = _phase.asStateFlow()

    private val _seconds = MutableStateFlow(0)
    val seconds = _seconds.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()
    private val _isSpeaker = MutableStateFlow(callType == CallType.VIDEO)
    val isSpeaker = _isSpeaker.asStateFlow()

    // Раньше "подключено" означало только "SDP-обмен завершён" — если ICE после этого
    // не пробивался (NAT/файрвол/перегруженный бесплатный TURN), звонок молча шёл дальше
    // с работающим таймером и нулём медиа-байт в обе стороны: то самое "звонок есть,
    // звука нет ни у кого" без единой ошибки. Теперь реальное ICE-состояние отслеживается
    // отдельно, и используется и для UI ("Устанавливаем соединение..."), и как таймаут-guard.
    private val _iceState = MutableStateFlow<org.webrtc.PeerConnection.IceConnectionState?>(null)
    val mediaConnected = _iceState.map {
        it == org.webrtc.PeerConnection.IceConnectionState.CONNECTED || it == org.webrtc.PeerConnection.IceConnectionState.COMPLETED
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _connectionFailed = MutableStateFlow(false)
    val connectionFailed = _connectionFailed.asStateFlow()

    // Конкретная причина неудачи, если она известна. Главный случай — PERMISSION_DENIED от
    // подколлекций кандидатов: правила firestore.rules с послаблением для callerCandidates/
    // calleeCandidates (версия 1.0.2.5) просто не задеплоены в облако. Раньше эта ошибка
    // ловилась и уходила в Log — на экране звонка оставалось лишь общее "не удалось
    // установить соединение", неотличимое от проблем с сетью/TURN.
    private val _failReason = MutableStateFlow<String?>(null)
    val failReason = _failReason.asStateFlow()

    // WebRTC вызывает этот колбэк на своём внутреннем потоке наблюдателя PeerConnection,
    // а не на главном. failCall()/cleanup() ниже трогает SurfaceViewRenderer и камеру —
    // это привязано к главному потоку, как и все остальные места в этом ViewModel, где
    // cleanup() вызывается (кнопки UI, коллектор Firestore на главном потоке). Раньше сюда
    // приходил no-op `{}`, поэтому проблема не проявлялась; теперь, когда тут реальная
    // логика, без явного переключения потока это крашило приложение прямо в момент FAILED
    // и из-за прерванного на середине cleanup() оставляло камеру занятой.
    private fun onIceStateChanged(state: org.webrtc.PeerConnection.IceConnectionState) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            try {
                callCrumb("ICE state changed: $state")
                _iceState.value = state
                if (state == org.webrtc.PeerConnection.IceConnectionState.FAILED) {
                    Log.e("CallViewModel", "ICE connection failed")
                    _connectionFailed.value = true
                    // Некритичный (non-fatal) репорт — вместе со всеми callCrumb-крошками выше
                    // (какие типы ICE-кандидатов вообще собрались — host/srflx/relay) появится
                    // в Firebase → Crashlytics → Non-fatals при следующем открытии приложения.
                    // Без этого узнать причину можно было только через Logcat с телефона.
                    runCatching {
                        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                            .recordException(Exception("ICE connection FAILED — см. крошки выше про типы кандидатов"))
                    }
                    failCall()
                }
            } catch (e: Throwable) {
                // Как и везде в этом ViewModel вокруг разбора звонка — коллбэк из
                // launch{} не оборачивается автоматически, необработанное исключение тут
                // раньше и роняло приложение. Ничего критичного тут случиться не должно,
                // но лучше залогировать и не крашнуть, чем упасть посреди cleanup().
                Log.e("CallViewModel", "onIceStateChanged failed", e)
            }
        }
    }

    val call: StateFlow<Call?> = FirebaseRepo.observeCall(callId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var timerJob: Job? = null
    private var iceJob: Job? = null
    private var started = false
    private var cleaned = false

    init {
        manager.init()
        viewModelScope.launch {
            call.collect { c ->
                if (c == null) return@collect
                when (c.status) {
                    CallStatus.ACTIVE -> {
                        if (isCaller && manager.peerConnection != null && _phase.value != CallPhase.ACTIVE) {
                            try {
                                if (c.answerSdp.isNotEmpty()) {
                                    manager.setRemoteDescription(SessionDescription.Type.ANSWER, c.answerSdp)
                                }
                                onConnected()
                            } catch (e: Exception) {
                                Log.e("CallViewModel", "failed applying answer", e)
                                failCall()
                            }
                        }
                    }
                    CallStatus.ENDED, CallStatus.DECLINED, CallStatus.MISSED -> {
                        if (_phase.value != CallPhase.ENDED) { _phase.value = CallPhase.ENDED; cleanup() }
                    }
                    CallStatus.RINGING -> {}
                }
            }
        }
        if (isCaller) {
            // Раньше звонок звонящего стартовал прямо тут, не дожидаясь ответа на системный
            // диалог разрешений — микрофон мог быть фактически ещё не готов к моменту, когда
            // WebRTC уже пытался его захватить. Никакой ошибки это не даёт (просто тишина),
            // поэтому теперь старт откладывается — см. requestStartIfCaller(), вызывается из
            // UI только когда оба разрешения подтверждены.
        }
    }

    // Вызывается из UI (CallScreen) только когда микрофон (и камера для видео) точно
    // разрешены — раньше стартовали сразу в init{}, не дожидаясь диалога разрешений.
    fun requestStartIfCaller() {
        if (isCaller) startAsCaller()
    }

    private fun startAsCaller() {
        if (started) return
        started = true
        callCrumb("startAsCaller: creating peer connection isVideo=${callType == CallType.VIDEO}")
        viewModelScope.launch {
            try {
                manager.createPeerConnection(
                    onIceCandidate = { c ->
                        viewModelScope.launch {
                            FirebaseRepo.addIceCandidate(
                                callId, fromCaller = true,
                                IceCandidateData(sdpMid = c.sdpMid ?: "", sdpMLineIndex = c.sdpMLineIndex, candidate = c.sdp)
                            )
                        }
                    },
                    onIceConnectionState = ::onIceStateChanged
                )
                if (manager.peerConnection == null) throw IllegalStateException("peerConnection is null (caller)")
                callCrumb("startAsCaller: peer connection ok, adding local tracks")
                manager.addLocalTracks(callType == CallType.VIDEO)
                callCrumb("startAsCaller: local tracks added, creating offer")
                val offer = manager.createOffer()
                callCrumb("startAsCaller: offer created, writing to firestore")
                FirebaseRepo.setOffer(callId, offer.description)
                listenRemoteCandidates(fromCaller = false)
                callCrumb("startAsCaller: done")
            } catch (e: Exception) {
                Log.e("CallViewModel", "failed starting outgoing call", e)
                failCall()
            }
        }
    }

    // Разрешения на микрофон/камеру запрашиваются в UI ДО вызова accept() — сюда
    // приходим уже с готовым согласием пользователя.
    fun accept() {
        if (started) return
        started = true
        callCrumb("accept: tapped, waiting for offer")
        viewModelScope.launch {
            try {
                // Документ звонка создаётся сразу (статус RINGING), а offerSdp дописывается
                // отдельным апдейтом чуть позже — если принять звонок в этот короткий
                // промежуток, в локальном снэпшоте его ещё может не быть. Ждём (с таймаутом),
                // а не читаем call.value один раз — иначе именно тут раньше был обрыв/краш.
                val c = withTimeoutOrNull(8000) { call.filterNotNull().first { it.offerSdp.isNotEmpty() } }
                if (c == null) { Log.e("CallViewModel", "offer never arrived"); failCall(); return@launch }
                callCrumb("accept: got offer (len=${c.offerSdp.length}), creating peer connection")

                manager.createPeerConnection(
                    onIceCandidate = { cand ->
                        viewModelScope.launch {
                            FirebaseRepo.addIceCandidate(
                                callId, fromCaller = false,
                                IceCandidateData(sdpMid = cand.sdpMid ?: "", sdpMLineIndex = cand.sdpMLineIndex, candidate = cand.sdp)
                            )
                        }
                    },
                    onIceConnectionState = ::onIceStateChanged
                )
                if (manager.peerConnection == null) throw IllegalStateException("peerConnection is null (callee)")
                callCrumb("accept: peer connection ok, adding local tracks isVideo=${callType == CallType.VIDEO}")
                manager.addLocalTracks(callType == CallType.VIDEO)
                callCrumb("accept: local tracks added, setting remote description")
                manager.setRemoteDescription(SessionDescription.Type.OFFER, c.offerSdp)
                callCrumb("accept: remote description set, creating answer")
                val answer = manager.createAnswer()
                callCrumb("accept: answer created, writing to firestore")
                FirebaseRepo.setAnswer(callId, answer.description)
                listenRemoteCandidates(fromCaller = true)
                callCrumb("accept: done, connected")
                onConnected()
            } catch (e: Exception) {
                Log.e("CallViewModel", "failed accepting call", e)
                failCall()
            }
        }
    }

    // Общий путь на случай сбоя где угодно в установке соединения — вместо падения
    // приложения аккуратно завершаем звонок и гасим WebRTC-ресурсы.
    private fun failCall() {
        viewModelScope.launch { FirebaseRepo.endCall(callId, CallStatus.ENDED) }
        _phase.value = CallPhase.ENDED
        cleanup()
    }

    fun decline() {
        viewModelScope.launch { FirebaseRepo.declineCall(callId) }
        _phase.value = CallPhase.ENDED
        cleanup()
    }

    fun hangUp() {
        val finalStatus = if (call.value?.status == CallStatus.ACTIVE) CallStatus.ENDED else CallStatus.MISSED
        viewModelScope.launch { FirebaseRepo.endCall(callId, finalStatus) }
        _phase.value = CallPhase.ENDED
        cleanup()
    }

    private fun onConnected() {
        _phase.value = CallPhase.ACTIVE
        timerJob?.cancel()
        timerJob = viewModelScope.launch { while (true) { delay(1000); _seconds.value++ } }
        // Сигналинг (SDP) прошёл успешно — это ещё не значит, что медиа реально пробилось
        // между телефонами. Если за 15 секунд ICE так и не дошёл до CONNECTED/COMPLETED,
        // значит путь для звука не установился (не пробить NAT / TURN недоступен) — раньше
        // звонок в этом случае просто продолжал идти в полной тишине без объяснений.
        viewModelScope.launch {
            delay(15000)
            try {
                if (_phase.value == CallPhase.ACTIVE && !mediaConnected.value) {
                    callCrumb("onConnected: no media after 15s, iceState=${_iceState.value}")
                    Log.e("CallViewModel", "media never connected, iceState=${_iceState.value}")
                    _connectionFailed.value = true
                    failCall()
                }
            } catch (e: Throwable) {
                Log.e("CallViewModel", "15s connectivity guard failed", e)
            }
        }
    }

    private fun listenRemoteCandidates(fromCaller: Boolean) {
        iceJob?.cancel()
        iceJob = viewModelScope.launch {
            try {
                FirebaseRepo.listenIceCandidates(callId, fromCaller).collect { list ->
                    list.forEach { manager.addRemoteIceCandidate(it.sdpMid, it.sdpMLineIndex, it.candidate) }
                }
            } catch (e: Exception) {
                // Раньше именно этот коллектор ронял всё приложение: PERMISSION_DENIED от
                // list-запроса без where-фильтра против правила с get() (см. firestore.rules).
                // Само правило в репозитории исправлено, НО если firestore.rules ни разу не
                // деплоился, в облаке живёт старая версия — и тогда кандидаты собеседника
                // не приходят вообще. Звонок при этом доходит до "Устанавливаем соединение..."
                // и через 15с падает по таймауту, а причина видна только здесь. Больше не
                // прячем её в лог.
                Log.e("CallViewModel", "ICE candidate listener failed", e)
                val denied = e.message?.contains("PERMISSION_DENIED", true) == true ||
                    e.message?.contains("Missing or insufficient", true) == true
                _failReason.value = if (denied)
                    "Правила Firestore не задеплоены — обмен ICE-кандидатами запрещён.\nЗапусти: firebase deploy --only firestore:rules"
                else "Обмен кандидатами прервался: ${e.message}"
            }
        }
    }

    fun toggleMute() { _isMuted.value = !_isMuted.value; manager.setMuted(_isMuted.value) }
    fun toggleSpeaker() { _isSpeaker.value = !_isSpeaker.value; manager.setSpeakerphone(_isSpeaker.value) }
    fun switchCamera() { manager.switchCamera() }

    private fun cleanup() {
        if (cleaned) return
        cleaned = true
        timerJob?.cancel(); iceJob?.cancel()
        manager.release()
    }

    override fun onCleared() { cleanup() }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CallScreen(callId: String, isCaller: Boolean, isVideo: Boolean, peerName: String, onFinish: () -> Unit) {
    val context = LocalContext.current
    val vm: CallViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CallViewModel(callId, isCaller, if (isVideo) CallType.VIDEO else CallType.AUDIO, context) as T
        }
    )

    val phase by vm.phase.collectAsStateWithLifecycle()
    val seconds by vm.seconds.collectAsStateWithLifecycle()
    val isMuted by vm.isMuted.collectAsStateWithLifecycle()
    val isSpeaker by vm.isSpeaker.collectAsStateWithLifecycle()
    val call by vm.call.collectAsStateWithLifecycle()
    val mediaConnected by vm.mediaConnected.collectAsStateWithLifecycle()
    val connectionFailed by vm.connectionFailed.collectAsStateWithLifecycle()
    val failReason by vm.failReason.collectAsStateWithLifecycle()

    val micPerm = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    val camPerm = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (isCaller) {
            if (!micPerm.status.isGranted) micPerm.launchPermissionRequest()
            if (isVideo && !camPerm.status.isGranted) camPerm.launchPermissionRequest()
        }
    }

    // Раньше исходящий звонок стартовал сразу при открытии экрана, не дожидаясь ответа
    // на диалог разрешений выше — микрофон мог быть физически не готов, и получалась
    // тишина без единой ошибки. Теперь ждём, пока оба разрешения реально подтвердятся.
    LaunchedEffect(isCaller, micPerm.status, camPerm.status) {
        if (isCaller) {
            val micOk = micPerm.status.isGranted
            val camOk = !isVideo || camPerm.status.isGranted
            if (micOk && camOk) vm.requestStartIfCaller()
        }
    }

    LaunchedEffect(phase) { if (phase == CallPhase.ENDED) { delay(600); onFinish() } }

    // Вибро на входящем — простой повторяющийся паттерн, пока не примут/отклонят.
    DisposableEffect(phase) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (phase == CallPhase.INCOMING) {
            val pattern = longArrayOf(0, 700, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0)
        }
        onDispose { vibrator?.cancel() }
    }

    BackHandler(enabled = phase == CallPhase.DIALING || phase == CallPhase.ACTIVE) { vm.hangUp() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isVideo && phase == CallPhase.ACTIVE) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> SurfaceViewRenderer(ctx).also { vm.manager.attachRemoteRenderer(it) } }
            )
            AndroidView(
                modifier = Modifier
                    .size(110.dp, 150.dp)
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(14.dp)),
                factory = { ctx -> SurfaceViewRenderer(ctx).also { vm.manager.attachLocalRenderer(it) } }
            )
        }

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(80.dp))

            if (!isVideo || phase != CallPhase.ACTIVE) {
                val photoUrl = if (!isCaller) call?.callerPhotoUrl.orEmpty() else ""
                val photoData = if (!isCaller) call?.callerPhotoData.orEmpty() else ""
                Box(Modifier.size(120.dp).clip(CircleShape).background(AccentMuted), Alignment.Center) {
                    if (hasImage(photoUrl, photoData)) SmartImage(photoUrl, photoData, Modifier.fillMaxSize(), ContentScale.Crop)
                    else Text(peerName.firstOrNull()?.toString() ?: "?", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.height(24.dp))
            }

            Text(peerName, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                when (phase) {
                    CallPhase.DIALING  -> "Звоним..."
                    CallPhase.INCOMING -> if (isVideo) "Входящий видеозвонок" else "Входящий звонок"
                    CallPhase.ACTIVE   -> if (mediaConnected) "%02d:%02d".format(seconds / 60, seconds % 60) else "Устанавливаем соединение..."
                    CallPhase.ENDED    -> if (connectionFailed) "Не удалось установить соединение" else "Звонок завершён"
                },
                style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f)
            )
            val reason = failReason
            if (phase == CallPhase.ENDED && connectionFailed && reason != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )
            }

            // Раньше единственный Spacer(weight=1f) выталкивал кнопки к самому низу экрана —
            // на некоторых устройствах (особенно с жестовой навигацией/вырезом снизу) они
            // оказывались слишком близко к краю. Разбиваем свободное место на "над кнопками"
            // и "под кнопками", чтобы ряд с кнопками поднялся заметно выше, но не улетел в центр.
            Spacer(Modifier.weight(1f))

            when (phase) {
                CallPhase.INCOMING -> Row(
                    Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallBtn(Icons.Default.CallEnd, "Отклонить", ErrorRed) { vm.decline() }
                    CallBtn(Icons.Default.Call, "Принять", Color(0xFF2ECC71)) {
                        val micOk = micPerm.status.isGranted
                        val camOk = !isVideo || camPerm.status.isGranted
                        when {
                            !micOk -> micPerm.launchPermissionRequest()
                            !camOk -> camPerm.launchPermissionRequest()
                            else -> vm.accept()
                        }
                    }
                }
                CallPhase.DIALING, CallPhase.ACTIVE -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
                    ) {
                        CallBtn(
                            if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            if (isMuted) "Вкл. микр." else "Выкл. микр.", BgTertiary
                        ) { vm.toggleMute() }

                        Box(Modifier.size(72.dp).clip(CircleShape).background(ErrorRed).clickable { vm.hangUp() }, Alignment.Center) {
                            Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        CallBtn(
                            if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            if (isSpeaker) "Тихо" else "Громко", BgTertiary
                        ) { vm.toggleSpeaker() }
                    }
                    if (isVideo) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(bottom = 32.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CallBtn(Icons.Default.Cameraswitch, "Камера", BgTertiary) { vm.switchCamera() }
                        }
                    }
                }
                CallPhase.ENDED -> {}
            }

            Spacer(Modifier.weight(0.4f))
        }
    }
}

@Composable
private fun CallBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
    }
}
