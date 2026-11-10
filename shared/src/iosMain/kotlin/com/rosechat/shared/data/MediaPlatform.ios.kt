@file:OptIn(ExperimentalForeignApi::class)

package com.rosechat.shared.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePosition
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureFileOutput
import platform.AVFoundation.AVCaptureFileOutputRecordingDelegateProtocol
import platform.AVFoundation.AVCaptureMovieFileOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetMedium
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSTimer
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIView
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSError
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataTaskWithURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.lastPathComponent
import platform.Foundation.setValue
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerCameraCaptureMode
import platform.UIKit.UIImagePickerControllerCameraDevice
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume

// Те же значения, что в MediaPlatform.desktop.kt и в Android (unsigned upload preset — не секрет).
private const val CLOUDINARY_CLOUD_NAME = "guurqicg"
private const val CLOUDINARY_UPLOAD_PRESET = "Pinggram"

// ── вспомогательное ──────────────────────────────────────────────────────────────────────

private fun NSData.toByteArray(): ByteArray {
    val size = this.length.toInt()
    val out = ByteArray(size)
    if (size > 0) {
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, this.length) }
    }
    return out
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
}

private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic" -> "image/heic"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    "m4a" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    "pdf" -> "application/pdf"
    "txt" -> "text/plain"
    "html", "htm" -> "text/html"
    "zip" -> "application/zip"
    else -> "application/octet-stream"
}

// Самый верхний экран — на нём показываем системные окна выбора файла / просмотра.
private fun topViewController(): UIViewController? {
    var vc: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (true) {
        val next = vc?.presentedViewController ?: break
        vc = next
    }
    return vc
}

// UIKit держит делегатов слабой ссылкой — пока окно открыто, держим их сами.
private var activeDelegate: Any? = null
private var previewHolder: Any? = null

// ── выбор файла ──────────────────────────────────────────────────────────────────────────

private class ImagePickerDelegate(
    private val kind: MediaKind,
    private val onDone: (PickedFile?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val stamp = NSDate().timeIntervalSince1970.toLong()
        val result: PickedFile? = if (kind == MediaKind.IMAGE) {
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
            val data = image?.let { UIImageJPEGRepresentation(it, 0.85) }
            if (data == null) null else {
                val bytes = data.toByteArray()
                PickedFile("photo_$stamp.jpg", bytes, "image/jpeg", bytes.size.toLong())
            }
        } else {
            val url = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL
            val data = url?.let { NSData.dataWithContentsOfURL(it) }
            if (url == null || data == null) null else {
                val bytes = data.toByteArray()
                val name = url.lastPathComponent ?: "video_$stamp.mov"
                PickedFile(name, bytes, guessMime(name), bytes.size.toLong())
            }
        }
        onDone(result)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onDone(null)
    }
}

private class DocumentPickerDelegate(
    private val onDone: (PickedFile?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        val accessed = url?.startAccessingSecurityScopedResource() ?: false
        val data = url?.let { NSData.dataWithContentsOfURL(it) }
        if (accessed) url?.stopAccessingSecurityScopedResource()
        if (url == null || data == null) {
            onDone(null)
        } else {
            val bytes = data.toByteArray()
            val name = url.lastPathComponent ?: "file"
            onDone(PickedFile(name, bytes, guessMime(name), bytes.size.toLong()))
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onDone(null)
    }
}

actual suspend fun pickFile(kind: MediaKind): PickedFile? = suspendCancellableCoroutine { cont ->
    val presenter = topViewController()
    if (presenter == null) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }
    var finished = false
    val finish: (PickedFile?) -> Unit = { picked ->
        if (!finished) {
            finished = true
            activeDelegate = null
            cont.resume(picked)
        }
    }
    if (kind == MediaKind.FILE) {
        val delegate = DocumentPickerDelegate(finish)
        activeDelegate = delegate
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.item"),
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
        )
        picker.delegate = delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    } else {
        val delegate = ImagePickerDelegate(kind, finish)
        activeDelegate = delegate
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        picker.mediaTypes = if (kind == MediaKind.IMAGE) listOf("public.image") else listOf("public.movie")
        picker.delegate = delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}

