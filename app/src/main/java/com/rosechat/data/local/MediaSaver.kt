package com.rosechat.data.local

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Пока нет Firebase Storage, фото/файлы/видео едут как Base64 внутри документа чата и
// нигде не существуют отдельным файлом на диске — соответственно, посмотреть их вне
// пузырька чата или сохранить было нельзя. Тут — декодирование обратно в реальный файл
// и открытие через системный ACTION_VIEW (плюс сохранение в Загрузки/Галерею на API 29+,
// где для этого не нужно разрешение на запись — MediaStore.Downloads/Images/Video приняли
// на себя разрешения ещё в Android 10). На API 26-28 (без разрешения WRITE_EXTERNAL_STORAGE,
// которое сюда специально не добавляли) файл кладётся в собственную внешнюю папку
// приложения — не в системные Загрузки, но так же открывается через FileProvider.
object MediaSaver {
    private const val TAG = "MediaSaver"

    fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    // Декодирует base64 и сохраняет как fileName. kind решает, в какую системную папку
    // класть на API 29+ (Загрузки/Картинки/Видео через MediaStore) — само разрешение
    // коллекции откладывается до ветки API 29+ внутри, а не считается заранее, иначе
    // обращение к MediaStore.Downloads (класс появился только в API 29) само по себе
    // крашит более старые устройства ещё до проверки версии.
    private fun saveBytes(context: Context, bytes: ByteArray, fileName: String, mimeType: String, kind: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val (relDir, collection) = when (kind) {
                    "image" -> Environment.DIRECTORY_PICTURES to MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> Environment.DIRECTORY_MOVIES to MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else -> Environment.DIRECTORY_DOWNLOADS to MediaStore.Downloads.EXTERNAL_CONTENT_URI
                }
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relDir)
                }
                val uri = resolver.insert(collection, values) ?: return null
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
                uri
            } else {
                val dir = File(context.getExternalFilesDir(null), "PingGram")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                file.writeBytes(bytes)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveBytes failed", e)
            null
        }
    }

    // kind: "image" | "video" | "file" — решает, в какую системную коллекцию/папку класть.
    fun saveBase64(context: Context, base64: String, fileName: String, mimeType: String, kind: String): Uri? {
        val bytes = try { Base64.decode(base64, Base64.NO_WRAP) } catch (e: Exception) { Log.e(TAG, "decode failed", e); return null }
        return saveBytes(context, bytes, fileName, mimeType, kind)
    }

    fun openUri(context: Context, uri: Uri, mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openUri failed", e)
            Toast.makeText(context, "Нет приложения, чтобы открыть этот файл", Toast.LENGTH_SHORT).show()
        }
    }

    // Сохранить и сразу попробовать открыть — то, что нужно и для "скачать", и для
    // "посмотреть" одним тапом.
    fun saveAndOpen(context: Context, base64: String, fileName: String, mimeType: String, kind: String) {
        val uri = saveBase64(context, base64, fileName, mimeType, kind)
        if (uri == null) {
            Toast.makeText(context, "Не удалось сохранить", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
        openUri(context, uri, mimeType)
    }

    // То же самое, но для медиа, загруженного в Cloudinary — там сообщение хранит просто
    // ссылку (mediaUrl), а не Base64, так что сначала скачиваем файл, а дальше та же
    // логика сохранения/открытия, что и в saveAndOpen. Сетевой запрос — suspend,
    // вызывающий код (Composable) должен обернуть вызов в свой coroutine scope.
    suspend fun saveAndOpenFromUrl(context: Context, url: String, fileName: String, mimeType: String, kind: String) {
        val bytes = withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 30_000
                }
                conn.inputStream.use { it.readBytes() }
            } catch (e: Exception) {
                Log.e(TAG, "download failed", e)
                null
            } finally {
                conn?.disconnect()
            }
        }
        if (bytes == null) {
            Toast.makeText(context, "Не удалось скачать", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = saveBytes(context, bytes, fileName, mimeType, kind)
        if (uri == null) {
            Toast.makeText(context, "Не удалось сохранить", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
        openUri(context, uri, mimeType)
    }
}
