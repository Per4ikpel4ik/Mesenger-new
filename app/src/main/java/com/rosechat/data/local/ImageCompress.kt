package com.rosechat.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Пока нет Firebase Storage (нужен Blaze-план), фото кодируем в Base64 и кладём прямо
// в документ Firestore. Firestore ограничивает документ 1 МБ — поэтому агрессивно сжимаем
// картинку перед кодированием (уменьшаем сторону + понижаем JPEG-качество, пока не впишемся
// в целевой размер). Когда подключится Storage — это можно заменить на putFile(...),
// задел под настоящий URL (mediaUrl/photoUrl/chatBackground) уже есть.
object ImageCompress {

    // context — для чтения файла через content resolver (Uri может указывать на галерею).
    // maxDim — максимальная сторона в пикселях после сжатия.
    // targetBytes — целевой размер итогового JPEG.
    // Возвращает сырые байты JPEG — используется для загрузки в Cloudinary (см.
    // CloudinaryRepository), где Base64 не нужен, файл грузится как есть.
    // Раньше это была обычная функция, вызываемая прямо из viewModelScope.launch{} (Main-поток
    // по умолчанию) — то есть ВЕСЬ декод/масштабирование/JPEG-сжатие битмапа выполнялись прямо
    // на UI-потоке. Для мелких аватарок это проскакивало незаметно, но именно этот же путь
    // используют фон чата и фото в чат покрупнее — на слабом телефоне или с тяжёлым фото это
    // подвешивало интерфейс на заметное время. Публичная suspend-обёртка переносит работу на
    // Dispatchers.IO; сама логика (со своими `return` внутри try) остаётся отдельной обычной
    // функцией — так `return` внутри нужен только внутри неё самой, а не внутри лямбды
    // withContext (там non-local return из не-inline функции всё равно не скомпилировался бы).
    suspend fun compressToBytes(context: Context, uri: Uri, maxDim: Int, targetBytes: Int): ByteArray? =
        withContext(Dispatchers.IO) { compressToBytesBlocking(context, uri, maxDim, targetBytes) }

    private fun compressToBytesBlocking(context: Context, uri: Uri, maxDim: Int, targetBytes: Int): ByteArray? {
        return try {
            val resolver = context.contentResolver

            // Сначала читаем только размеры — не грузим огромную картинку в память целиком.
            // ВАЖНО (тут был настоящий баг): BitmapFactory.decodeStream() в режиме
            // inJustDecodeBounds=true по документации Android ВСЕГДА возвращает null — битмап
            // не создаётся, только заполняется bounds.outWidth/outHeight. Строчка ниже раньше
            // была `...?.use { decodeStream(...) } ?: return null` — то есть читала ЭТОТ
            // всегда-null результат как "не удалось открыть файл" и выходила с null ещё до
            // самого декодирования. Из-за этого compressToBytes/compressToBase64 возвращали
            // null для АБСОЛЮТНО ЛЮБОГО фото, при любом файле, всегда — отсюда и не грузились
            // ни аватарки, ни истории, ни фото в чат: все они идут через эту одну функцию.
            // Теперь поток и результат декодирования проверяются раздельно: null-поток —
            // это реальная ошибка чтения файла, а успех бордер-декода проверяем по
            // заполненным bounds, а не по (ожидаемо null) возврату decodeStream.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(uri) ?: return null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

            // Финальный точный ресайз до maxDim по большей стороне
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            if (scale < 1f) {
                val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
                bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)
            }

            var quality = 85
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 12
            } while (bytes.size > targetBytes && quality > 20)

            if (bytes.size > targetBytes) null  // даже на минимальном качестве не влезло — картинка слишком сложная
            else bytes
        } catch (_: Exception) { null }
    }

    // Base64-обёртка над compressToBytes — используется, когда Cloudinary не настроен
    // и фото по-прежнему едет прямо в документе Firestore (см. FirebaseRepo/ChatViewModel).
    suspend fun compressToBase64(context: Context, uri: Uri, maxDim: Int, targetBytes: Int): String? =
        compressToBytes(context, uri, maxDim, targetBytes)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
}
