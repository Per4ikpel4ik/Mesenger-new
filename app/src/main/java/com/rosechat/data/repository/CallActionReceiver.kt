package com.rosechat.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Кнопка "Отклонить" прямо в уведомлении о звонке — без открытия приложения.
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationHelper.ACTION_DECLINE) return
        val callId = intent.getStringExtra(NotificationHelper.EXTRA_CALL_ID) ?: return
        NotificationHelper.cancelCall(context, callId)

        // goAsync даёт ~10 секунд на асинхронную работу — с запасом ограничиваем 8 с,
        // чтобы без сети приёмник не завис до принудительного убийства.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000L) { FirebaseRepo.declineCall(callId) }
            } finally {
                pending.finish()
            }
        }
    }
}
