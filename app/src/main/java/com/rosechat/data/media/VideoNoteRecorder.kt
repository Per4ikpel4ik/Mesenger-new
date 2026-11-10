package com.rosechat.data.media

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "VideoNoteRecorder"

/**
 * Запись коротких круглых видео-заметок ("кружки", как в Telegram) через Camera2 + MediaRecorder.
 *
 * ГЛАВНОЕ ИСПРАВЛЕНИЕ (1.1.6.0): раньше тут жёстко стоял setVideoSize(320, 320) — квадрат
 * под круглый показ в чате. Но при VideoSource.SURFACE размер из setVideoSize становится
 * размером поверхности, в которую пишет САМА КАМЕРА, а Camera2 принимает только те размеры,
 * которые устройство реально отдаёт (StreamConfigurationMap.getOutputSizes). Квадрат 320x320
 * не поддерживает практически ни один телефон — поэтому createCaptureSession падал в
 * onConfigureFailed, и наружу шло ровно то, что видно на скриншоте:
 * "Не удалось включить камеру: не удалось настроить сессию камеры".
 * Теперь размер выбирается из списка поддерживаемых, а круг делается кропом уже при показе
 * (см. VideoNoteBubble в ChatScreen — видео выходит за пределы круга и обрезается им).
 *
 * Второе: раньше не выставлялся setOrientationHint — кружок с фронталки записывался
 * "лёжа на боку". Теперь ориентация берётся из характеристик сенсора.
 *
 * Качество: когда настроен Cloudinary (обычный случай), размер файла ничем не ограничен,
 * и писать в 250 кбит/с незачем — это был обход лимита документа Firestore в 1 МБ.
 * HQ-режим пишет 2.5 Мбит/с видео + 128 кбит/с звук, до 60с.
 */
