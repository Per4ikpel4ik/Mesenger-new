package com.rosechat.data.repository

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.rosechat.data.local.InAppBanner
import com.rosechat.data.local.InAppNotifier
import com.rosechat.data.local.NotificationsState
import com.rosechat.data.model.ChatType
import com.rosechat.data.model.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Уведомления, которым не нужны ни Cloud Functions (платный Blaze), ни Google Play
// Services. Сервис живёт как foreground и держит обычные Firestore-листенеры:
//   • список чатов — новое сообщение = lastMessageTime вырос и написал не я;
//   • входящие звонки — документ calls со статусом RINGING, адресованный мне.
// Дальше он сам решает, что показать: плашку внутри приложения (если оно на экране)
// или системное уведомление (рингтон + вибрация для звонка).
//
// Честные ограничения: это постоянное соединение (небольшой расход батареи и
// постоянное тихое уведомление), а некоторые оболочки (Xiaomi/Huawei/Oppo и т.п.)
// убивают фоновые сервисы, пока приложению не разрешён автозапуск и не сняты ограничения
// батареи — см. карточку "Работа в фоне" в Настройках.
class NotificationService : Service() {

    private var scope: CoroutineScope? = null
    private val userNames = HashMap<String, String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground нужно вызвать в первые секунды после startForegroundService —
        // поэтому до любых проверок.
        val n = NotificationHelper.backgroundNotification(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FG_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(FG_ID, n)
        }

        val uid = FirebaseRepo.currentUid
        if (uid == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (scope == null) {
            val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            scope = s
            isRunning = true
            s.launch { watchChats(uid) }
            s.launch { watchCalls(uid) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    // Слушатель может упасть (нет сети, временный отказ) — пробуем снова через 15 секунд.
    // baseline живёт вне цикла: сообщения, пришедшие пока не было связи, покажутся после
    // переподключения, а не потеряются.
    private suspend fun watchChats(uid: String) {
        val baseline = HashMap<String, Long>()
        var seeded = false
        while (scope?.isActive == true) {
            try {
                FirebaseRepo.getChats(uid).collect { chats ->
                    val total = chats.filter { uid !in it.archivedBy }.sumOf { it.unreadCount[uid] ?: 0 }
                    for (chat in chats) {
                        val time = chat.lastMessageTime?.toDate()?.time ?: continue
                        val prev = baseline[chat.id]
                        baseline[chat.id] = maxOf(time, prev ?: 0L)
                        if (!seeded) continue                       // первый снимок — только запоминаем
                        if (prev != null && time <= prev) continue  // ничего нового
                        if (chat.lastSenderId.isBlank() || chat.lastSenderId == uid) continue
                        if (uid in chat.archivedBy || chat.type == ChatType.SAVED) continue
                        notifyMessage(chat.id, chat.type, chat.name, chat.lastSenderId, chat.lastMessage, total)
                    }
                    seeded = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "chats listener failed: ${e.message}")
            }
            delay(15_000)
        }
    }

    private suspend fun notifyMessage(chatId: String, type: ChatType, chatName: String, senderId: String, lastMessage: String, total: Int) {
        if (!NotificationsState.isEnabled(this)) return
        val senderName = userName(senderId)
        val isGroupish = type == ChatType.GROUP || type == ChatType.CHANNEL
        val title = if (isGroupish && chatName.isNotBlank()) chatName else senderName.ifBlank { "PingGram" }
        val body = (if (isGroupish && senderName.isNotBlank()) "$senderName: " else "") +
            lastMessage.ifBlank { "Новое сообщение" }

        if (InAppNotifier.isForeground) {
            if (chatId == InAppNotifier.openChatId) return
            InAppNotifier.post(InAppBanner(chatId, title, body.take(200)))
            InAppNotifier.feedback(this)
        } else {
            NotificationHelper.showMessage(this, chatId, title, body.take(200), total)
        }
    }

    private suspend fun userName(uid: String): String {
        userNames[uid]?.let { return it }
        return try {
            val name = FirebaseRepo.db.collection("users").document(uid).get().await()
                .toObject(User::class.java)?.displayName.orEmpty()
            if (name.isNotBlank()) userNames[uid] = name
            name
        } catch (e: Exception) { "" }
    }

    private suspend fun watchCalls(uid: String) {
        var shownCallId: String? = null
        while (scope?.isActive == true) {
            try {
                FirebaseRepo.listenIncomingCalls(uid).collect { call ->
                    if (call != null) {
                        val age = System.currentTimeMillis() - (call.startedAt?.toDate()?.time ?: 0L)
                        // Документ звонка, брошенный в статусе RINGING давным-давно (у звонившего
                        // села батарея и т.п.), не должен звонить снова при каждом запуске сервиса.
                        if (call.id != shownCallId && age in 0..45_000L) {
                            shownCallId = call.id
                            if (!InAppNotifier.isForeground) {
                                NotificationHelper.showCall(this@NotificationService, call.id, call.callerName, call.type == com.rosechat.data.model.CallType.VIDEO)
                            }
                        }
                    } else {
                        shownCallId?.let { NotificationHelper.cancelCall(this@NotificationService, it) }
                        shownCallId = null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "calls listener failed: ${e.message}")
            }
            delay(15_000)
        }
    }

    companion object {
        private const val TAG = "NotificationService"
        private const val FG_ID = 7001

        @Volatile var isRunning = false
            private set

        fun start(context: Context) {
            if (FirebaseRepo.currentUid == null) return
            val app = context.applicationContext
            try {
                ContextCompat.startForegroundService(app, Intent(app, NotificationService::class.java))
            } catch (e: Exception) {
                // Android 12+ запрещает стартовать foreground-сервис из фона — ничего страшного,
                // он поднимется при следующем открытии приложения.
                Log.w(TAG, "start not allowed: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.stopService(Intent(app, NotificationService::class.java))
        }
    }
}
