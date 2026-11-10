package com.rosechat.shared.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLConnection
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

// Те же значения, что в app/local.properties → CLOUDINARY_CLOUD_NAME / CLOUDINARY_UPLOAD_PRESET.
// Это НЕ секрет: unsigned upload preset специально придуман Cloudinary для встраивания прямо
// в клиентский код (телефон, десктоп, браузер) — настоящий API key/secret остаётся только в
// дашборде Cloudinary и сюда не попадает. Если когда-нибудь сменишь preset/cloud name на
// Android — поменяй и здесь.
private const val CLOUDINARY_CLOUD_NAME = "guurqicg"
private const val CLOUDINARY_UPLOAD_PRESET = "Pinggram"

private val httpClient: HttpClient by lazy {
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
}

actual suspend fun pickFile(kind: MediaKind): PickedFile? = withContext(Dispatchers.Swing) {
    val chooser = JFileChooser().apply {
        dialogTitle = when (kind) {
            MediaKind.IMAGE -> "Выбрать фото"
            MediaKind.VIDEO -> "Выбрать видео"
            MediaKind.FILE -> "Выбрать файл"
        }
        fileFilter = when (kind) {
            MediaKind.IMAGE -> FileNameExtensionFilter("Изображения", "jpg", "jpeg", "png", "webp", "gif")
            MediaKind.VIDEO -> FileNameExtensionFilter("Видео", "mp4", "mov", "mkv", "webm", "avi")
            MediaKind.FILE -> null
        }
        isAcceptAllFileFilterUsed = kind == MediaKind.FILE
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return@withContext null
    val file = chooser.selectedFile ?: return@withContext null
    withContext(Dispatchers.IO) {
        val bytes = file.readBytes()
        val mime = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        PickedFile(name = file.name, bytes = bytes, mimeType = mime, size = bytes.size.toLong())
    }
}

actual suspend fun cloudinaryUpload(bytes: ByteArray, fileName: String, resourceType: String, mimeType: String): Result<String> =
    withContext(Dispatchers.IO) {
        try {
            val boundary = "----PingGramBoundary${UUID.randomUUID()}"
            val body = buildMultipartBody(boundary, bytes, fileName, mimeType)
            val request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/$resourceType/upload"))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                return@withContext Result.failure(Exception("Cloudinary вернул код ${response.statusCode()}: ${response.body().take(300)}"))
            }
            // Ответ Cloudinary — JSON, но тащить сюда отдельный парсер ради одного поля не
            // стоит: "secure_url" всегда простая строка без вложенных кавычек/спецсимволов.
            val match = Regex("\"secure_url\"\\s*:\\s*\"([^\"]+)\"").find(response.body())
            val url = match?.groupValues?.get(1)?.replace("\\/", "/")
            if (url.isNullOrBlank()) Result.failure(Exception("Не удалось разобрать ответ Cloudinary"))
            else Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

private fun buildMultipartBody(boundary: String, fileBytes: ByteArray, fileName: String, mimeType: String): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    fun writeText(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
    writeText("--$boundary\r\n")
    writeText("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n$CLOUDINARY_UPLOAD_PRESET\r\n")
    writeText("--$boundary\r\n")
    writeText("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
    writeText("Content-Type: $mimeType\r\n\r\n")
    out.write(fileBytes)
    writeText("\r\n--$boundary--\r\n")
    return out.toByteArray()
}

actual suspend fun downloadAndOpen(url: String, fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val request = HttpRequest.newBuilder().uri(java.net.URI.create(url)).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) {
            return@withContext Result.failure(Exception("Не удалось скачать (код ${response.statusCode()})"))
        }
        val downloads = File(System.getProperty("user.home"), "Downloads").apply { if (!exists()) mkdirs() }
        val safeName = fileName.ifBlank { "pinggram_file" }
        var target = File(downloads, safeName)
        // Не перезаписываем уже скачанный файл с тем же именем — добавляем суффикс, как
        // это делает большинство браузеров.
        var counter = 1
        val dot = safeName.lastIndexOf('.')
        val base = if (dot > 0) safeName.substring(0, dot) else safeName
        val ext = if (dot > 0) safeName.substring(dot) else ""
        while (target.exists()) {
            target = File(downloads, "$base ($counter)$ext")
            counter++
        }
        target.writeBytes(response.body())
        withContext(Dispatchers.Swing) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(target)
            }
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

actual suspend fun fetchBytes(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
    try {
        val request = HttpRequest.newBuilder().uri(java.net.URI.create(url)).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in 200..299) Result.failure(Exception("Код ${response.statusCode()}"))
        else Result.success(response.body())
    } catch (e: Exception) {
        Result.failure(e)
    }
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    loadImageBitmap(ByteArrayInputStream(bytes))
} catch (e: Exception) {
    null
}

actual val mediaStorageLabel: String = "Cloudinary · $CLOUDINARY_CLOUD_NAME"

actual fun voiceRecordingSupported(): Boolean = false
actual suspend fun startVoiceRecording(): Result<Unit> = Result.failure(UnsupportedOperationException("Запись голосовых на десктопе пока не поддерживается"))
actual suspend fun stopVoiceRecording(): PickedFile? = null
actual fun cancelVoiceRecording() {}

actual fun videoNoteSupported(): Boolean = false
actual suspend fun recordVideoNote(): PickedFile? = null
