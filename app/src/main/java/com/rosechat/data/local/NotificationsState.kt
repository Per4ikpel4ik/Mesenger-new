package com.rosechat.data.local

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Локальный переключатель push-уведомлений. Хранится на устройстве (не в Firestore) —
// это чисто клиентская настройка "показывать/не показывать" системные уведомления,
// сервер продолжает слать сообщения как обычно.
object NotificationsState {
    private const val PREFS = "pinggram_prefs"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"

    var enabled by mutableStateOf(true)
        private set

    // Вызывается один раз в MainActivity.onCreate — как и ThemeState.init.
    fun init(context: Context) {
        enabled = isEnabled(context)
    }

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
    }

    // Отдельная функция для мест вне Compose-дерева (например, FcmService),
    // где нет гарантии, что init() уже отработал в текущем процессе.
    fun isEnabled(context: Context): Boolean {
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATIONS, true)
    }
}
