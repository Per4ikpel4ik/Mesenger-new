package com.rosechat.shared.data

// Только для компиляции commonMain: :app на Android использует свой GifRepository.
actual fun decodeGifAnimation(bytes: ByteArray): GifAnimation? = null
actual fun gifApiKey(): String = ""
actual fun loadStickerResource(name: String): ByteArray? = null
actual fun localSetting(name: String): String = ""
actual fun loadWallpaperResource(name: String): ByteArray? = null
