package com.rosechat.data.repository

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rosechat.MainActivity
import com.rosechat.R

// Все системные уведомления в одном месте (FcmService только решает, что показать).
//
// Каналы имеют суффикс _v2 намеренно: после первого создания Android не даёт менять
// звук/вибрацию/важность канала из кода — старые каналы (без рингтона и нормальной
// вибрации) остались бы у тех, кто уже ставил приложение. Новый id = новые настройки.
object NotificationHelper {
    const val MSG_CHANNEL = "pinggram_messages_v2"
    const val CALL_CHANNEL = "pinggram_calls_v2"
    const val BG_CHANNEL = "pinggram_background"
    const val ACTION_DECLINE = "com.rosechat.action.DECLINE_CALL"
    const val EXTRA_CALL_ID = "callId"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.deleteNotificationChannel("pinggram_messages")
        manager.deleteNotificationChannel("pinggram_calls")

        val msgAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val messages = NotificationChannel(MSG_CHANNEL, "Сообщения", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 120, 250)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), msgAttrs)
        }

        val callAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val calls = NotificationChannel(CALL_CHANNEL, "Звонки", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 600, 800, 600)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), callAttrs)
        }
        // Тихий канал под постоянное уведомление фонового сервиса (без звука, свёрнутое).
        val background = NotificationChannel(BG_CHANNEL, "Фоновая работа", NotificationManager.IMPORTANCE_MIN).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        manager.createNotificationChannels(listOf(messages, calls, background))
    }

    fun showMessage(ctx: Context, chatId: String, title: String, text: String, unreadCount: Int) {
        ensureChannels(ctx)
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openChatId", chatId)
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, chatId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, MSG_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pinggram)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)   // для Android < 8, где каналов нет
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pendingIntent)
            // Число на иконке приложения: его показывают Samsung One UI и часть других
            // лаунчеров. Чистый Android/Pixel рисует точку, пока в шторке есть
            // непрочитанное уведомление — поэтому ниже уведомление снимается при
            // открытии чата (cancelMessage).
            .setNumber(unreadCount)
            .build()

        manager.notify(chatId.hashCode(), notification)
    }

    fun cancelMessage(ctx: Context, chatId: String) {
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(chatId.hashCode())
    }

    // Входящий звонок: рингтон + вибрация из канала, повтор до ответа (FLAG_INSISTENT),
    // full-screen intent (на заблокированном экране открывается экран звонка сразу),
    // кнопки "Ответить"/"Отклонить". Само уведомление гаснет через 40 секунд.
    fun showCall(ctx: Context, callId: String, callerName: String, isVideo: Boolean) {
        ensureChannels(ctx)
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openCallId", callId)
            putExtra("callerName", callerName)
            putExtra("isVideo", isVideo)
        }
        val openPi = PendingIntent.getActivity(
            ctx, callId.hashCode(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val decline = Intent(ctx, CallActionReceiver::class.java).apply {
            action = ACTION_DECLINE
            putExtra(EXTRA_CALL_ID, callId)
        }
        val declinePi = PendingIntent.getBroadcast(
            ctx, callId.hashCode() + 1, decline,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, CALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pinggram)
            .setContentTitle(if (isVideo) "Входящий видеозвонок" else "Входящий звонок")
            .setContentText(callerName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(true)
            .setTimeoutAfter(40_000L)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .addAction(0, "Отклонить", declinePi)
            .addAction(0, "Ответить", openPi)
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        manager.notify(callId.hashCode(), notification)
    }

    // Постоянное уведомление сервиса, который держит соединение с Firestore и показывает
    // сообщения/звонки без Google Play Services и без Cloud Functions. Android требует его
    // для любого фонового сервиса; на Android 14+ его можно смахнуть, сервис продолжит работу.
    fun backgroundNotification(ctx: Context): Notification {
        ensureChannels(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, BG_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pinggram)
            .setContentTitle("PingGram")
            .setContentText("Получение сообщений и звонков")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(open)
            .build()
    }

    fun cancelCall(ctx: Context, callId: String) {
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(callId.hashCode())
    }
}
