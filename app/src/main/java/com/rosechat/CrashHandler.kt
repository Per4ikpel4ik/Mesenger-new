package com.rosechat

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ловит любой необработанный краш процесса и сохраняет его текст в SharedPreferences —
 * чтобы посмотреть причину падения можно было прямо в приложении (баннер на Home),
 * без USB/Android Studio/Logcat. На настоящий нативный краш (сегфолт где-то в недрах
 * WebRTC/JNI) это не сработает — такой краш убивает процесс раньше, чем успевает дойти
 * до Java-обработчика, — но для обычных необработанных исключений покажет ровно то,
 * что нужно.
 */
object CrashHandler {
    private const val PREFS = "crash_log_prefs"
    private const val KEY_LOG = "last_crash"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val text = "[$stamp] поток: ${thread.name}\n$sw"
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_LOG, text).apply()
            } catch (_: Throwable) {
                // Если даже запись лога упала — не мешаем штатной обработке краша ниже.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun getSavedCrash(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LOG, null)

    fun clearSavedCrash(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LOG).apply()
    }
}
