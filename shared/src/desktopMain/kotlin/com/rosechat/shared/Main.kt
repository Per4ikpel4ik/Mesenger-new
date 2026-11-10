package com.rosechat.shared

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.rosechat.shared.data.SharedRepo
import com.rosechat.shared.data.ensureFirebaseInitialized
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

fun main() {
    ensureFirebaseInitialized()
    application {
        // Окно пошире: слева список чатов, справа переписка (как в Telegram Desktop).
        val windowState = rememberWindowState(size = DpSize(1180.dp, 760.dp))
        // Иконка окна и панели задач — тот же розово-чёрный логотип, что у Android-приложения
        // (раньше стояла заглушка Compose с буквами "JB"). Файл лежит в desktopMain/resources.
        val icon = BitmapPainter(useResource("pinggram_icon.png", ::loadImageBitmap))
        Window(
            onCloseRequest = {
                // Перед выходом помечаем себя «не в сети» (не дольше 2 с, чтобы окно не зависло).
                val uid = SharedRepo.currentUid
                if (uid != null) runBlocking { withTimeoutOrNull(2000) { SharedRepo.setOnlineStatus(uid, false) } }
                exitApplication()
            }, title = "PingGram",
            state = windowState,
            icon = icon,
        ) {
            App()
        }
    }
}
