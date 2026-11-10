package com.rosechat

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

// Нужен, чтобы GIF-сообщения анимировались, а не показывались статичной первой рамкой.
// На API 28+ Coil умеет сам через ImageDecoder, но т.к. minSdk = 26, для более старых
// устройств добавляем GifDecoder явно — иначе на них GIF будет замороженной картинкой.
class RoseChatApp : Application(), ImageLoaderFactory {
    companion object {
        // Нужен ChatWallpapers.kt, чтобы декодировать встроенные фото-пресеты обоев
        // (например, гору) из ресурсов — у CHAT_WALLPAPER_PRESETS нет своего Context,
        // а он всегда доступен уже к моменту первого обращения к пресетам (Compose
        // строится только после Application.onCreate()).
        lateinit var instance: RoseChatApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashHandler.install(this)
        com.rosechat.data.repository.NotificationHelper.ensureChannels(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .build()
    }
}