// ── загрузка в Cloudinary ───────────────────────────────────────────────────────────────

private fun buildMultipartBody(boundary: String, fileBytes: ByteArray, fileName: String, mimeType: String): ByteArray {
    val head = StringBuilder()
    head.append("--").append(boundary).append("\r\n")
    head.append("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n").append(CLOUDINARY_UPLOAD_PRESET).append("\r\n")
    head.append("--").append(boundary).append("\r\n")
    head.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName.replace("\"", "_")).append("\"\r\n")
    head.append("Content-Type: ").append(mimeType).append("\r\n\r\n")
    val tail = "\r\n--$boundary--\r\n"
    return head.toString().encodeToByteArray() + fileBytes + tail.encodeToByteArray()
}

actual suspend fun cloudinaryUpload(
    bytes: ByteArray,
    fileName: String,
    resourceType: String,
    mimeType: String
): Result<String> = suspendCancellableCoroutine { cont ->
    val url = NSURL.URLWithString("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/$resourceType/upload")
    if (url == null) {
        cont.resume(Result.failure(IllegalStateException("Некорректный адрес загрузки")))
        return@suspendCancellableCoroutine
    }
    val boundary = "----PingGramBoundary" + NSUUID().UUIDString
    val body = buildMultipartBody(boundary, bytes, fileName, mimeType)
    val request = NSMutableURLRequest.requestWithURL(url)
    request.HTTPMethod = "POST"
    request.setTimeoutInterval(180.0)
    request.setValue("multipart/form-data; boundary=$boundary", forHTTPHeaderField = "Content-Type")
    request.HTTPBody = body.toNSData()
    val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
        val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
        val text = data?.toByteArray()?.decodeToString() ?: ""
        if (error != null && status == null) {
            cont.resume(Result.failure(Exception("Нет соединения: ${error.localizedDescription}")))
        } else if (status == null || status !in 200..299) {
            cont.resume(Result.failure(Exception("Cloudinary вернул код $status: ${text.take(300)}")))
        } else {
            val match = Regex("\"secure_url\"\\s*:\\s*\"([^\"]+)\"").find(text)
            val link = match?.groupValues?.get(1)?.replace("\\/", "/")
            if (link.isNullOrBlank()) cont.resume(Result.failure(Exception("Не удалось разобрать ответ Cloudinary")))
            else cont.resume(Result.success(link))
        }
    }
    cont.invokeOnCancellation { task.cancel() }
    task.resume()
}

// ── скачать и открыть ───────────────────────────────────────────────────────────────────

private class PreviewDelegate(private val presenter: UIViewController) : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
    override fun documentInteractionControllerViewControllerForPreview(controller: UIDocumentInteractionController): UIViewController = presenter
}

// Скачивает файл во временную папку и открывает системным просмотром (QuickLook): он сам
// проигрывает видео и голосовые, показывает документы; если не может — меню «Поделиться».
actual suspend fun downloadAndOpen(url: String, fileName: String): Result<Unit> {
    val bytes = fetchBytes(url).getOrElse { return Result.failure(it) }
    val safeName = fileName.ifBlank { "pinggram_file" }.replace("/", "_")
    val path = NSTemporaryDirectory() + safeName
    if (!bytes.toNSData().writeToFile(path, atomically = true)) {
        return Result.failure(Exception("Не удалось сохранить файл"))
    }
    val fileUrl = NSURL.fileURLWithPath(path)
    val presenter = topViewController() ?: return Result.failure(Exception("Нет окна для показа файла"))
    val controller = UIDocumentInteractionController.interactionControllerWithURL(fileUrl)
    val delegate = PreviewDelegate(presenter)
    previewHolder = listOf(controller, delegate)
    controller.delegate = delegate
    if (!controller.presentPreviewAnimated(true)) {
        val share = UIActivityViewController(activityItems = listOf(fileUrl), applicationActivities = null)
        presenter.presentViewController(share, animated = true, completion = null)
    }
    return Result.success(Unit)
}

