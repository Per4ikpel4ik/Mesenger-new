@file:OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)

package com.rosechat.shared.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Покадровая анимация GIF на iPhone — тот же Skia-кодек, что и на десктопе.
private class SkiaGifAnimation(private val codec: Codec) : GifAnimation {
    override val frameCount: Int = codec.frameCount
    private val delays: IntArray = IntArray(codec.frameCount) { i -> codec.getFrameInfo(i).duration }

    override fun delayMs(index: Int): Int = delays[index].coerceIn(30, 2000)

    override fun frame(index: Int): ImageBitmap {
        val bmp = Bitmap()
        bmp.allocPixels(codec.imageInfo)
        codec.readPixels(bmp, index)
        return bmp.asComposeImageBitmap()
    }
}

actual fun decodeGifAnimation(bytes: ByteArray): GifAnimation? = try {
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    if (codec.frameCount > 1) SkiaGifAnimation(codec) else null
} catch (e: Throwable) { null }

// Ключ Klipy и другие настройки лежат в Info.plist приложения: project.yml подставляет
// их при сборке из GitHub Secrets (см. build-ios.yml). Пустая строка — ключ не задан.
actual fun localSetting(name: String): String {
    val v = (NSBundle.mainBundle.objectForInfoDictionaryKey(name) as? String)?.trim().orEmpty()
    return if (v.startsWith("$(")) "" else v
}

actual fun gifApiKey(): String = localSetting("GIF_API_KEY")

// Стикеры и обои лежат в iosApp/iosApp/stickers и iosApp/iosApp/wallpapers: XcodeGen кладёт
// их в ресурсы приложения (в корень бандла), поэтому ищем по имени файла без папки.
private fun bundleFileBytes(name: String, ext: String): ByteArray? {
    val bundle = NSBundle.mainBundle
    val path = bundle.pathForResource(name, ext)
        ?: bundle.pathForResource(name, ext, "stickers")
        ?: bundle.pathForResource(name, ext, "wallpapers")
        ?: return null
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val size = data.length.toInt()
    if (size == 0) return null
    val out = ByteArray(size)
    out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return out
}

private fun embedded(b64: String): ByteArray? = try {
    Base64.Default.decode(b64.filter { !it.isWhitespace() })
} catch (e: Throwable) { null }

// Сначала берём встроенные в код данные, потом (на всякий случай) файл из бандла приложения.
actual fun loadStickerResource(name: String): ByteArray? = when (name) {
    "sticker_seal" -> embedded(STICKER_SEAL_B64)
    "sticker_hippo1" -> embedded(STICKER_HIPPO1_B64)
    "sticker_hippo2" -> embedded(STICKER_HIPPO2_B64)
    else -> null
} ?: bundleFileBytes(name, "png")

actual fun loadWallpaperResource(name: String): ByteArray? = when (name) {
    "wallpaper_mountain" -> embedded(WALLPAPER_MOUNTAIN_B64)
    else -> null
} ?: bundleFileBytes(name, "jpg")
