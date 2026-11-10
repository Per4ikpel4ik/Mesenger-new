package com.rosechat.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.ui.home.HomeScreen
import kotlinx.coroutines.flow.catch
import com.rosechat.ui.settings.SettingsScreen
import com.rosechat.ui.theme.*

// Нижнее меню с двумя вкладками: "Чаты" (существующий список чатов + поиск + FAB)
// и "Настройки" (профиль, тема, фон чата, уведомления, безопасность — всё в одном
// месте, без дублирования). Каждая вкладка сохраняет свой собственный Scaffold
// с topBar — вложенные Scaffold'ы в Compose не конфликтуют между собой, внешний
// просто резервирует место под нижнюю панель.
//
// Панель — плавающая полупрозрачная капсула ("жидкое стекло" в стиле iOS): рамка не
// на весь экран, а с отступами, скруглённая, полупрозрачная, с лёгкой светлой обводкой
// для блика стекла. Настоящий backdrop-blur контента под панелью в Compose требует
// сторонней библиотеки — здесь имитация через полупрозрачность и подсветку края,
// без риска утащить лишнюю зависимость.
@Composable
fun MainScreen(
    onOpenChat: (String, String) -> Unit,
    onOpenCalls: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenChatSettings: () -> Unit,
    onOpenStoryViewer: (Int) -> Unit,
    onSignOut: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }

    // Главный экран открыт => пользователь точно вошёл: регистрируем push-токен (если
    // профиль только что создан, при запуске MainActivity документа ещё не было).
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        FirebaseRepo.registerPushToken()
        // После входа сервис мог ещё не стартовать (MainActivity запускала его до логина).
        com.rosechat.data.repository.NotificationService.start(appContext)
    }

    // Общий счётчик непрочитанных для значка на вкладке "Чаты". Считается отдельно от
    // HomeScreen, потому что тот не живёт, пока открыта вкладка "Настройки". Архивные
    // чаты в сумму не входят — как и в Telegram, они "тихие".
    val myUid = FirebaseRepo.currentUid
    val totalUnread by produceState(0, myUid) {
        if (myUid == null) return@produceState
        FirebaseRepo.getChats(myUid).catch { }.collect { chats ->
            value = chats.filter { myUid !in it.archivedBy }.sumOf { it.unreadCount[myUid] ?: 0 }
        }
    }

    Scaffold(
        containerColor = BgPrimary,
        bottomBar = {
            // Раньше shadowElevation = 10.dp давал заметный прямоугольный ореol-тень вокруг
            // капсулы — на светлой теме особенно бросается в глаза как "второй белый
            // прямоугольник" позади вкладок (см. скриншоты: "такой прямоугольник белый
            // видно"). Убрали тень совсем — капсула и так читается за счёт полупрозрачной
            // заливки и тонкой светлой обводки, без лишнего боксa вокруг.
            Box(Modifier.fillMaxWidth().background(BgPrimary).padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = BgSecondary.copy(alpha = 0.78f),
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        GlassTabItem(Icons.Default.ChatBubble, "Чаты", tab == 0, badge = totalUnread) { tab = 0 }
                        GlassTabItem(Icons.Default.Settings, "Настройки", tab == 1) { tab = 1 }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                0 -> HomeScreen(
                    onOpenChat      = onOpenChat,
                    onOpenCalls     = onOpenCalls,
                    onOpenDeveloper = onOpenDeveloper,
                    onOpenArchive   = onOpenArchive,
                    onOpenStoryViewer = onOpenStoryViewer
                )
                1 -> SettingsScreen(onSignOut = onSignOut, onOpenPremium = onOpenPremium, onOpenChatSettings = onOpenChatSettings, onOpenDeveloper = onOpenDeveloper)
            }
        }
    }
}

@Composable
private fun GlassTabItem(icon: ImageVector, label: String, selected: Boolean, badge: Int = 0, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)
            .background(if (selected) Accent.copy(alpha = 0.2f) else Color.Transparent)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BadgedBox(badge = {
            if (badge > 0) Badge { Text(if (badge > 99) "99+" else badge.toString()) }
        }) {
            Icon(icon, contentDescription = label, tint = if (selected) Accent else TextSecondary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(label, color = if (selected) Accent else TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}
