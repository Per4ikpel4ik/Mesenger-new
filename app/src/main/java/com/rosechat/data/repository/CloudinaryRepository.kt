package com.rosechat.data.repository

import android.util.Log
import com.rosechat.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

// Хранилище фото/видео/файлов/аватаров — вместо Base64 прямо в документе Firestore
// грузим сам файл во внешний сервис (Cloudinary, cloudinary.com) и храним в Firestore
// только ссылку (mediaUrl/photoUrl/chatBackground — эти поля для этого и заведены,
// см. Models.kt). Firebase-тариф это не трогает вообще — Cloudinary отдельный сервис
// с собственным бесплатным лимитом (~25GB).
//
// Используем unsigned upload preset (Settings → Upload → Upload presets в Cloudinary) —
// это специально придуманный Cloudinary способ загружать файлы с клиента (телефона)
// без API secret на устройстве: пресет сам ограничивает, что разрешено загружать,
// а настоящий ключ/secret остаётся только в дашборде Cloudinary. Не нужны отдельные
// политики доступа на запись, как у некоторых других сервисов — сам пресет их заменяет.
//
// Ключ и облачное имя — из BuildConfig (см. local.properties → CLOUDINARY_CLOUD_NAME /
// CLOUDINARY_UPLOAD_PRESET). Без них isConfigured == false, и вызывающий код
// (FirebaseRepo/ChatViewModel) сам откатывается на старый способ Base64-в-Firestore —
// ничего не ломается, если ключи ещё не вписаны.
object CloudinaryRepository {
    private const val TAG = "CloudinaryRepository"
    private val cloudName get() = BuildConfig.CLOUDINARY_CLOUD_NAME
    private val uploadPreset get() = BuildConfig.CLOUDINARY_UPLOAD_PRESET

    val isConfigured: Boolean get() = cloudName.isNotBlank() && uploadPreset.isNotBlank()

    // Публичный геттер для экрана настроек (см. SettingsScreen → "Хранилище медиа") — сам
    // cloudName выше private, чтобы не трогать остальной файл, но показать пользователю,
    // к какому облаку он подключён, всё же нужно.
    val cloudNameForDisplay: String get() = cloudName

    suspend fun uploadImage(bytes: ByteArray, fileName: String = "image.jpg"): Result<String> =
        upload("image", bytes, fileName, "image/jpeg")

    // Cloudinary трактует "video" resource_type широко — туда же входят и аудио-файлы
    // без видеодорожки (m4a голосовых), отдельного audio/upload у Cloudinary нет.
    suspend fun uploadVideo(bytes: ByteArray, fileName: String = "video.mp4"): Result<String> =
        upload("video", bytes, fileName, "video/mp4")

    suspend fun uploadAudio(bytes: ByteArray, fileName: String = "audio.m4a"): Result<String> =
        upload("video", bytes, fileName, "audio/mp4")

    // Произвольные файлы (документы и т.п.) — raw/upload, без перекодирования.
    suspend fun uploadFile(bytes: ByteArray, fileName: String): Result<String> =
        upload("raw", bytes, fileName, "application/octet-stream")

    // Кадр-обложка для видео: Cloudinary умеет отдать любой кадр ролика как картинку, если
    // в ссылку добавить трансформацию so_0 (second offset 0) и сменить расширение на .jpg.
    // Нужно, чтобы видео в чате не было чёрным прямоугольником до нажатия play: обложка
    // грузится мгновенно, а заодно из неё сразу известны пропорции кадра — так пузырь
    // принимает правильную форму без чёрных полей ("подбородка") ещё до старта.
    // Если ссылка не Cloudinary-шная (старое сообщение, Base64) — вернётся пустая строка,
    // и UI просто покажет как раньше.
    fun videoPosterUrl(videoUrl: String): String {
        if (videoUrl.isBlank() || !videoUrl.contains("/video/upload/")) return ""
        val withTransform = videoUrl.replace("/video/upload/", "/video/upload/so_0/")
        val dot = withTransform.lastIndexOf('.')
        val slash = withTransform.lastIndexOf('/')
        return if (dot > slash) withTransform.substring(0, dot) + ".jpg" else "$withTransform.jpg"
    }

    // q_auto,f_auto — стандартная Cloudinary-трансформация "на лету": сама подбирает
    // формат/битрейт под клиента. Отдаёт не исходный загруженный файл, а копию, прогнанную
    // через их пайплайн доставки — на практике это и есть то, что обычно чинит долгий
    // старт воспроизведения по ссылке (то есть если исходный файл был не готов к
    // прогрессивной загрузке, пересобранная версия обычно уже готова).
    fun streamingVideoUrl(videoUrl: String): String {
        if (videoUrl.isBlank() || !videoUrl.contains("/video/upload/")) return videoUrl
        if (videoUrl.contains("/video/upload/q_auto")) return videoUrl
        return videoUrl.replace("/video/upload/", "/video/upload/q_auto,f_auto/")
    }

    private suspend fun upload(resourceType: String, bytes: ByteArray, fileName: String, contentType: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext Result.failure(
                IllegalStateException("CLOUDINARY_CLOUD_NAME/CLOUDINARY_UPLOAD_PRESET не заданы в local.properties")
            )
            val boundary = "----PingGram" + UUID.randomUUID().toString().replace("-", "")
            var conn: HttpURLConnection? = null
            try {
                val url = URL("https://api.cloudinary.com/v1_1/$cloudName/$resourceType/upload")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 20_000
                    readTimeout = 60_000  // видео крупнее фото — даём больше времени на аплоад
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }
                val body = buildMultipartBody(boundary, uploadPreset, fileName, bytes, contentType)
                conn.outputStream.use { it.write(body) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val respBody = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) {
                    Log.w(TAG, "Cloudinary HTTP $code: ${respBody.take(300)}")
                    return@withContext Result.failure(Exception("Cloudinary HTTP $code"))
                }
                val json = JSONObject(respBody)
                val secureUrl = json.optString("secure_url").ifBlank { json.optString("url") }
                if (secureUrl.isBlank()) return@withContext Result.failure(Exception("Cloudinary: пустой ответ без ссылки"))
                Result.success(secureUrl)
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка загрузки в Cloudinary: ${e.message}")
                Result.failure(e)
            } finally {
                conn?.disconnect()
            }
        }

    // Собираем multipart/form-data вручную (как и с Klipy в GifRepository, без Retrofit/OkHttp) —
    // два текстовых поля (upload_preset, обязателен для unsigned-загрузки) и один файл.
    private fun buildMultipartBody(boundary: String, preset: String, fileName: String, fileBytes: ByteArray, contentType: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun text(s: String) = out.write(s.toByteArray(Charsets.UTF_8))

        text("--$boundary\r\n")
        text("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n")
        text("$preset\r\n")

        text("--$boundary\r\n")
        text("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
        text("Content-Type: $contentType\r\n\r\n")
        out.write(fileBytes)
        text("\r\n--$boundary--\r\n")

        return out.toByteArray()
    }
}