// ── скачать байты и показать картинку ───────────────────────────────────────────────────

actual suspend fun fetchBytes(url: String): Result<ByteArray> =
    suspendCancellableCoroutine { cont ->
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            cont.resume(Result.failure(IllegalArgumentException("Некорректная ссылка")))
            return@suspendCancellableCoroutine
        }
        val task = NSURLSession.sharedSession.dataTaskWithURL(nsUrl) { data, _, error ->
            if (data != null && error == null) {
                cont.resume(Result.success(data.toByteArray()))
            } else {
                cont.resume(Result.failure(Exception(error?.localizedDescription ?: "Ошибка загрузки")))
            }
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    try {
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Throwable) {
        null
    }

actual val mediaStorageLabel: String = "Cloudinary · $CLOUDINARY_CLOUD_NAME"


// ── Запись голосовых (AVAudioRecorder → AAC в .m4a) ─────────────────────────────────────
private var voiceRecorder: AVAudioRecorder? = null
private var voicePath: String? = null
private var voiceStartedAt: Double = 0.0

actual fun voiceRecordingSupported(): Boolean = true

private suspend fun requestMicPermission(): Boolean = suspendCancellableCoroutine { cont ->
    AVAudioSession.sharedInstance().requestRecordPermission { granted ->
        if (cont.isActive) cont.resume(granted)
    }
}

actual suspend fun startVoiceRecording(): Result<Unit> {
    if (voiceRecorder != null) return Result.success(Unit)
    // Без этой строки в Info.plist iOS закрывает приложение при запросе микрофона. Проверяем заранее.
    if (NSBundle.mainBundle.objectForInfoDictionaryKey("NSMicrophoneUsageDescription") == null) {
        return Result.failure(Exception("В этой сборке нет разрешения на микрофон (NSMicrophoneUsageDescription в Info.plist). Нужно обновить project.yml и пересобрать"))
    }
    if (!requestMicPermission()) {
        return Result.failure(Exception("Нет доступа к микрофону. Разрешите его в Настройки → PingGram"))
    }
    val session = AVAudioSession.sharedInstance()
    if (!session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)) {
        return Result.failure(Exception("Не удалось включить микрофон (аудиосессия)"))
    }
    session.setActive(true, error = null)

    val path = NSTemporaryDirectory() + "voice_" + NSUUID().UUIDString + ".m4a"
    val url = NSURL.fileURLWithPath(path)
    val settings: Map<Any?, Any?> = mapOf(
        AVFormatIDKey to kAudioFormatMPEG4AAC.toInt(),
        AVSampleRateKey to 44100.0,
        AVNumberOfChannelsKey to 1,
        AVEncoderAudioQualityKey to 96, // AVAudioQualityHigh
    )
    return memScoped {
        val err = alloc<ObjCObjectVar<NSError?>>()
        // Если iOS вернёт nil, конструктор бросает исключение — ловим его, чтобы приложение не закрылось
        val recorder = try { AVAudioRecorder(url, settings, err.ptr) } catch (e: Throwable) { null }
        if (recorder == null) {
            Result.failure(Exception(err.value?.localizedDescription ?: "Не удалось создать запись"))
        } else if (!recorder.prepareToRecord() || !recorder.record()) {
            Result.failure(Exception(err.value?.localizedDescription ?: "Не удалось начать запись"))
        } else {
            voiceRecorder = recorder
            voicePath = path
            voiceStartedAt = NSDate().timeIntervalSince1970
            Result.success(Unit)
        }
    }
}

actual suspend fun stopVoiceRecording(): PickedFile? {
    val recorder = voiceRecorder ?: return null
    val path = voicePath
    val seconds = NSDate().timeIntervalSince1970 - voiceStartedAt
    recorder.stop()
    voiceRecorder = null
    voicePath = null
    AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
    if (path == null || seconds < 0.7) return null
    val data = NSData.dataWithContentsOfURL(NSURL.fileURLWithPath(path)) ?: return null
    val bytes = data.toByteArray()
    if (bytes.isEmpty()) return null
    return PickedFile("voice_${NSDate().timeIntervalSince1970.toLong()}.m4a", bytes, "audio/mp4", bytes.size.toLong())
}

