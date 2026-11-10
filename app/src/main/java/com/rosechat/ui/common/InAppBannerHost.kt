package com.rosechat.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosechat.data.local.InAppBanner
import com.rosechat.data.local.InAppNotifier
import com.rosechat.ui.theme.Accent
import com.rosechat.ui.theme.AccentMuted
import com.rosechat.ui.theme.BgSecondary
import com.rosechat.ui.theme.TextPrimary
import com.rosechat.ui.theme.TextSecondary
import kotlinx.coroutines.delay

// Плашка "новое сообщение" сверху экрана, пока приложение открыто. Тап — открыть чат.
// Кладётся один раз поверх NavHost (см. NavGraph), поэтому видна на любом экране.
@Composable
fun InAppBannerHost(onOpenChat: (String, String) -> Unit, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<InAppBanner?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        InAppNotifier.banners.collect { banner ->
            current = banner
            visible = true
        }
    }
    LaunchedEffect(current) {
        if (current != null) {
            delay(4_000)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        val banner = current
        if (banner != null) {
            Box(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        visible = false
                        onOpenChat(banner.chatId, banner.title)
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = BgSecondary,
                    shadowElevation = 8.dp
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(AccentMuted), Alignment.Center) {
                            Icon(Icons.Default.ChatBubble, null, tint = Accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                banner.title, color = TextPrimary, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                banner.text, color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
