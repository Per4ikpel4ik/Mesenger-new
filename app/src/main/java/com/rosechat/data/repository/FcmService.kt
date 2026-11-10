package com.rosechat.data.repository

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rosechat.data.local.InAppBanner
import com.rosechat.data.local.InAppNotifier
import com.rosechat.data.local.NotificationsState

class FcmService : FirebaseMessagingService() {

    // Токен создаётся/меняется системой, часто ещё ДО входа в аккаунт — тогда сохранить
    // его некуда. Это нормально: MainActivity и MainScreen повторно регистрируют токен
    // (FirebaseRepo.registerPushToken) при каждом запуске и сразу после входа.
    override fun onNewToken(token: String) {
        FirebaseRepo.saveFcmToken(token)
    }

    // Данные приходят из Cloud Functions sendMessageNotification / sendCallNotification
    // (см. /functions). Оба шлют data-сообщение, а не notification-сообщение — так пуш
    // всегда проходит через этот обработчик, даже когда приложение открыто. Различаем
    // по полю "type": звонок или обычное сообщение.
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data

        // Локальный сервис (NotificationService) уже следит за Firestore и сам показывает
        // и сообщения, и звонки. Если он жив — пуш дублировать не нужно. Если систему
        // убила процесс (агрессивная оболочка), isRunning сбросится, и пуш сработает как раньше.
        if (NotificationService.isRunning) return

        if (data["type"] == "call") {
            val callId = data["callId"] ?: return
            // Приложение на экране — входящий звонок и так откроется через Firestore-листенер
            // в NavGraph (с вибрацией на CallScreen). Дублировать шторкой не нужно.
            if (InAppNotifier.isForeground) return
            // Звонки не зависят от переключателя "Уведомления о сообщениях".
            NotificationHelper.showCall(
                this, callId,
                data["callerName"] ?: "PingGram",
                data["isVideo"] == "true"
            )
            return
        }

        // Пользователь выключил уведомления в Настройках — молча выходим. Сообщение всё
        // равно долетело и лежит в Firestore, просто без пуша.
        if (!NotificationsState.isEnabled(this)) return

        val chatId = data["chatId"] ?: return
        val chatName = data["chatName"] ?: "PingGram"
        val text = data["text"] ?: "Новое сообщение"
        val unreadCount = data["unreadCount"]?.toIntOrNull() ?: 0

        if (InAppNotifier.isForeground) {
            // Читает именно этот чат прямо сейчас — сообщение и так видно на экране.
            if (chatId == InAppNotifier.openChatId) return
            // Приложение открыто, но на другом экране: плашка внутри приложения вместо шторки.
            InAppNotifier.post(InAppBanner(chatId, chatName, text))
            InAppNotifier.feedback(this)
            return
        }

        NotificationHelper.showMessage(this, chatId, chatName, text, unreadCount)
    }
}