actual fun cancelVoiceRecording() {
    val recorder = voiceRecorder ?: return
    recorder.stop()
    recorder.deleteRecording()
    voiceRecorder = null
    voicePath = null
    AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
}


// ── Видео-кружок: своя камера с круглым превью (фронтальная, до 60 с) ───────────────────
actual fun videoNoteSupported(): Boolean =
    UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)

private suspend fun requestCaptureAccess(mediaType: String?): Boolean = suspendCancellableCoroutine { cont ->
    AVCaptureDevice.requestAccessForMediaType(mediaType) { granted: Boolean ->
        if (cont.isActive) cont.resume(granted)
    }
}

private class VideoNoteTarget(
    private val onRecord: () -> Unit,
    private val onCancel: () -> Unit,
    private val onFlip: () -> Unit,
) : NSObject() {
    @ObjCAction
    fun recordTapped() { onRecord() }

    @ObjCAction
    fun cancelTapped() { onCancel() }

    @ObjCAction
    fun flipTapped() { onFlip() }
}

private class VideoNoteController(private val finish: (PickedFile?) -> Unit) :
    UIViewController(nibName = null, bundle = null), AVCaptureFileOutputRecordingDelegateProtocol {

    private val session = AVCaptureSession()
    private val movieOutput = AVCaptureMovieFileOutput()
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private val circle = UIView()
    private val recordButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val cancelButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val flipButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val target = VideoNoteTarget({ toggleRecording() }, { cancelTapped() }, { flipCamera() })
    private var cameraPosition: AVCaptureDevicePosition = AVCaptureDevicePositionFront
    private var cameraInput: AVCaptureDeviceInput? = null
    private var recording = false
    private var cancelled = false
    private var done = false
    private var timer: NSTimer? = null

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor
        circle.backgroundColor = UIColor.darkGrayColor
        circle.layer.masksToBounds = true
        view.addSubview(circle)

        recordButton.setTitle("●  Записать", forState = UIControlStateNormal)
        recordButton.setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
        recordButton.backgroundColor = UIColor.redColor
        recordButton.layer.cornerRadius = 25.0
        recordButton.addTarget(target, NSSelectorFromString("recordTapped"), UIControlEventTouchUpInside)
        view.addSubview(recordButton)

        cancelButton.setTitle("Отмена", forState = UIControlStateNormal)
        cancelButton.setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
        cancelButton.addTarget(target, NSSelectorFromString("cancelTapped"), UIControlEventTouchUpInside)
        view.addSubview(cancelButton)

        flipButton.setTitle("⟲  Сменить камеру", forState = UIControlStateNormal)
        flipButton.setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
        flipButton.addTarget(target, NSSelectorFromString("flipTapped"), UIControlEventTouchUpInside)
        view.addSubview(flipButton)

        setupSession()
    }

    private fun cameraFor(position: AVCaptureDevicePosition): AVCaptureDevice? =
        AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
            deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
            mediaType = AVMediaTypeVideo,
            position = position
        ).devices.firstOrNull() as? AVCaptureDevice

    private fun flipCamera() {
        if (done || recording) return
        val newPosition = if (cameraPosition == AVCaptureDevicePositionFront) AVCaptureDevicePositionBack else AVCaptureDevicePositionFront
        val camera = cameraFor(newPosition) ?: return
        val newInput = AVCaptureDeviceInput.deviceInputWithDevice(camera, null) ?: return
        session.beginConfiguration()
        cameraInput?.let { session.removeInput(it) }
        if (session.canAddInput(newInput)) {
            session.addInput(newInput)
            cameraInput = newInput
            cameraPosition = newPosition
        } else {
            cameraInput?.let { session.addInput(it) }
        }
        session.commitConfiguration()
    }

    private fun setupSession() {
        session.sessionPreset = AVCaptureSessionPresetMedium
        val camera = cameraFor(cameraPosition)
        if (camera != null) {
            val input = AVCaptureDeviceInput.deviceInputWithDevice(camera, null)
            if (input != null && session.canAddInput(input)) {
                session.addInput(input)
                cameraInput = input
            }
        }
        val mic = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio)
        if (mic != null) {
            val input = AVCaptureDeviceInput.deviceInputWithDevice(mic, null)
            if (input != null && session.canAddInput(input)) session.addInput(input)
        }
        if (session.canAddOutput(movieOutput)) session.addOutput(movieOutput)
        val layer = AVCaptureVideoPreviewLayer.layerWithSession(session)
        layer.videoGravity = AVLayerVideoGravityResizeAspectFill
        circle.layer.addSublayer(layer)
        previewLayer = layer
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        val w = view.bounds.useContents { size.width }
        val h = view.bounds.useContents { size.height }
        val d = minOf(w * 0.8, 340.0)
        val top = h * 0.16
        circle.setFrame(CGRectMake((w - d) / 2.0, top, d, d))
        circle.layer.cornerRadius = d / 2.0
        previewLayer?.setFrame(CGRectMake(0.0, 0.0, d, d))
        recordButton.setFrame(CGRectMake(w / 2.0 - 120.0, top + d + 40.0, 240.0, 50.0))
        cancelButton.setFrame(CGRectMake(w / 2.0 - 120.0, top + d + 104.0, 240.0, 44.0))
        flipButton.setFrame(CGRectMake(w / 2.0 - 120.0, top + d + 152.0, 240.0, 44.0))
    }

    override fun viewWillAppear(animated: Boolean) {
        super.viewWillAppear(animated)
        session.startRunning()
    }

    private fun toggleRecording() {
        if (done) return
        if (!recording) {
            val path = NSTemporaryDirectory() + "vn_" + NSUUID().UUIDString + ".mov"
            movieOutput.startRecordingToOutputFileURL(NSURL.fileURLWithPath(path), this)
            recording = true
            flipButton.setEnabled(false)
            recordButton.setTitle("■  Стоп и отправить", forState = UIControlStateNormal)
            timer = NSTimer.scheduledTimerWithTimeInterval(60.0, false) { _ ->
                if (recording) movieOutput.stopRecording()
            }
        } else {
            movieOutput.stopRecording()
        }
    }

    private fun cancelTapped() {
        if (done) return
        cancelled = true
        if (recording) movieOutput.stopRecording() else complete(null)
    }

    override fun captureOutput(
        output: AVCaptureFileOutput,
        didFinishRecordingToOutputFileAtURL: NSURL,
        fromConnections: List<*>,
        error: NSError?,
    ) {
        timer?.invalidate()
        timer = null
        recording = false
        if (cancelled) { complete(null); return }
        val data = NSData.dataWithContentsOfURL(didFinishRecordingToOutputFileAtURL)
        if (data == null || data.length.toInt() == 0) { complete(null); return }
        val bytes = ByteArray(data.length.toInt())
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        complete(PickedFile("video_note_${NSDate().timeIntervalSince1970.toLong()}.mov", bytes, "video/quicktime", bytes.size.toLong()))
    }

    private fun complete(file: PickedFile?) {
        if (done) return
        done = true
        session.stopRunning()
        dismissViewControllerAnimated(true) { finish(file) }
    }
}

actual suspend fun recordVideoNote(): PickedFile? {
    if (!videoNoteSupported()) return null
    if (!requestCaptureAccess(AVMediaTypeVideo)) throw Exception("Нет доступа к камере. Разрешите его в Настройки → PingGram")
    if (!requestCaptureAccess(AVMediaTypeAudio)) throw Exception("Нет доступа к микрофону. Разрешите его в Настройки → PingGram")
    return suspendCancellableCoroutine { cont ->
        val presenter = topViewController()
        if (presenter == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val controller = VideoNoteController { picked ->
            activeDelegate = null
            if (cont.isActive) cont.resume(picked)
        }
        activeDelegate = controller
        controller.modalPresentationStyle = UIModalPresentationFullScreen
        presenter.presentViewController(controller, animated = true, completion = null)
    }
}
