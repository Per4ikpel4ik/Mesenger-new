package com.rosechat.data.local

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class InAppBanner(
    val chatId: String,
    val title: String,
    val text: String,
    val stamp: Long = System.nanoTime()   // чтобы два одинаковых сообщения подряд тоже показывались
)

// Уведомления внутри самого приложения. Пока приложение на экране, системная шторка не
// нужна и даже мешает: вместо неё FcmService кладёт сюда баннер, а InAppBannerHost
// (см. ui/common) рисует его сверху поверх любого экрана, плюс короткая вибрация и звук.
// Если человек прямо сейчас читает этот самый чат — не показываем ничего.
object InAppNotifier {
    // Compose-state, чтобы экраны (например, карточка проверки уведомлений в Настройках)
    // перерисовывались, когда человек вернулся в приложение из системных настроек.
    var isForeground by mutableStateOf(false)
        private set

    // Какой чат открыт на экране прямо сейчас (null — никакой).
    @Volatile var openChatId: String? = null

    fun markForeground(value: Boolean) { isForeground = value }

    private val _banners = MutableSharedFlow<InAppBanner>(extraBufferCapacity = 8)
    val banners: SharedFlow<InAppBanner> = _banners.asSharedFlow()

    fun post(banner: InAppBanner) { _banners.tryEmit(banner) }

    // Короткая вибрация + системный звук уведомления. Звук сам подчиняется режиму
    // "без звука"/"не беспокоить", вибрация — короткая, не раздражающая.
    fun feedback(context: Context) {
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            runCatching {
                val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE))
                } else @Suppress("DEPRECATION") vibrator?.vibrate(90)
            }
            runCatching {
                RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
            }
        }
    }
}
