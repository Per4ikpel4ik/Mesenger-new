package com.rosechat.shared.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import java.io.File
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

private class SkiaGifAnimation(private val codec: Codec) : GifAnimation {
    override val frameCount: Int = codec.frameCount
    private val delays: IntArray = IntArray(codec.frameCount) { i -> codec.getFrameInfo(i).duration }

    // Браузеры тоже не показывают кадры быстрее ~20–30 мс; слишком длинные паузы режем до 2 с.
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

private fun readSetting(name: String): String {
    System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val home = System.getProperty("user.home") ?: ""
    // При запуске через gradle (:shared:run) рабочая папка — shared/, поэтому корневой
    // local.properties лежит на уровень выше.
    val candidates = listOf(
        File("local.properties"), File("../local.properties"), File("../../local.properties"),
        File(home, ".pinggram/local.properties"), File(home, ".pinggram/gif_api_key.txt"),
    )
    for (f in candidates) {
        if (!f.isFile) continue
        val text = try { f.readText() } catch (e: Exception) { continue }
        if (f.name.endsWith(".txt")) {
            // Одиночный файл с ключом — только для GIF_API_KEY.
            val k = text.trim()
            if (name == "GIF_API_KEY" && k.isNotEmpty()) return k
        } else {
            for (line in text.lines()) {
                val t = line.trim()
                if (t.startsWith("$name=")) {
                    val k = t.removePrefix("$name=").trim()
                    if (k.isNotEmpty()) return k
                }
            }
        }
    }
    return ""
}

private val settingCache = java.util.concurrent.ConcurrentHashMap<String, String>()

actual fun localSetting(name: String): String = settingCache.getOrPut(name) { readSetting(name) }

actual fun gifApiKey(): String = localSetting("GIF_API_KEY")

private object StickerRes

actual fun loadStickerResource(name: String): ByteArray? = try {
    StickerRes::class.java.getResourceAsStream("/stickers/$name.png")?.use { it.readBytes() }
} catch (e: Exception) { null }

actual fun loadWallpaperResource(name: String): ByteArray? = try {
    StickerRes::class.java.getResourceAsStream("/wallpapers/$name.jpg")?.use { it.readBytes() }
} catch (e: Exception) { null }
