package com.rosechat.shared.data

import androidx.compose.ui.graphics.ImageBitmap

// Платформенные мелочи для GIF и картиночных стикеров. Реализованы на desktopMain
// (GifPlatform.desktop.kt); androidMain/iosMain — заглушки, чтобы commonMain компилировался
// на всех целях (:app на Android использует свои GifRepository/ресурсы).

// Покадровая анимация (GIF / animated WebP). frame() декодирует кадр по запросу, чтобы не держать
// в памяти сразу все кадры.
interface GifAnimation {
    val frameCount: Int
    fun delayMs(index: Int): Int
    fun frame(index: Int): ImageBitmap
}

// null — это не анимация (обычная картинка из одного кадра) или не удалось разобрать.
expect fun decodeGifAnimation(bytes: ByteArray): GifAnimation?

// Ключ Klipy: переменная окружения GIF_API_KEY или строка GIF_API_KEY=... в local.properties
// (в корне проекта — тот же файл, что использует Android) либо в ~/.pinggram/local.properties.
// Пустая строка — ключ не найден (панель GIF покажет подсказку).
expect fun gifApiKey(): String

// PNG картиночного стикера из ресурсов приложения (stickers/<name>.png); null — не найден.
expect fun loadStickerResource(name: String): ByteArray?

// Локальная настройка, которую не кладём в исходники и не отправляем в чат (PIN админ-панели и т.п.):
// переменная окружения <name> или строка <name>=... в local.properties. Пустая строка — не задана.
expect fun localSetting(name: String): String

// Встроенное фото-обои (wallpapers/<name>.jpg в ресурсах приложения); null — не найдено.
expect fun loadWallpaperResource(name: String): ByteArray?
