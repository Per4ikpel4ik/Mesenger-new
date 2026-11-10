package com.rosechat.ui.chat

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import com.rosechat.data.local.ChatSettingsState
import com.rosechat.data.local.ImageCompress
import com.rosechat.data.local.MediaSaver
import com.rosechat.data.local.VideoCompress
import com.rosechat.ui.common.SmartImage
import com.rosechat.ui.common.formatLastSeen
import com.rosechat.ui.common.hasImage
import com.rosechat.ui.common.premiumBubbleColorOrNull
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.rosechat.data.model.Chat
import com.rosechat.data.model.CallType
import com.rosechat.data.model.ChatType
import com.rosechat.data.model.Message
import com.rosechat.data.model.MessageType
import com.rosechat.data.model.User
import com.rosechat.data.model.PROFILE_STICKERS
import com.rosechat.data.model.CHAT_STICKERS
import com.rosechat.data.repository.CloudinaryRepository
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Пока голосовые едут через Firestore (Base64), а не через Storage, держим их короткими,
// чтобы гарантированно укладываться в лимит документа Firestore (1 МБ).
private const val MAX_VOICE_SECONDS_FREE = 60
private const val MAX_VOICE_SECONDS_PREMIUM = 240  // Premium-перк
private const val MAX_FILE_BYTES = 700_000  // лимит документа Firestore — действует, только если Cloudinary не настроен
private const val MAX_FILE_BYTES_CLOUDINARY = 10_000_000  // с внешним хранилищем лимит куда мягче (10 МБ, с запасом от лимита бесплатного тарифа Cloudinary)

// Стикеры-картинки — в дополнение к эмодзи-стикерам (CHAT_STICKERS): та же панель, тот же
// MessageType.STICKER, но вместо msg.sticker используется обычное поле mediaData (как у
// фото/файлов) — Bubble() ниже отличает картиночный стикер от эмодзи-стикера именно по
// тому, что msg.sticker пустой. Отправляются всегда через Base64 (см. sendImageSticker),
// без похода в Cloudinary — сами картинки маленькие и заранее лежат в APK.
private val IMAGE_STICKERS: List<Int> = listOf(
    com.rosechat.R.drawable.sticker_seal,
    com.rosechat.R.drawable.sticker_hippo1,
    com.rosechat.R.drawable.sticker_hippo2,
)

class ChatViewModel(val chatId: String) : ViewModel() {
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    val messages = FirebaseRepo.getMessages(chatId)
        .catch { e -> _loadError.value = "Сообщения: ${e.message ?: "ошибка загрузки"}" }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val chat: StateFlow<Chat?> = FirebaseRepo.getChat(chatId)
        .catch { e -> _loadError.value = "Чат: ${e.message ?: "ошибка загрузки"}" }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    private val _partnerName = MutableStateFlow<String?>(null)
    val partnerName = _partnerName.asStateFlow()
    private val _partnerUser = MutableStateFlow<User?>(null)
    val partnerUser = _partnerUser.asStateFlow()
    private var partnerJob: kotlinx.coroutines.Job? = null
    private val _input       = MutableStateFlow("")
    val input = _input.asStateFlow()
    private val _me          = MutableStateFlow<User?>(null)
    val me = _me.asStateFlow()
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()
    private val _recSecs     = MutableStateFlow(0)
    val recSecs = _recSecs.asStateFlow()
    private val _uploading   = MutableStateFlow(false)
    val uploading = _uploading.asStateFlow()
    private val _showStickers = MutableStateFlow(false)
    val showStickers = _showStickers.asStateFlow()
    private val _showGifs = MutableStateFlow(false)
    val showGifs = _showGifs.asStateFlow()
    private val _gifQuery = MutableStateFlow("")
    val gifQuery = _gifQuery.asStateFlow()
    private val _gifResults = MutableStateFlow<List<com.rosechat.data.repository.GifResult>>(emptyList())
    val gifResults = _gifResults.asStateFlow()
    private val _gifLoading = MutableStateFlow(false)
    val gifLoading = _gifLoading.asStateFlow()
    // Раньше ошибка запроса шла только в общий _loadError (снекбар, легко пропустить/он сам
    // гаснет) — а сама панель GIF в это время показывала "Ничего не найдено", неотличимо от
    // честной пустой выдачи. Теперь ошибка ещё и остаётся видна прямо в панели, пока не
    // закроешь её или не повторишь запрос.
    private val _gifError = MutableStateFlow<String?>(null)
    val gifError = _gifError.asStateFlow()
    private var gifSearchJob: kotlinx.coroutines.Job? = null
    private val _editingId = MutableStateFlow<String?>(null)
    val editingId = _editingId.asStateFlow()
    private val _playingId = MutableStateFlow<String?>(null)   // id голосового, которое сейчас проигрывается
    val playingId = _playingId.asStateFlow()
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var recJob: kotlinx.coroutines.Job? = null
    private var player: MediaPlayer? = null

    private val _isRecordingVideo = MutableStateFlow(false)
    val isRecordingVideo = _isRecordingVideo.asStateFlow()
    private val _videoRecSecs = MutableStateFlow(0)
    val videoRecSecs = _videoRecSecs.asStateFlow()

    // Пропорции кадра, который реально выдала камера (см. VideoNoteRecorder.recordAspect) —
    // по ним живое превью обрезается кругом вместо растягивания.
    private val _videoPreviewAspect = MutableStateFlow(1f)
    val videoPreviewAspect = _videoPreviewAspect.asStateFlow()
    private var videoNoteRecorder: com.rosechat.data.media.VideoNoteRecorder? = null
    private var videoRecJob: kotlinx.coroutines.Job? = null
    // Раньше "какую камеру использовать" жило внутри VideoNoteRecorder и терялось при
    // пересоздании объекта (а он пересоздаётся при каждом перезапуске записи) — переключение
    // камеры физически ничего не меняло. Теперь состояние здесь, во ViewModel, переживает
    // пересоздание рекордера. Текстуру и контекст тоже кэшируем: TextureView у Compose не
    // обязательно пересоздаётся при быстром false→true isRecordingVideo, а значит и
    // onSurfaceTextureAvailable может не вызваться второй раз — перезапуск делаем по
    // сохранённой текстуре напрямую, не дожидаясь нового колбэка.
    private var preferFrontCamera = true
    private var lastPreviewContext: Context? = null
    private var lastPreviewTexture: android.graphics.SurfaceTexture? = null

    init {
        viewModelScope.launch {
            FirebaseRepo.observeUser(FirebaseRepo.currentUid ?: return@launch).collect { _me.value = it }
        }
    }

    // Живое наблюдение за собеседником — имя (для диплинков, когда chatName ещё не подтянулся),
    // онлайн-статус и lastSeen для строки под именем в шапке чата.
    fun observePartner(uid: String) {
        if (uid.isBlank() || partnerJob != null) return
        partnerJob = viewModelScope.launch {
            FirebaseRepo.observeUser(uid).collect { u -> _partnerUser.value = u; _partnerName.value = u?.displayName }
        }
    }

    private var typingJob: kotlinx.coroutines.Job? = null
    private var typingSent = false

    fun onInputChanged(v: String) {
        _input.update { v }
        if (!ChatSettingsState.showTyping) return
        val uid = _me.value?.uid ?: return
        typingJob?.cancel()
        if (v.isNotBlank()) {
            if (!typingSent) { FirebaseRepo.setTyping(chatId, uid, true); typingSent = true }
            typingJob = viewModelScope.launch {
                kotlinx.coroutines.delay(3000)  // 3с без ввода — считаем, что перестал печатать
                FirebaseRepo.setTyping(chatId, uid, false); typingSent = false
            }
        } else if (typingSent) {
            FirebaseRepo.setTyping(chatId, uid, false); typingSent = false
        }
    }
    fun toggleStickers() { _showGifs.value = false; _showStickers.update { !it } }

    fun toggleGifs() {
        val opening = !_showGifs.value
        _showStickers.value = false
        _showGifs.update { opening }
        if (opening && _gifResults.value.isEmpty()) loadTrendingGifs()
    }

    private fun loadTrendingGifs() {
        gifSearchJob?.cancel()
        _gifLoading.value = true
        _gifError.value = null
        gifSearchJob = viewModelScope.launch {
            com.rosechat.data.repository.GifRepository.trending()
                .onSuccess { _gifResults.value = it }
                .onFailure { e -> _gifError.value = e.message ?: "не загрузилось"; _loadError.value = "GIF: ${e.message ?: "не загрузилось"}" }
            _gifLoading.value = false
        }
    }

