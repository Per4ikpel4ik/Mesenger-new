package com.rosechat

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.rosechat.data.local.ChatSettingsState
import com.rosechat.data.local.InAppNotifier
import com.rosechat.data.local.NotificationsState
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.data.repository.NotificationService
import com.rosechat.navigation.NavGraph
import com.rosechat.ui.theme.AppTheme
import com.rosechat.ui.theme.ThemeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemeState.init(this)
        NotificationsState.init(this)
        ChatSettingsState.init(this)

        // Отслеживаем именно сворачивание/разворачивание ВСЕГО приложения (а не отдельного
        // экрана) — так статус "в сети" корректно снимается, когда человек ушёл в другое
        // приложение или на рабочий стол, а не только когда он выходит из аккаунта.
        if (!onlineObserverRegistered) {
            onlineObserverRegistered = true
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    InAppNotifier.markForeground(true)
                    FirebaseRepo.currentUid?.let { FirebaseRepo.setOnline(it, true) }
                    // Токен для пушей — при каждом выходе на передний план (см. FirebaseRepo).
                    FirebaseRepo.registerPushToken()
                }
                override fun onStop(owner: LifecycleOwner)  {
                    InAppNotifier.markForeground(false)
                    FirebaseRepo.currentUid?.let { FirebaseRepo.setOnline(it, false) }
                }
            })
        }

        // Android 13+ требует явного runtime-разрешения на показ уведомлений (пуш-сообщений).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val launcher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Фоновый сервис уведомлений (см. NotificationService) — работает и без Blaze/FCM.
        NotificationService.start(this)

        // Если приложение открыто тапом по пуш-уведомлению — сразу переходим в нужный чат
        // или на экран входящего звонка (см. FcmService — оба типа пушей кладут разные extra).
        val deepLinkChatId = intent?.getStringExtra("openChatId")
        val deepLinkCallId = intent?.getStringExtra("openCallId")
        val deepLinkCallerName = intent?.getStringExtra("callerName")
        val deepLinkIsVideo = intent?.getBooleanExtra("isVideo", false) ?: false

        // Открыто тапом по уведомлению о звонке при заблокированном экране — показываем
        // экран звонка поверх блокировки. Только для звонка: обычный запуск приложения
        // (и чаты) поверх блокировки показываться не должны.
        if (!deepLinkCallId.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        setContent {
            AppTheme {
                NavGraph(
                    deepLinkChatId = deepLinkChatId,
                    deepLinkCallId = deepLinkCallId,
                    deepLinkCallerName = deepLinkCallerName,
                    deepLinkIsVideo = deepLinkIsVideo
                )
            }
        }
    }

    companion object {
        // MainActivity.onCreate может вызваться повторно (поворот экрана и т.п.) —
        // observer должен быть добавлен один раз на весь процесс, а не на каждый onCreate.
        private var onlineObserverRegistered = false
    }
}