class VideoNoteRecorder(private val context: Context, private val hq: Boolean = false, preferFront: Boolean = true) {
    companion object {
        const val MAX_DURATION_SEC = 20          // Base64-путь (Cloudinary не настроен)
        const val MAX_DURATION_SEC_HQ = 60       // путь через Cloudinary

        private const val VIDEO_BITRATE = 250_000
        private const val VIDEO_BITRATE_HQ = 2_500_000
        private const val AUDIO_BITRATE = 32_000
        private const val AUDIO_BITRATE_HQ = 128_000
        private const val FRAME_RATE = 24

        // Целевая короткая сторона кадра. Кружок показывается небольшим кругом — гнаться за
        // 1080p смысла нет, это только вес файла и время загрузки.
        private const val TARGET_SHORT_SIDE = 480
        private const val TARGET_SHORT_SIDE_HQ = 720

        fun maxDurationSec(hq: Boolean) = if (hq) MAX_DURATION_SEC_HQ else MAX_DURATION_SEC

        // CameraDevice.StateCallback.onError коды — расшифровка для тоста в UI.
        fun describeCameraError(code: Int): String = when (code) {
            CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "камера занята другим приложением"
            CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "все камеры устройства заняты"
            CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "камера отключена политикой устройства"
            CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "ошибка камеры на уровне устройства"
            CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "сбой сервиса камеры (нужен перезапуск телефона)"
            else -> "код $code"
        }
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var outputFile: File? = null
    private var released = false

    var lastError: String? = null
        private set

    // Пропорции выбранного камерой кадра — нужны UI, чтобы живое превью в кружке
    // обрезалось по кругу, а не растягивалось (TextureView сам растянул бы буфер
    // камеры под свои границы и сплющил лицо).
    var recordAspect: Float = 1f
        private set

    private fun startBackgroundThread() {
        val t = HandlerThread("VideoNoteCamera").also { it.start() }
        backgroundThread = t
        backgroundHandler = Handler(t.looper)
    }

    private var currentCameraIsFront = preferFront
    private var allCameraIds: List<String> = emptyList()

    // Реальная причина "рваного"/ускоренного или замедленного звука в записях: при
    // VideoSource.SURFACE кадры в MediaRecorder подаёт не он сам, а камера через Camera2 —
    // и если её реальный fps не совпадает с тем, что задан в setVideoFrameRate(), звук (который
    // пишется по настоящему времени) и видео (которое считает кадры по заявленному fps)
    // расходятся по темпу. Без явного CONTROL_AE_TARGET_FPS_RANGE камера сама выбирает
    // диапазон (часто "плавающий", напр. 15–30 для автоэкспозиции) — берём вместо этого
    // ближайший ФИКСИРОВАННЫЙ диапазон из реально поддерживаемых устройством и потом пишем
    // в setVideoFrameRate() именно это значение, а не константу.
    //
    // ОГРАНИЧЕНИЕ этого подхода (если звук всё ещё "плывёт" после этого): некоторые
    // устройства/прошивки вообще не сообщают ни одного диапазона с lower == upper — то есть
    // "истинно фиксированного" fps на таком телефоне попросту нет, и даже ближайший fallback-
    // диапазон остаётся плавающим (автоэкспозиция может урезать реальный fps в темноте).
    // wasFixedRange ниже — чтобы это было видно в logcat, а не оставалось загадкой.
    var lastRangeWasFixed: Boolean = true
        private set

    private fun chooseFpsRange(chars: CameraCharacteristics, target: Int): Range<Int> {
        val ranges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
        val fixed = ranges.filter { it.lower == it.upper }
        lastRangeWasFixed = fixed.isNotEmpty()
        val pool = if (fixed.isNotEmpty()) fixed else ranges.toList()
        return pool.minByOrNull { kotlin.math.abs(it.upper - target) + kotlin.math.abs(it.lower - target) }
            ?: Range(target, target)
    }

    private fun findAllCameras(manager: CameraManager): List<String> {
        return try { manager.cameraIdList.toList() } catch (e: Exception) { emptyList() }
    }

    private fun findFrontCameraId(manager: CameraManager): String? {
        return try {
            manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: manager.cameraIdList.firstOrNull()
        } catch (e: Exception) { Log.e(TAG, "findFrontCameraId failed", e); null }
    }

    private fun findBackCameraId(manager: CameraManager): String? {
        return try {
            manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (e: Exception) { Log.e(TAG, "findBackCameraId failed", e); null }
    }

    // Из всех размеров, которые камера реально умеет отдавать в MediaRecorder, берём самый
    // близкий к квадрату (кружок всё равно кропается в круг — чем ближе к 1:1, тем меньше
    // отрезается) и не крупнее нужного. Если список пуст (бывает на странных прошивках) —
    // откатываемся на 640x480, он поддерживается практически везде.
    private fun chooseRecordSize(chars: CameraCharacteristics): Size {
        val target = if (hq) TARGET_SHORT_SIDE_HQ else TARGET_SHORT_SIDE
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) as? StreamConfigurationMap
        val sizes = try { map?.getOutputSizes(MediaRecorder::class.java)?.toList() } catch (e: Exception) {
            Log.e(TAG, "getOutputSizes failed", e); null
        } ?: emptyList()
        if (sizes.isEmpty()) return Size(640, 480)

        // Сначала те, у которых короткая сторона не меньше целевой (иначе картинка мыльная);
        // среди них — самый "квадратный" и самый маленький. Если таких нет — лучшее из того,
        // что есть.
        val fitting = sizes.filter { minOf(it.width, it.height) >= target }
        val pool = if (fitting.isNotEmpty()) fitting else sizes
        return pool.minByOrNull { s ->
            val aspect = maxOf(s.width, s.height).toFloat() / minOf(s.width, s.height).toFloat()
            // отклонение от квадрата весит больше, чем лишние пиксели
            (aspect - 1f) * 1000f + minOf(s.width, s.height).toFloat() / 100f
        } ?: Size(640, 480)
    }

    // Переключить на другую камеру (фронт ↔ основная) — нужно перезапустить запись.
    // Вернёт true если переключение удалось, false если другой камеры нет или был сбой.
    fun canSwitchCamera(): Boolean {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val front = findFrontCameraId(manager)
        val back = findBackCameraId(manager)
        return front != null && back != null && front != back
    }

    fun switchCamera() {
        currentCameraIsFront = !currentCameraIsFront
    }

    @Suppress("MissingPermission")
    suspend fun start(texture: SurfaceTexture): Boolean = suspendCancellableCoroutine { cont ->
        try {
            startBackgroundThread()
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            allCameraIds = findAllCameras(manager)
            val id = if (currentCameraIsFront) findFrontCameraId(manager) else findBackCameraId(manager)
                ?: findFrontCameraId(manager)
            if (id == null) { lastError = "не найдена камера"; cont.resume(false); return@suspendCancellableCoroutine }

            val chars = manager.getCameraCharacteristics(id)
            val size = chooseRecordSize(chars)
            val fpsRange = chooseFpsRange(chars, FRAME_RATE)
            val actualFps = fpsRange.upper
            val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val isFront = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            Log.i(TAG, "record size=${size.width}x${size.height} fps=$fpsRange (fixed=$lastRangeWasFixed) sensor=$sensorOrientation front=$isFront hq=$hq")
            if (!lastRangeWasFixed) {
                Log.w(TAG, "device has NO fixed AE fps range — real capture rate can still drift " +
                        "in low light, audio/video sync isn't fully guaranteed on this hardware")
            }
            // Кадр приходит в "лежачей" ориентации сенсора, а показываем мы его повёрнутым
            // на 90° — поэтому для превью пропорции переворачиваются.
            recordAspect = size.height.toFloat() / size.width.toFloat()

            outputFile = File(context.cacheDir, "video_note_${System.currentTimeMillis()}.mp4")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            recorder.apply {
                // CAMCORDER вместо MIC — профиль, настроенный именно под запись видео
                // (шумоподавление рассчитано на направление камеры, а не звонков).
                setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoSize(size.width, size.height)
                // Обязательно то же значение, что и в CONTROL_AE_TARGET_FPS_RANGE ниже —
                // иначе видео и звук расходятся по темпу (см. комментарий у chooseFpsRange).
                setVideoFrameRate(actualFps)
                setVideoEncodingBitRate(if (hq) VIDEO_BITRATE_HQ else VIDEO_BITRATE)
                setAudioEncodingBitRate(if (hq) AUDIO_BITRATE_HQ else AUDIO_BITRATE)
                setAudioSamplingRate(if (hq) 44100 else 22050)
                // Телефон держат вертикально, поэтому поворот кадра = ориентация сенсора
                // (у фронтальной камеры — зеркально).
                setOrientationHint(if (isFront) (360 - sensorOrientation) % 360 else sensorOrientation)
                setMaxDuration(maxDurationSec(hq) * 1000)
                setOutputFile(outputFile!!.absolutePath)
                prepare()
            }
            mediaRecorder = recorder

            // Превью запрашивает у камеры тот же размер, что и запись — иначе Camera2 поднимает
            // второй поток другого разрешения, а на части устройств это снова роняет
            // конфигурацию сессии.
            texture.setDefaultBufferSize(size.width, size.height)
            val previewSurface = Surface(texture)
            val recorderSurface = recorder.surface

            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraDevice = device
                    try {
                        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
                        builder.addTarget(previewSurface)
                        builder.addTarget(recorderSurface)
                        device.createCaptureSession(listOf(previewSurface, recorderSurface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                try {
                                    session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                                    recorder.start()
                                    if (cont.isActive) cont.resume(true)
                                } catch (e: Exception) {
                                    Log.e(TAG, "setRepeatingRequest/recorder.start failed", e)
                                    lastError = "запись: ${e.message ?: e.javaClass.simpleName}"
                                    if (cont.isActive) cont.resume(false)
                                }
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                Log.e(TAG, "capture session configuration failed (size=${size.width}x${size.height})")
                                lastError = "камера не приняла размер ${size.width}x${size.height}"
                                if (cont.isActive) cont.resume(false)
                            }
                        }, backgroundHandler)
                    } catch (e: Exception) {
                        Log.e(TAG, "createCaptureSession failed", e)
                        lastError = "сессия камеры: ${e.message ?: e.javaClass.simpleName}"
                        if (cont.isActive) cont.resume(false)
                    }
                }
                override fun onDisconnected(device: CameraDevice) { device.close(); cameraDevice = null }
                override fun onError(device: CameraDevice, error: Int) {
                    Log.e(TAG, "camera onError: $error")
                    lastError = describeCameraError(error)
                    device.close(); cameraDevice = null
                    if (cont.isActive) cont.resume(false)
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            lastError = e.message ?: e.javaClass.simpleName
            if (cont.isActive) cont.resume(false)
        }
    }

    // Останавливает запись и освобождает камеру. Возвращает файл с записью
    // (или null, если что-то пошло не так и файла по сути нет).
    fun stop(): File? {
        val f = outputFile
        try { mediaRecorder?.stop() } catch (e: Exception) { Log.e(TAG, "recorder.stop failed", e) }
        release()
        return f?.takeIf { it.exists() && it.length() > 0 }
    }

    fun cancel() {
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        release()
        outputFile?.delete()
        outputFile = null
    }

    private fun release() {
        if (released) return
        released = true
        try { captureSession?.close() } catch (_: Exception) {}
        try { cameraDevice?.close() } catch (_: Exception) {}
        try { mediaRecorder?.reset() } catch (_: Exception) {}
        try { mediaRecorder?.release() } catch (_: Exception) {}
        captureSession = null; cameraDevice = null; mediaRecorder = null
        backgroundThread?.quitSafely()
        try { backgroundThread?.join(500) } catch (_: InterruptedException) {}
        backgroundThread = null; backgroundHandler = null
    }
}