    // Кнопка "Повторить" в панели GIF при ошибке — переигрывает тот же запрос (поиск, если
    // было что-то введено, иначе трендовые).
    fun retryGifs() {
        if (_gifQuery.value.isBlank()) loadTrendingGifs() else onGifQueryChanged(_gifQuery.value)
    }

    fun onGifQueryChanged(q: String) {
        _gifQuery.value = q
        gifSearchJob?.cancel()
        gifSearchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)  // не долбим API на каждую букву
            _gifLoading.value = true
            _gifError.value = null
            val result = if (q.isBlank()) com.rosechat.data.repository.GifRepository.trending()
                         else com.rosechat.data.repository.GifRepository.search(q)
            result.onSuccess { _gifResults.value = it }
                .onFailure { e -> _gifError.value = e.message ?: "не загрузилось"; _loadError.value = "GIF: ${e.message ?: "не загрузилось"}" }
            _gifLoading.value = false
        }
    }

    fun sendGif(url: String) {
        val u = _me.value ?: return
        _showGifs.value = false
        viewModelScope.launch { FirebaseRepo.sendGif(chatId, url, u) }
    }

    fun startEdit(msg: Message) {
        _editingId.value = msg.id
        _input.value = msg.text
    }
    fun cancelEdit() {
        _editingId.value = null
        _input.value = ""
    }

    fun sendText() {
        val t = _input.value.trim(); val u = _me.value ?: return; if (t.isBlank()) return
        val editId = _editingId.value
        if (editId != null) {
            viewModelScope.launch {
                FirebaseRepo.editMessage(chatId, editId, t)
                _input.update { "" }; _editingId.value = null
            }
        } else {
            val text = t
            typingJob?.cancel(); if (typingSent) { FirebaseRepo.setTyping(chatId, u.uid, false); typingSent = false }
            _input.update { "" }
            viewModelScope.launch {
                FirebaseRepo.sendText(chatId, text, u)
                    .onSuccess { premiumMsg -> premiumMsg?.let { _loadError.value = it } }
                    .onFailure { e -> _loadError.value = "Не удалось отправить: ${e.message}" }
            }
        }
    }

    fun sendSticker(s: String) {
        val u = _me.value ?: return
        _showStickers.value = false
        viewModelScope.launch { FirebaseRepo.sendSticker(chatId, s, u) }
    }

    // Картиночный стикер (см. IMAGE_STICKERS) — читаем уже готовый PNG прямо из ресурсов
    // приложения (openRawResource работает для любого файла в drawable, не только res/raw),
    // без похода в сеть и без Cloudinary: файлы маленькие и заранее лежат в APK.
    fun sendImageSticker(resId: Int, context: Context) {
        val u = _me.value ?: return
        _showStickers.value = false
        viewModelScope.launch {
            val base64 = try {
                context.resources.openRawResource(resId).use { it.readBytes() }
                    .let { Base64.encodeToString(it, Base64.NO_WRAP) }
            } catch (e: Exception) { null }
            if (base64 == null) { _loadError.value = "Не удалось отправить стикер"; return@launch }
            FirebaseRepo.sendMediaBase64(chatId, u, MessageType.STICKER, base64)
                .onFailure { e -> _loadError.value = "Не удалось отправить стикер: ${e.message}" }
        }
    }

    // Если настроен Cloudinary — грузим сжатое фото туда и шлём сообщение с готовой
    // ссылкой (sendMediaUrl). Если ключи не заданы — как и раньше, Base64 прямо в
    // документе (sendMediaBase64). Качество сжатия для Cloudinary-пути выше (не нужно
    // экономить место в документе Firestore).
    fun sendImage(uri: Uri, context: Context) {
        val u = _me.value ?: return
        _uploading.value = true
        viewModelScope.launch {
            val result = if (CloudinaryRepository.isConfigured) {
                val bytes = ImageCompress.compressToBytes(context, uri, maxDim = 1600, targetBytes = 3_000_000)
                if (bytes == null) Result.failure(Exception("Не удалось обработать изображение"))
                else CloudinaryRepository.uploadImage(bytes).fold(
                    onSuccess = { url -> FirebaseRepo.sendMediaUrl(chatId, u, MessageType.IMAGE, url) },
                    onFailure = { e ->
                        // Сбой самой загрузки (сеть, 401 из-за пресета и т.п.) — не бросаем отправку,
                        // а пробуем тот же Base64-путь, что и при полностью не настроенном Cloudinary.
                        Log.w("ChatViewModel", "Cloudinary image upload failed, falling back to Base64: ${e.message}")
                        val base64 = ImageCompress.compressToBase64(context, uri, maxDim = 1080, targetBytes = 700_000)
                        if (base64 == null) Result.failure(e)
                        else FirebaseRepo.sendMediaBase64(chatId, u, MessageType.IMAGE, base64)
                    }
                )
            } else {
                val base64 = ImageCompress.compressToBase64(context, uri, maxDim = 1080, targetBytes = 700_000)
                if (base64 == null) Result.failure(Exception("Не удалось обработать изображение"))
                else FirebaseRepo.sendMediaBase64(chatId, u, MessageType.IMAGE, base64)
            }
            result.onFailure { e -> _loadError.value = "Не удалось отправить фото: ${e.message}" }
            _uploading.value = false
        }
    }

    fun sendFile(uri: Uri, name: String, size: Long, context: Context) {
        val u = _me.value ?: return
        val cloudinaryReady = CloudinaryRepository.isConfigured
        val limit = if (cloudinaryReady) MAX_FILE_BYTES_CLOUDINARY else MAX_FILE_BYTES
        if (size > limit) {
            _loadError.value = if (cloudinaryReady) "Файл слишком большой (до ${limit / 1024 / 1024} МБ)"
                else "Файл слишком большой (до ${limit / 1024} КБ, пока не настроено внешнее хранилище)"
            return
        }
        _uploading.value = true
        viewModelScope.launch {
            val bytes = try { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (_: Exception) { null }
            if (bytes == null) { _loadError.value = "Не удалось прочитать файл"; _uploading.value = false; return@launch }
            val result = if (cloudinaryReady) {
                CloudinaryRepository.uploadFile(bytes, name).fold(
                    onSuccess = { url -> FirebaseRepo.sendMediaUrl(chatId, u, MessageType.FILE, url, fileName = name, fileSize = size) },
                    onFailure = { e ->
                        Log.w("ChatViewModel", "Cloudinary file upload failed, falling back to Base64: ${e.message}")
                        if (bytes.size <= MAX_FILE_BYTES) {
                            FirebaseRepo.sendMediaBase64(chatId, u, MessageType.FILE, Base64.encodeToString(bytes, Base64.NO_WRAP), fileName = name, fileSize = size)
                        } else {
                            // Файл прошёл проверку размера в расчёте на Cloudinary (лимит куда мягче),
                            // а на резервный Base64-путь при его нынешнем сбое он уже не влезает.
                            Result.failure(Exception("Cloudinary недоступен, а файл слишком большой для резервной отправки (до ${MAX_FILE_BYTES / 1024} КБ)"))
                        }
                    }
                )
            } else {
                FirebaseRepo.sendMediaBase64(chatId, u, MessageType.FILE, Base64.encodeToString(bytes, Base64.NO_WRAP), fileName = name, fileSize = size)
            }
            result.onFailure { e -> _loadError.value = "Не удалось отправить файл: ${e.message}" }
            _uploading.value = false
        }
    }

    // Обычное видео (не кружок) — в отличие от фото, тут мало просто "сжать": нужно
    // перекодировать видео- и аудио-дорожку через MediaCodec, иначе почти любой ролик из
    // галереи не влезет в лимит документа Firestore (1 МБ) и раньше просто отклонялся как
    // "слишком большой файл" через общий 📎-пикер — отсюда и жалоба, что видео "не грузится".
    // Быстрая проверка длительности — до тяжёлого перекодирования, чтобы сразу сказать
    // понятную причину, а не крутить транскод впустую.
    fun sendVideo(uri: Uri, context: Context) {
        val u = _me.value ?: return
        val hq = CloudinaryRepository.isConfigured
        val limitMs = VideoCompress.maxDurationMs(hq)
        val durationMs = VideoCompress.getDurationMs(context, uri)
        if (durationMs != null && durationMs > limitMs) {
            _loadError.value = "Видео длиннее ${limitMs / 1000}с — обрежь покороче"
            return
        }
        _uploading.value = true
        viewModelScope.launch {
            val bytes = try {
                // Когда подключён Cloudinary, лимита документа Firestore нет — жать видео до
                // 480p/250кбит/с больше незачем (именно это и делало картинку мыльной, а звук
                // глухим). HQ-профиль: 720p, 2.5 Мбит/с видео, 128 кбит/с звук.
                VideoCompress.compressToBytes(
                    context, uri,
                    audioBitrate = if (hq) VideoCompress.AUDIO_BITRATE_HQ else 32_000,
                    maxSide = if (hq) VideoCompress.MAX_SIDE_HQ else VideoCompress.MAX_SIDE,
                    videoBitrate = if (hq) VideoCompress.VIDEO_BITRATE_HQ else VideoCompress.VIDEO_BITRATE,
                    maxDurationMs = limitMs,
                    // HQ (Cloudinary): звук из исходного видео копируется без перекодирования
                    passthroughAudio = hq
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "sendVideo compress failed", e); null
            }
            if (bytes == null) { _loadError.value = "Не удалось обработать видео"; _uploading.value = false; return@launch }
            val dur = ((durationMs ?: 0L) / 1000).toInt()
            val result = if (CloudinaryRepository.isConfigured) {
                CloudinaryRepository.uploadVideo(bytes).fold(
                    onSuccess = { url -> FirebaseRepo.sendMediaUrl(chatId, u, MessageType.VIDEO, url, duration = dur) },
                    onFailure = { e ->
                        Log.w("ChatViewModel", "Cloudinary video upload failed, falling back to Base64: ${e.message}")
                        if (bytes.size <= MAX_FILE_BYTES) {
                            FirebaseRepo.sendVideo(chatId, u, Base64.encodeToString(bytes, Base64.NO_WRAP), dur)
                        } else {
                            // HQ-сжатое видео (720p) почти всегда крупнее лимита документа Firestore —
                            // честно говорим, что запасной путь тут не спасает, а не тратим время на
                            // повторное перекодирование "вслепую" под более низкий профиль.
                            Result.failure(Exception("Cloudinary сейчас недоступен (см. настройки загрузки), а видео слишком большое для резервной отправки"))
                        }
                    }
                )
            } else {
                FirebaseRepo.sendVideo(chatId, u, Base64.encodeToString(bytes, Base64.NO_WRAP), dur)
            }
            result.onFailure { e -> _loadError.value = "Не удалось отправить видео: ${e.message}" }
            _uploading.value = false
        }
    }

    fun startRecording(context: Context) {
        if (_isRecording.value) return
        val isPremium = _me.value?.isPremium == true
        val maxSecs = if (isPremium) MAX_VOICE_SECONDS_PREMIUM else MAX_VOICE_SECONDS_FREE
        // 240с на битрейте free (32кбит/с) не влезли бы в лимит документа Firestore (1 МБ) —
        // поэтому для длинных Premium-записей битрейт был ниже. Если настроен Cloudinary,
        // это ограничение больше не действует (файл не лежит в документе) — качество равное.
        // AAC-LC на 32 кбит/с слышно как "подводный"/металлический звук — при Cloudinary лимита
        // документа нет, поэтому берём нормальный битрейт для голоса (моно, 64 кбит/с).
        val bitrate = if (CloudinaryRepository.isConfigured) 64000 else if (isPremium) 20000 else 32000
        audioFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        recorder?.apply { setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setAudioChannels(1); setAudioEncodingBitRate(bitrate); setAudioSamplingRate(44100)
            setOutputFile(audioFile?.absolutePath); prepare(); start() }
        _isRecording.value = true; _recSecs.value = 0
        recJob = viewModelScope.launch {
            while (_isRecording.value) {
                kotlinx.coroutines.delay(1000)
                _recSecs.update { it + 1 }
                if (_recSecs.value >= maxSecs) { stopAndSend(); break }  // авто-стоп по лимиту длительности
            }
        }
    }

    fun stopAndSend() {
        if (!_isRecording.value) return
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
        recorder = null; _isRecording.value = false; recJob?.cancel()
        val dur = _recSecs.value; _recSecs.value = 0
        val u = _me.value ?: return; val f = audioFile ?: return
        if (dur < 1) { f.delete(); return }  // случайный тап без записи — не отправляем пустышку
        _uploading.value = true
        viewModelScope.launch {
            val bytes = try { f.readBytes() } catch (_: Exception) { null }
            f.delete()
            if (bytes == null) { _loadError.value = "Не удалось прочитать запись"; _uploading.value = false; return@launch }
            val result = if (CloudinaryRepository.isConfigured) {
                CloudinaryRepository.uploadAudio(bytes).fold(
                    onSuccess = { url -> FirebaseRepo.sendMediaUrl(chatId, u, MessageType.VOICE, url, duration = dur) },
                    onFailure = { e ->
                        Log.w("ChatViewModel", "Cloudinary audio upload failed, falling back to Base64: ${e.message}")
                        if (bytes.size <= MAX_FILE_BYTES) {
                            FirebaseRepo.sendVoiceMessage(chatId, u, Base64.encodeToString(bytes, Base64.NO_WRAP), dur)
                        } else {
                            Result.failure(Exception("Cloudinary сейчас недоступен, а запись слишком длинная для резервной отправки — запишите короче"))
                        }
                    }
                )
            } else {
                FirebaseRepo.sendVoiceMessage(chatId, u, Base64.encodeToString(bytes, Base64.NO_WRAP), dur)
            }
            result.onFailure { e -> _loadError.value = "Не удалось отправить голосовое: ${e.message}" }
            _uploading.value = false
        }
    }

    fun cancelRecording() {
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
        recorder = null; _isRecording.value = false; recJob?.cancel(); _recSecs.value = 0; audioFile?.delete()
    }

    // Вызывается из UI, когда пользователь начал долгое нажатие на кнопку видео-кружка —
    // само открытие камеры произойдёт чуть позже, когда TextureView отдаст SurfaceTexture
    // (см. onVideoPreviewReady). Тут только показываем строку записи с превью.
    fun beginVideoNoteRecording() {
        if (_isRecordingVideo.value) return
        _isRecordingVideo.value = true
        _videoRecSecs.value = 0
    }

    fun onVideoPreviewReady(context: Context, texture: android.graphics.SurfaceTexture) {
        lastPreviewContext = context.applicationContext
        lastPreviewTexture = texture
        if (!_isRecordingVideo.value || videoNoteRecorder != null) return
        startRecorder(context.applicationContext, texture)
    }

    private fun startRecorder(context: Context, texture: android.graphics.SurfaceTexture) {
        val hq = CloudinaryRepository.isConfigured
        val recorder = com.rosechat.data.media.VideoNoteRecorder(context, hq = hq, preferFront = preferFrontCamera)
        videoNoteRecorder = recorder
        val maxSec = com.rosechat.data.media.VideoNoteRecorder.maxDurationSec(hq)
        viewModelScope.launch {
            val ok = recorder.start(texture)
            _videoPreviewAspect.value = recorder.recordAspect
            if (!ok) {
                _loadError.value = "Не удалось включить камеру: ${recorder.lastError ?: "неизвестная причина"}"
                cancelVideoNote()
                return@launch
            }
            videoRecJob = viewModelScope.launch {
                while (_isRecordingVideo.value) {
                    kotlinx.coroutines.delay(1000)
                    _videoRecSecs.update { it + 1 }
                    if (_videoRecSecs.value >= maxSec) { stopAndSendVideoNote(); break }
                }
            }
        }
    }

    fun stopAndSendVideoNote() {
        if (!_isRecordingVideo.value) return
        _isRecordingVideo.value = false; videoRecJob?.cancel()
        val dur = _videoRecSecs.value; _videoRecSecs.value = 0
        val rec = videoNoteRecorder; videoNoteRecorder = null
        val u = _me.value
        val file = rec?.stop()
        if (file == null || u == null || dur < 1) { file?.delete(); return }
        _uploading.value = true
        viewModelScope.launch {
            val bytes = try { file.readBytes() } catch (_: Exception) { null }
            file.delete()
            if (bytes == null) { _loadError.value = "Не удалось прочитать запись"; _uploading.value = false; return@launch }
            val result = if (CloudinaryRepository.isConfigured) {
                CloudinaryRepository.uploadVideo(bytes).fold(
                    onSuccess = { url -> FirebaseRepo.sendMediaUrl(chatId, u, MessageType.VIDEO_NOTE, url, duration = dur) },
                    onFailure = { e ->
                        Log.w("ChatViewModel", "Cloudinary video-note upload failed, falling back to Base64: ${e.message}")
                        if (bytes.size <= 675_000) {
                            FirebaseRepo.sendVideoNote(chatId, u, Base64.encodeToString(bytes, Base64.NO_WRAP), dur)
                        } else {
                            Result.failure(Exception("Cloudinary сейчас недоступен, а кружок слишком большой для резервной отправки — запишите короче"))
                        }
                    }
                )
            } else if (bytes.size > 675_000) {
                // Подстраховка на случай, если битрейт на конкретном устройстве не уложился
                // в расчёт — лучше явно отказать, чем упереться в лимит документа Firestore
                // (порог в байтах ~ старый лимит 900_000 символов Base64 / 1.33).
                Result.failure(Exception("Видео получилось слишком большим — запишите короче"))
            } else {
                FirebaseRepo.sendVideoNote(chatId, u, Base64.encodeToString(bytes, Base64.NO_WRAP), dur)
            }
            result.onFailure { e -> _loadError.value = "Не удалось отправить видео: ${e.message}" }
            _uploading.value = false
        }
    }

    fun cancelVideoNote() {
        _isRecordingVideo.value = false; videoRecJob?.cancel(); _videoRecSecs.value = 0
        videoNoteRecorder?.cancel(); videoNoteRecorder = null
        lastPreviewTexture = null; lastPreviewContext = null
    }

    // Доступна ли смена камеры в принципе — не зависит от того, идёт ли прямо сейчас
    // пересоздание рекордера (см. switchCamera), проверяем по актуально закэшированному
    // контексту, а не только у живого объекта рекордера.
    fun canSwitchCamera(): Boolean {
        val recorder = videoNoteRecorder
        if (recorder != null) return recorder.canSwitchCamera()
        val ctx = lastPreviewContext ?: return false
        return com.rosechat.data.media.VideoNoteRecorder(ctx).canSwitchCamera()
    }

    // Переключение камеры прямо во время записи: старую сессию (камера+MediaRecorder)
    // закрываем и сразу открываем новую на уже имеющейся SurfaceTexture — без сброса
    // isRecordingVideo, поэтому диалог и таймер не мигают и не пересоздаются, а
    // preferFrontCamera (в отличие от старого кода) переживает пересоздание рекордера.
    fun switchCamera() {
        if (!_isRecordingVideo.value) return
        val texture = lastPreviewTexture ?: return
        val context = lastPreviewContext ?: return
        preferFrontCamera = !preferFrontCamera
        videoRecJob?.cancel()
        videoNoteRecorder?.cancel()
        videoNoteRecorder = null
        startRecorder(context, texture)
    }

    // Проигрывание голосового: декодируем Base64 во временный файл и играем через MediaPlayer.
    // Повторный тап по тому же сообщению — остановка.
    // Для голосовых, отправленных через Cloudinary (см. sendMediaUrl), ссылка лежит в
    // общем поле mediaUrl (у Message нет отдельного audioUrl — VOICE его тоже переиспользует).
    // Старые сообщения, отправленные до подключения Cloudinary, по-прежнему хранят Base64
    // в audioData и играются как раньше — проверяем именно содержимое сообщения, а не
    // текущую настройку CloudinaryRepository.isConfigured.
    fun toggleVoicePlayback(msg: Message, context: Context) {
        if (_playingId.value == msg.id) { stopPlayback(); return }
        stopPlayback()
        if (msg.audioData.isBlank() && msg.mediaUrl.isBlank()) return

        if (msg.mediaUrl.isNotBlank()) {
            // prepare() тут блокирующий и делает сетевой запрос — уводим в IO, чтобы не
            // подвесить UI-поток на медленном интернете.
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val p = MediaPlayer().apply {
                        setDataSource(msg.mediaUrl)
                        setOnCompletionListener { _playingId.value = null }
                        prepare(); start()
                    }
                    player = p
                    _playingId.value = msg.id
                } catch (_: Exception) {
                    _loadError.value = "Не удалось воспроизвести голосовое"
                }
            }
            return
        }

        try {
            val bytes = Base64.decode(msg.audioData, Base64.NO_WRAP)
            val tmp = File(context.cacheDir, "play_${msg.id}.m4a")
            tmp.writeBytes(bytes)
            player = MediaPlayer().apply {
                setDataSource(tmp.absolutePath)
                setOnCompletionListener { _playingId.value = null }
                prepare(); start()
            }
            _playingId.value = msg.id
        } catch (_: Exception) {
            _loadError.value = "Не удалось воспроизвести голосовое"
        }
    }

    private fun stopPlayback() {
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null; _playingId.value = null
    }

    override fun onCleared() {
        stopPlayback()
        cancelVideoNote()
        typingJob?.cancel()
        if (typingSent) _me.value?.uid?.let { FirebaseRepo.setTyping(chatId, it, false) }
        super.onCleared()
    }

    fun deleteMessage(msgId: String) { viewModelScope.launch { FirebaseRepo.deleteMessage(chatId, msgId) } }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(chatId: String, chatName: String, onBack: () -> Unit, onCall: (String, String, Boolean) -> Unit, onOpenInfo: () -> Unit, onOpenProfile: (String) -> Unit) {
    val context = LocalContext.current
    val vm: ChatViewModel = viewModel(key = chatId, factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(mc: Class<T>): T { @Suppress("UNCHECKED_CAST") return ChatViewModel(chatId) as T }
    })
    val messages     by vm.messages.collectAsStateWithLifecycle()
    val chat         by vm.chat.collectAsStateWithLifecycle()
    val partnerName  by vm.partnerName.collectAsStateWithLifecycle()
    val partnerUser  by vm.partnerUser.collectAsStateWithLifecycle()
    val input        by vm.input.collectAsStateWithLifecycle()
    val me           by vm.me.collectAsStateWithLifecycle()
    val isRecording  by vm.isRecording.collectAsStateWithLifecycle()
    val recSecs      by vm.recSecs.collectAsStateWithLifecycle()
    val isRecordingVideo by vm.isRecordingVideo.collectAsStateWithLifecycle()
    val videoRecSecs by vm.videoRecSecs.collectAsStateWithLifecycle()
    val videoPreviewAspect by vm.videoPreviewAspect.collectAsStateWithLifecycle()
    val uploading    by vm.uploading.collectAsStateWithLifecycle()
    val showStickers by vm.showStickers.collectAsStateWithLifecycle()
    val showGifs     by vm.showGifs.collectAsStateWithLifecycle()
    val gifQuery     by vm.gifQuery.collectAsStateWithLifecycle()
    val gifResults   by vm.gifResults.collectAsStateWithLifecycle()
    val gifLoading   by vm.gifLoading.collectAsStateWithLifecycle()
    val gifError     by vm.gifError.collectAsStateWithLifecycle()
    val editingId    by vm.editingId.collectAsStateWithLifecycle()
    val playingId    by vm.playingId.collectAsStateWithLifecycle()
    val listState    = rememberLazyListState()
    val scope        = rememberCoroutineScope()
    val micPerm      = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    val camPerm      = rememberPermissionState(android.Manifest.permission.CAMERA)
    var showAttach   by remember { mutableStateOf(false) }
    val otherUid     = chat?.memberIds?.firstOrNull { it != me?.uid } ?: ""
    val loadError    by vm.loadError.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(loadError) { loadError?.let { snackbarHost.showSnackbar(it) } }

    // Пока этот чат на экране — пуши/плашки по нему не показываем (человек и так читает),
    // а уже висящее в шторке уведомление по этому чату снимаем.
    DisposableEffect(chatId) {
        com.rosechat.data.local.InAppNotifier.openChatId = chatId
        com.rosechat.data.repository.NotificationHelper.cancelMessage(context, chatId)
        onDispose {
            if (com.rosechat.data.local.InAppNotifier.openChatId == chatId)
                com.rosechat.data.local.InAppNotifier.openChatId = null
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.sendImage(it, context) } }
    val filePicker  = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u -> val name = u.lastPathSegment ?: "file"; val size = context.contentResolver.openFileDescriptor(u,"r")?.statSize ?: 0; vm.sendFile(u, name, size, context) }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.sendVideo(it, context) } }

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    LaunchedEffect(otherUid) { if (otherUid.isNotBlank()) vm.observePartner(otherUid) }
    LaunchedEffect(messages.size, me?.uid) {
        val uid = me?.uid
        if (uid != null && messages.isNotEmpty()) {
            FirebaseRepo.clearUnread(chatId, uid)
            if (ChatSettingsState.sendReadReceipts) FirebaseRepo.markChatRead(chatId, uid)
        }
    }

    val isChannel  = chat?.type == ChatType.CHANNEL
    val isGroupish = chat?.type == ChatType.GROUP || isChannel
    val isAdmin    = me?.uid != null && chat?.adminIds?.contains(me?.uid) == true
    // В канале с ограничением "только админы" обычный подписчик не может писать
    val canType    = !isChannel || !(chat?.broadcastOnly ?: true) || isAdmin

    Scaffold(containerColor = BgPrimary,
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    val displayName = if (isGroupish) (chat?.name?.takeIf { it.isNotBlank() } ?: chatName)
                        else (partnerName?.takeIf { it.isNotBlank() } ?: chatName)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = if (!isGroupish && otherUid.isNotBlank()) Modifier.clickable { onOpenProfile(otherUid) } else Modifier) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(AccentMuted), Alignment.Center) {
                            if (isChannel) Icon(Icons.Default.Campaign, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            else Text(displayName.firstOrNull()?.toString() ?: "?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            val isTyping = otherUid.isNotBlank() && chat?.typingBy?.contains(otherUid) == true
                            when {
                                isGroupish -> {
                                    val count = chat?.memberIds?.size ?: 0
                                    Text(
                                        if (isChannel) "$count подписчиков" else "$count участников",
                                        style = MaterialTheme.typography.bodySmall, color = TextSecondary
                                    )
                                }
                                isTyping -> Text("печатает...", style = MaterialTheme.typography.bodySmall, color = Accent)
                                partnerUser?.isOnline == true -> Text("в сети", style = MaterialTheme.typography.bodySmall, color = Accent)
                                partnerUser != null -> Text(formatLastSeen(partnerUser?.lastSeen), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) } },
                actions = {
                    if (isGroupish) {
                        IconButton(onOpenInfo) { Icon(Icons.Default.Info, null, tint = TextSecondary) }
                    } else {
                        val startCall: (Boolean) -> Unit = { isVideo ->
                            val other = otherUid; val my = me
                            if (other.isNotBlank() && my != null) {
                                scope.launch {
                                    val callId = FirebaseRepo.startCall(
                                        my.uid, my.displayName, my.photoUrl, my.photoData,
                                        other, if (isVideo) CallType.VIDEO else CallType.AUDIO
                                    )
                                    onCall(callId, partnerName?.takeIf { it.isNotBlank() } ?: chatName, isVideo)
                                }
                            }
                        }
                        IconButton({ startCall(false) }) { Icon(Icons.Default.Call, null, tint = TextSecondary) }
                        IconButton({ startCall(true)  }) { Icon(Icons.Default.Videocam, null, tint = TextSecondary) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgSecondary)
            )
        },
        bottomBar = {
            Surface(color = BgSecondary) {
                Column {
                    if (uploading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = BgTertiary)
                    // Панель стикеров
                    if (showStickers) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 44.dp),
                            modifier = Modifier.fillMaxWidth().height(220.dp).background(BgTertiary).padding(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            // Картиночные стикеры — первыми, крупнее эмодзи-стикеров ниже.
                            items(IMAGE_STICKERS) { resId ->
                                Box(
                                    Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                        .clickable { vm.sendImageSticker(resId, context) },
                                    Alignment.Center
                                ) {
                                    Image(
                                        painterResource(resId), null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            items(CHAT_STICKERS) { s ->
                                Box(Modifier.size(44.dp).clickable { vm.sendSticker(s) }, Alignment.Center) {
                                    Text(s, fontSize = 26.sp)
                                }
                            }
                        }
                    }
                    // Панель GIF (Klipy)
                    if (showGifs) {
                        Column(Modifier.fillMaxWidth().height(260.dp).background(BgTertiary)) {
                            OutlinedTextField(
                                gifQuery, { vm.onGifQueryChanged(it) }, Modifier.fillMaxWidth().padding(8.dp),
                                placeholder = { Text("Поиск GIF...", color = TextSecondary) }, singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = Accent, unfocusedBorderColor = BgSecondary,
                                    cursorColor = Accent, focusedContainerColor = BgSecondary, unfocusedContainerColor = BgSecondary))
                            when {
                                !com.rosechat.data.repository.GifRepository.isConfigured -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                                    Text("GIF пока не настроены", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                gifLoading -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(28.dp))
                                }
                                gifError != null -> Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Не удалось загрузить GIF", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                                    Text(gifError ?: "", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp))
                                    TextButton(onClick = { vm.retryGifs() }) { Text("Повторить", color = Accent) }
                                }
                                gifResults.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                                    Text("Ничего не найдено", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                else -> LazyVerticalGrid(
                                    columns = GridCells.Fixed(3), modifier = Modifier.fillMaxWidth().weight(1f),
                                    contentPadding = PaddingValues(6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(gifResults, key = { it.id }) { g ->
                                        AsyncImage(g.previewUrl, null, contentScale = ContentScale.Crop,
                                            modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                                                .clickable { vm.sendGif(g.fullUrl) })
                                    }
                                }
                            }
                        }
                    }
                    if (isRecording) {
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, null, tint = Accent, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Запись... ${recSecs}с", color = TextPrimary, modifier = Modifier.weight(1f))
                            TextButton({ vm.cancelRecording() }) { Text("Отмена", color = TextSecondary) }
                            IconButton({ vm.stopAndSend() }, Modifier.size(48.dp).clip(CircleShape).background(Accent)) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    } else if (isRecordingVideo) {
                        // Полноэкранное модальное окно записи кружка, как в Telegram.
                        // Закрывает весь чат, показывает большое живое превью в центре,
                        // таймер, и кнопки отправки/отмены/смены камеры.
                        Dialog(onDismissRequest = { vm.cancelVideoNote() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                            Box(Modifier.fillMaxSize().background(Color.Black)) {
                                Column(
                                    Modifier.fillMaxSize().statusBarsPadding(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Верхний и нижний Spacer с разным весом держат превью+таймер+кнопки
                                    // не по центру экрана, а заметно выше — снизу остаётся больше пустого
                                    // места, чем сверху.
                                    Spacer(Modifier.weight(3f))

                                    // Круглое живое превью — ровно тот же приём, что и в маленькой версии
                                    // (кадр камеры почти никогда не квадратный, поэтому AndroidView делаем
                                    // крупнее круга по короткой стороне и обрезаем кругом внешним Box'ом,
                                    // а не растягиваем содержимое, как было раньше через RoundedCornerShape
                                    // на прямоугольнике — тот вариант превью был скорее овалом/прямоугольником,
                                    // не кругом).
                                    val previewSize = 260.dp
                                    Box(Modifier.size(previewSize).clip(CircleShape).background(Color.Black), Alignment.Center) {
                                        AndroidView(
                                            modifier = Modifier.size(
                                                width = previewSize * maxOf(1f, videoPreviewAspect),
                                                height = previewSize * maxOf(1f, 1f / videoPreviewAspect)
                                            ),
                                            factory = { ctx ->
                                                android.view.TextureView(ctx).apply {
                                                    surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                                        override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, w: Int, h: Int) {
                                                            vm.onVideoPreviewReady(context, texture)
                                                        }
                                                        override fun onSurfaceTextureSizeChanged(s: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                                                        override fun onSurfaceTextureDestroyed(s: android.graphics.SurfaceTexture): Boolean = true
                                                        override fun onSurfaceTextureUpdated(s: android.graphics.SurfaceTexture) {}
                                                    }
                                                }
                                            }
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "$videoRecSecs/${com.rosechat.data.media.VideoNoteRecorder.maxDurationSec(CloudinaryRepository.isConfigured)}с",
                                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    )

                                    Spacer(Modifier.height(28.dp))

                                    // Кнопки: отмена, смена камеры, отправка — сразу под таймером,
                                    // а не прижаты к самому низу экрана.
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton({ vm.cancelVideoNote() }, Modifier.size(56.dp).clip(CircleShape).background(Color.Red.copy(alpha = 0.8f))) {
                                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(28.dp))
                                        }

                                        if (vm.canSwitchCamera()) {
                                            IconButton(
                                                { vm.switchCamera() },
                                                Modifier.size(56.dp).clip(CircleShape).background(Accent.copy(alpha = 0.8f))
                                            ) {
                                                Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(24.dp))
                                            }
                                        }

                                        IconButton({ vm.stopAndSendVideoNote() }, Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF2ECC71))) {
                                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(28.dp))
                                        }
                                    }

                                    Spacer(Modifier.weight(5f))
                                }

                                // Закрыть по ESC (back button)
                                BackHandler(enabled = isRecordingVideo) {
                                    vm.cancelVideoNote()
                                }
                            }
                        }
                    } else if (!canType) {
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Lock, null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Только администраторы могут писать в этот канал", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        if (editingId != null) {
                            Row(Modifier.fillMaxWidth().background(BgTertiary).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, null, tint = Accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Редактирование сообщения", color = Accent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton({ vm.cancelEdit() }, Modifier.size(28.dp)) { Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) }
                            }
                        }
                        // Вертикальный отступ убран почти в ноль: у кнопок IconButton своя
                        // обязательная зона касания 48dp, поэтому дополнительные 8dp сверху и
                        // снизу превращались в заметную пустую полосу над системными кнопками —
                        // тот самый лишний "подбородок". navigationBarsPadding() оставлен:
                        // без него поле ввода залезет под кнопки навигации.
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.Bottom) {
                            IconButton({ vm.toggleStickers() }) { Icon(Icons.Default.EmojiEmotions, null, tint = if (showStickers) Accent else TextSecondary) }
                            IconButton({ vm.toggleGifs() }) { Icon(Icons.Default.Gif, null, tint = if (showGifs) Accent else TextSecondary) }
                            OutlinedTextField(input, { vm.onInputChanged(it) }, Modifier.weight(1f),
                                placeholder = { Text("Сообщение...", color = TextSecondary) },
                                shape = RoundedCornerShape(24.dp), maxLines = 4,
                                keyboardOptions = KeyboardOptions(imeAction = if (ChatSettingsState.enterToSend) ImeAction.Send else ImeAction.Default),
                                keyboardActions = if (ChatSettingsState.enterToSend) KeyboardActions(onSend = { vm.sendText() }) else KeyboardActions(),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = Accent, unfocusedBorderColor = BgTertiary,
                                    cursorColor = Accent, focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary))
                            Spacer(Modifier.width(6.dp))
                            if (input.isNotBlank()) {
                                IconButton({ vm.sendText() }, Modifier.size(48.dp).clip(CircleShape).background(Accent)) {
                                    Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton({ showAttach = !showAttach }) { Icon(Icons.Default.AttachFile, null, tint = if (showAttach) Accent else TextSecondary) }
                                    Box(Modifier.size(48.dp).clip(CircleShape).background(BgTertiary)
                                        .pointerInput(Unit) { detectTapGestures(onLongPress = {
                                            when {
                                                !micPerm.status.isGranted -> micPerm.launchPermissionRequest()
                                                !camPerm.status.isGranted -> camPerm.launchPermissionRequest()
                                                else -> vm.beginVideoNoteRecording()
                                            }
                                        }) }, Alignment.Center) {
                                        Icon(Icons.Default.Videocam, null, tint = Accent, modifier = Modifier.size(22.dp))
                                    }
                                    Box(Modifier.size(48.dp).clip(CircleShape).background(BgTertiary)
                                        .pointerInput(Unit) { detectTapGestures(onLongPress = {
                                            if (micPerm.status.isGranted) vm.startRecording(context) else micPerm.launchPermissionRequest()
                                        }) }, Alignment.Center) {
                                        Icon(Icons.Default.Mic, null, tint = Accent, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                        if (showAttach) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                AttachBtn("🖼", "Фото") { imagePicker.launch("image/*"); showAttach = false }
                                AttachBtn("🎬", "Видео") { videoPicker.launch("video/*"); showAttach = false }
                                AttachBtn("📎", "Файл") { filePicker.launch("*/*"); showAttach = false }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val bgPreset = me?.chatWallpaperPreset ?: ""
            if (com.rosechat.ui.common.hasWallpaper(bgPreset, me?.chatBackground ?: "", me?.chatBackgroundData ?: ""))
                com.rosechat.ui.common.ChatWallpaperView(bgPreset, me?.chatBackground ?: "", me?.chatBackgroundData ?: "", Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(BgPrimary))

            Column(Modifier.fillMaxSize()) {
                val pinnedId = chat?.pinnedMessageId ?: ""
                val pinnedMsg = if (pinnedId.isNotBlank()) messages.firstOrNull { it.id == pinnedId } else null
                if (pinnedMsg != null) {
                    Row(
                        Modifier.fillMaxWidth().background(BgSecondary)
                            .clickable {
                                val idx = messages.indexOfFirst { it.id == pinnedId }
                                if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PushPin, null, tint = Accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Закреплённое сообщение", style = MaterialTheme.typography.labelSmall, color = Accent)
                            Text(
                                pinnedMsg.text.ifBlank { when (pinnedMsg.type) { MessageType.STICKER -> pinnedMsg.sticker.ifBlank { "Стикер" }; MessageType.GIF -> "GIF"; else -> "Медиа" } },
                                style = MaterialTheme.typography.bodySmall, color = TextPrimary, maxLines = 1
                            )
                        }
                        IconButton({ FirebaseRepo.unpinMessage(chatId) }, Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val partnerReadAt = chat?.lastReadAt?.get(otherUid)
                        // Секретный режим (ChatSettingsState.showDeletedMessages, открывается кодом
                        // в Настройках чата) — раньше удалённые сообщения просто выпадали из списка
                        // насовсем, хотя в Firestore (см. FirebaseRepo.deleteMessage) текст никуда не
                        // девается, только помечается isDeleted=true. Premium-пользователям тот же
                        // переключатель доступен без кода — прямо в Настройках Premium (см.
                        // PremiumScreen), это теперь ещё и платный перк, а не только секрет для своих.
                        val deletedUnlockedForMe = ChatSettingsState.deletedMessagesUnlocked || me?.isPremium == true
                        val visibleMessages = if (deletedUnlockedForMe && ChatSettingsState.showDeletedMessages)
                            messages else messages.filter { !it.isDeleted }
                        items(visibleMessages, key = { it.id }) { msg ->
                            val isMe = msg.senderId == me?.uid
                            val isRead = isMe && partnerReadAt != null && msg.timestamp != null && partnerReadAt >= msg.timestamp
                            // Плавный сдвиг/появление в списке — раньше сообщения просто мгновенно
                            // выскакивали без анимации (см. чат: "сделай какие-нибудь анимацию в
                            // настройках"). Настройка на случай, если кому-то милее без движения.
                            val itemModifier = if (ChatSettingsState.interfaceAnimations) Modifier.animateItem() else Modifier
                            Box(itemModifier) {
                                Bubble(msg, isMe, isRead, isPinned = msg.id == pinnedId, isPlaying = playingId == msg.id,
                                    myBubbleColor = if (isMe) premiumBubbleColorOrNull(me?.premiumBubbleColor ?: "default") else null,
                                    onPlayVoice = { vm.toggleVoicePlayback(msg, context) },
                                    onDelete = { vm.deleteMessage(msg.id) }, onEdit = { vm.startEdit(msg) },
                                    onTogglePin = { if (msg.id == pinnedId) FirebaseRepo.unpinMessage(chatId) else FirebaseRepo.pinMessage(chatId, msg.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachBtn(emoji: String, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(BgTertiary), Alignment.Center) { Text(emoji, fontSize = 22.sp) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun Bubble(msg: Message, isMe: Boolean, isRead: Boolean, isPinned: Boolean, isPlaying: Boolean, myBubbleColor: Color?, onPlayVoice: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit, onTogglePin: () -> Unit) {
    val time = msg.timestamp?.toDate()?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: ""
    var showMenu by remember { mutableStateOf(false) }

    // Стикер — по центру. Эмодзи-стикер хранится в msg.sticker (см. CHAT_STICKERS); картиночный
    // стикер (см. IMAGE_STICKERS/sendImageSticker) — в тех же mediaUrl/mediaData, что и обычное
    // фото, просто без пузыря-рамки вокруг и покрупнее эмодзи.
    if (msg.type == MessageType.STICKER) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), if (isMe) Alignment.CenterEnd else Alignment.CenterStart) {
            Box {
                if (msg.sticker.isNotBlank()) {
                    Text(msg.sticker, fontSize = 48.sp, modifier = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { showMenu = true }) })
                } else if (hasImage(msg.mediaUrl, msg.mediaData)) {
                    SmartImage(
                        msg.mediaUrl, msg.mediaData,
                        Modifier.size(130.dp).pointerInput(Unit) { detectTapGestures(onLongPress = { showMenu = true }) },
                        ContentScale.Fit
                    )
                }
                // Раньше долгое нажатие на стикер включало showMenu, но само меню нигде не
                // рендерилось (ранний return выше не доходил до общего DropdownMenu) — скачать
                // картиночный стикер было невозможно. Эмодзи-стикер скачивать нечего (это просто
                // символ), поэтому пункт "Скачать" показываем только для картиночных стикеров.
                DropdownMenu(showMenu, { showMenu = false }, Modifier.background(BgSecondary)) {
                    if (hasImage(msg.mediaUrl, msg.mediaData)) {
                        DropdownMenuItem({ Text("Скачать", color = TextPrimary) }, {
                            showMenu = false
                            if (msg.mediaUrl.isNotBlank()) {
                                scope.launch { MediaSaver.saveAndOpenFromUrl(context, msg.mediaUrl, "sticker_${msg.id}.png", "image/png", "image") }
                            } else {
                                MediaSaver.saveAndOpen(context, msg.mediaData, "sticker_${msg.id}.png", "image/png", "image")
                            }
                        }, leadingIcon = { Icon(Icons.Default.Download, null, tint = TextSecondary) })
                    }
                    DropdownMenuItem({ Text(if (isPinned) "Открепить" else "Закрепить", color = TextPrimary) }, { showMenu = false; onTogglePin() }, leadingIcon = { Icon(Icons.Default.PushPin, null, tint = TextSecondary) })
                    if (isMe) DropdownMenuItem({ Text("Удалить", color = ErrorRed) }, { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = ErrorRed) })
                }
            }
        }
        return
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            if (!isMe && msg.senderName.isNotEmpty())
                Text(msg.senderName, style = MaterialTheme.typography.labelMedium, color = AccentLight, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            // Фото/видео/кружок/GIF показываем БЕЗ пузыря: раньше вокруг любого медиа рисовалась
            // толстая розовая рамка (фон пузыря + отступы 12/8 по кругу) — именно она и видна
            // на скриншотах как "большие рамки". Само медиа уже скруглено, подложка ему не нужна.
            val isBareMedia = !msg.isDeleted && msg.text.isBlank() && when (msg.type) {
                MessageType.IMAGE, MessageType.GIF, MessageType.VIDEO, MessageType.VIDEO_NOTE -> true
                else -> false
            }
            Box {
                Box(Modifier
                    .then(
                        if (isBareMedia) Modifier
                        else Modifier
                            .clip(RoundedCornerShape(topStart = if (isMe) 18.dp else 4.dp, topEnd = if (isMe) 4.dp else 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                            .background(if (isMe) (myBubbleColor ?: BubbleOut) else BubbleIn)
                    )
                    .then(if (msg.isDeleted) Modifier.alpha(0.55f) else Modifier)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { showMenu = true }) }
                    .then(if (isBareMedia) Modifier else Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    .widthIn(max = 280.dp)) {
                    when (msg.type) {
                        MessageType.IMAGE -> {
                            var showViewer by remember(msg.id) { mutableStateOf(false) }
                            // Ширина фиксирована, высота — по реальным пропорциям снимка
                            // (раньше был жёсткий квадрат 200x200 с обрезкой: вертикальные
                            // фото теряли верх и низ).
                            SmartImage(msg.mediaUrl, msg.mediaData, Modifier.width(240.dp).clip(RoundedCornerShape(14.dp)).clickable { showViewer = true }, ContentScale.Fit)
                            if (showViewer) ImageViewerDialog(msg) { showViewer = false }
                        }
                        MessageType.GIF -> AsyncImage(msg.mediaUrl, null, Modifier.width(220.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                        MessageType.VOICE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.clickable(onClick = onPlayVoice)) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = if (isMe) Color.White else TextPrimary, modifier = Modifier.size(24.dp))
                            Column { Text(if (isPlaying) "Играет..." else "Голосовое", color = if (isMe) Color.White else TextPrimary); Text("${msg.duration}с", style = MaterialTheme.typography.bodySmall, color = if (isMe) Color.White.copy(0.7f) else TextSecondary) }
                        }
                        MessageType.VIDEO_NOTE -> VideoNoteBubble(msg)
                        MessageType.VIDEO -> VideoBubble(msg)
                        MessageType.FILE -> {
                            val context = LocalContext.current
                            val scope = rememberCoroutineScope()
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.clickable {
                                    val name = msg.fileName.ifEmpty { "file" }
                                    if (msg.mediaUrl.isNotBlank()) {
                                        scope.launch { MediaSaver.saveAndOpenFromUrl(context, msg.mediaUrl, name, MediaSaver.guessMimeType(name), "file") }
                                    } else {
                                        MediaSaver.saveAndOpen(context, msg.mediaData, name, MediaSaver.guessMimeType(name), "file")
                                    }
                                }) {
                                Icon(Icons.Default.InsertDriveFile, null, tint = if (isMe) Color.White else TextPrimary, modifier = Modifier.size(24.dp))
                                Column { Text(msg.fileName.ifEmpty { "Файл" }, color = if (isMe) Color.White else TextPrimary, maxLines = 1)
                                    if (msg.fileSize > 0) Text("${msg.fileSize / 1024} KB", style = MaterialTheme.typography.bodySmall, color = if (isMe) Color.White.copy(0.7f) else TextSecondary) }
                                Icon(Icons.Default.Download, null, tint = if (isMe) Color.White.copy(0.7f) else TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                        else -> Text(msg.text, color = if (isMe) Color.White else TextPrimary,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * ChatSettingsState.textSize.scale))
                    }
                }
                DropdownMenu(showMenu, { showMenu = false }, Modifier.background(BgSecondary)) {
                    DropdownMenuItem({ Text(if (isPinned) "Открепить" else "Закрепить", color = TextPrimary) }, { showMenu = false; onTogglePin() }, leadingIcon = { Icon(Icons.Default.PushPin, null, tint = TextSecondary) })
                    if (isMe && msg.type == MessageType.TEXT) DropdownMenuItem({ Text("Редактировать", color = TextPrimary) }, { showMenu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = TextSecondary) })
                    if (isMe) DropdownMenuItem({ Text("Удалить", color = ErrorRed) }, { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = ErrorRed) })
                    DropdownMenuItem({ Text("Копировать", color = TextPrimary) }, { showMenu = false }, leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = TextSecondary) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                // Виден только в секретном режиме (см. ChatSettingsState.showDeletedMessages) —
                // без него такие сообщения вообще не попадают в этот список (см. фильтр выше).
                if (msg.isDeleted) Text("удалено", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                if (msg.isEdited) Text("изменено", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                Text(time, style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = if (isMe) TextAlign.End else TextAlign.Start)
                if (isMe) Icon(
                    if (isRead) Icons.Default.DoneAll else Icons.Default.Done, null,
                    tint = if (isRead) Accent else TextSecondary, modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// Круглый видео-кружок: до тапа — просто превью-заглушка с кнопкой play (декодировать
// Base64 заранее ради статичного кадра не стоит — секунды на распаковку ради превью того
// не стоят), по тапу — декодируем во временный файл и играем через VideoView, как и с
// голосовыми (toggleVoicePlayback), только без общего состояния во ViewModel, тут оно чисто локальное.
@Composable
private fun VideoNoteBubble(msg: Message) {
    val context = LocalContext.current
    var playing by remember(msg.id) { mutableStateOf(false) }
    // Отдельно от "playing": пока true, поверх обложки крутится спиннер — без него
    // на время буферизации/декодирования первого кадра кружок выглядел как чёрное
    // пятно, будто ничего не происходит (а не "видео думает").
    var buffering by remember(msg.id) { mutableStateOf(false) }
    val hasMedia = msg.mediaData.isNotBlank() || msg.mediaUrl.isNotBlank()
    // Камера не умеет писать квадрат (см. VideoNoteRecorder) — кадр приходит 4:3 или 16:9.
    // Чтобы кружок оставался кружком без чёрных полей, растягиваем видео ЗА пределы круга
    // по короткой стороне и обрезаем кругом (то же, что ContentScale.Crop, но для VideoView).
    val size = 180.dp
    var aspect by remember(msg.id) { mutableStateOf(1f) }
    val poster = remember(msg.mediaUrl) { CloudinaryRepository.videoPosterUrl(msg.mediaUrl) }
    Box(Modifier.size(size).clip(CircleShape).background(Color.Black), Alignment.Center) {
        // Обложка остаётся под видео и во время буферизации — так свежая копия крутящегося
        // спиннера не выглядит как провал в черноту, а сразу поверх знакомой картинки.
        if (poster.isNotBlank()) {
            AsyncImage(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (playing && hasMedia) {
            AndroidView(
                modifier = Modifier.size(
                    width = size * maxOf(1f, aspect),
                    height = size * maxOf(1f, 1f / aspect)
                ),
                factory = { ctx ->
                    var retriedPlainUrl = false
                    android.widget.VideoView(ctx).apply {
                        setOnCompletionListener { playing = false }
                        setOnInfoListener { _, what, _ ->
                            // Момент, когда реально пошли кадры — до этого держим спиннер,
                            // а не голый VideoView (у него самого кадр появляется не сразу
                            // после start(), особенно при стриминге по сети).
                            if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) buffering = false
                            false
                        }
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            if (mp.videoWidth > 0 && mp.videoHeight > 0) aspect = mp.videoWidth.toFloat() / mp.videoHeight
                            start()
                        }
                        // Раньше ошибки воспроизведения (плохая ссылка, неподдерживаемый кодек и
                        // т.п.) были полностью не видны — VideoView просто оставался чёрным
                        // кругом без единого сообщения, выглядело как "кружки не работают" без
                        // возможности понять, на каком этапе. Теперь хотя бы тост с причиной.
                        // Код 1/-1005 (IO/таймаут) на стримингованной ссылке чаще всего значит,
                        // что аккаунт Cloudinary не разрешает трансформацию q_auto,f_auto "на лету"
                        // (Strict Transformations) — прежде чем показывать ошибку, пробуем ещё раз
                        // тем же плеером по исходной, нетрансформированной ссылке.
                        setOnErrorListener { _, what, extra ->
                            if (!retriedPlainUrl && msg.mediaUrl.isNotBlank()) {
                                retriedPlainUrl = true
                                try {
                                    setVideoURI(Uri.parse(msg.mediaUrl))
                                    return@setOnErrorListener true
                                } catch (_: Exception) {}
                            }
                            playing = false; buffering = false
                            android.widget.Toast.makeText(context, "Не удалось воспроизвести кружок (код $what/$extra)", android.widget.Toast.LENGTH_SHORT).show()
                            true
                        }
                        try {
                            if (msg.mediaUrl.isNotBlank()) {
                                // Кружок из Cloudinary — стримим по адаптивной ссылке (q_auto,f_auto),
                                // без временного файла.
                                setVideoURI(Uri.parse(CloudinaryRepository.streamingVideoUrl(msg.mediaUrl)))
                            } else {
                                val bytes = Base64.decode(msg.mediaData, Base64.NO_WRAP)
                                val tmp = File(context.cacheDir, "vn_${msg.id}.mp4")
                                if (!tmp.exists()) tmp.writeBytes(bytes)
                                setVideoPath(tmp.absolutePath)
                            }
                        } catch (e: Exception) {
                            playing = false; buffering = false
                            android.widget.Toast.makeText(context, "Не удалось открыть кружок: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
            if (buffering) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton({ if (hasMedia) { playing = true; buffering = true } }) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            if (msg.duration > 0) {
                Text("${msg.duration}с", color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
            }
        }
    }
}

// Полноэкранный просмотр фото по тапу на миниатюру — раньше фото можно было только
// увидеть маленьким прямоугольником 200dp в самом пузыре чата, ни посмотреть крупно,
// ни сохранить было нельзя. Теперь ещё и приближается: pinch-to-zoom + перетаскивание
// пальцем, двойной тап приближает/возвращает как есть, обычный тап (без зума) закрывает —
// раньше приблизить и рассмотреть детали было вообще нечем, кроме исходного маленького превью.
@Composable
private fun ImageViewerDialog(msg: Message, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember(msg.id) { mutableStateOf(1f) }
    var offset by remember(msg.id) { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            SmartImage(
                msg.mediaUrl, msg.mediaData,
                Modifier
                    .fillMaxSize()
                    .pointerInput(msg.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            scale = newScale
                            offset = if (newScale <= 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(msg.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) { scale = 1f; offset = Offset.Zero } else { scale = 3f }
                            },
                            onTap = { if (scale <= 1f) onDismiss() }
                        )
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                ContentScale.Fit
            )
            Row(Modifier.align(Alignment.TopEnd).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (msg.mediaData.isNotBlank() || msg.mediaUrl.isNotBlank()) {
                    IconButton({
                        if (msg.mediaUrl.isNotBlank()) {
                            scope.launch { MediaSaver.saveAndOpenFromUrl(context, msg.mediaUrl, "photo_${msg.id}.jpg", "image/jpeg", "image") }
                        } else {
                            MediaSaver.saveAndOpen(context, msg.mediaData, "photo_${msg.id}.jpg", "image/jpeg", "image")
                        }
                    }) {
                        Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
                IconButton(onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
        }
    }
}

// Обычное видео-сообщение — тот же приём, что и в VideoNoteBubble выше (декодируем Base64
// во временный файл по тапу, играем через VideoView), но прямоугольная форма и сохранённые
// пропорции вместо обрезки в квадрат — см. VideoCompress.
@Composable
private fun VideoBubble(msg: Message) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playing by remember(msg.id) { mutableStateOf(false) }
    val hasMedia = msg.mediaData.isNotBlank() || msg.mediaUrl.isNotBlank()
    // Раньше пузырь был жёстким прямоугольником 220x260 — вертикальное видео внутри него
    // ужималось по ширине, и снизу/по бокам оставались чёрные поля (тот самый "подбородок").
    // Теперь высота считается из реальных пропорций кадра: сначала по обложке, потом
    // уточняется плеером. Чёрных полей не остаётся вообще.
    var aspect by remember(msg.id) { mutableStateOf(3f / 4f) }
    var buffering by remember(msg.id) { mutableStateOf(false) }
    val poster = remember(msg.mediaUrl) { CloudinaryRepository.videoPosterUrl(msg.mediaUrl) }
    Box(
        Modifier.width(240.dp).aspectRatio(aspect.coerceIn(0.5f, 2f))
            .clip(RoundedCornerShape(14.dp)).background(Color.Black),
        Alignment.Center
    ) {
        if (poster.isNotBlank() && !playing) {
            AsyncImage(
                poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onSuccess = { st ->
                    val w = st.painter.intrinsicSize.width
                    val h = st.painter.intrinsicSize.height
                    if (w > 0f && h > 0f) aspect = w / h
                }
            )
        }
        if (playing && hasMedia) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    var retriedPlainUrl = false
                    android.widget.VideoView(ctx).apply {
                        setOnCompletionListener { playing = false }
                        setOnInfoListener { _, what, _ ->
                            if (what == android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) buffering = false
                            false
                        }
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            if (mp.videoWidth > 0 && mp.videoHeight > 0) aspect = mp.videoWidth.toFloat() / mp.videoHeight
                            start()
                        }
                        // Код 1/-1005 (IO/таймаут) на трансформированной ссылке чаще всего значит,
                        // что у аккаунта Cloudinary включены Strict Transformations и q_auto,f_auto
                        // "на лету" не разрешена — пробуем ещё раз по исходной ссылке без неё,
                        // прежде чем показывать ошибку пользователю.
                        setOnErrorListener { _, what, extra ->
                            if (!retriedPlainUrl && msg.mediaUrl.isNotBlank()) {
                                retriedPlainUrl = true
                                try {
                                    setVideoURI(Uri.parse(msg.mediaUrl))
                                    return@setOnErrorListener true
                                } catch (_: Exception) {}
                            }
                            playing = false; buffering = false
                            android.widget.Toast.makeText(context, "Не удалось воспроизвести видео (код $what/$extra)", android.widget.Toast.LENGTH_SHORT).show()
                            true
                        }
                        try {
                            if (msg.mediaUrl.isNotBlank()) {
                                // Видео из Cloudinary — стримим по адаптивной ссылке (q_auto,f_auto),
                                // без временного файла.
                                setVideoURI(Uri.parse(CloudinaryRepository.streamingVideoUrl(msg.mediaUrl)))
                            } else {
                                val bytes = Base64.decode(msg.mediaData, Base64.NO_WRAP)
                                val tmp = File(context.cacheDir, "v_${msg.id}.mp4")
                                if (!tmp.exists()) tmp.writeBytes(bytes)
                                setVideoPath(tmp.absolutePath)
                            }
                        } catch (e: Exception) {
                            playing = false; buffering = false
                            android.widget.Toast.makeText(context, "Не удалось открыть видео: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
            if (buffering) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton({ if (hasMedia) { playing = true; buffering = true } }) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
            if (msg.duration > 0) {
                Text("${msg.duration}с", color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
            if (hasMedia) {
                IconButton(
                    {
                        if (msg.mediaUrl.isNotBlank()) {
                            scope.launch { MediaSaver.saveAndOpenFromUrl(context, msg.mediaUrl, "video_${msg.id}.mp4", "video/mp4", "video") }
                        } else {
                            MediaSaver.saveAndOpen(context, msg.mediaData, "video_${msg.id}.mp4", "video/mp4", "video")
                        }
                    },
                    Modifier.align(Alignment.TopEnd)
                ) { Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}
