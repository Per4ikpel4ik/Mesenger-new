package com.rosechat.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rosechat.data.model.User
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.ui.common.SmartImage
import com.rosechat.ui.common.formatLastSeen
import com.rosechat.ui.common.PremiumFrameGlow
import com.rosechat.ui.common.premiumBadgeEmoji
import com.rosechat.ui.common.premiumFrameColor
import com.rosechat.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(uid: String, onBack: () -> Unit, onOpenChat: (String, String) -> Unit) {
    var user by remember { mutableStateOf<User?>(null) }
    var loading by remember { mutableStateOf(true) }
    var starting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uid) {
        FirebaseRepo.observeUser(uid).collect { user = it; loading = false }
    }

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            TopAppBar(
                title = { Text("Профиль", color = TextPrimary) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgSecondary)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator(color = Accent)
                user == null -> Text("Пользователь не найден", color = TextSecondary)
                else -> {
                    val u = user!!
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(12.dp))
                        Box(contentAlignment = Alignment.Center) {
                        if (u.isPremium) PremiumFrameGlow(u.premiumFrame, avatarSize = 104.dp)
                        Box(
                            Modifier.size(104.dp).clip(CircleShape)
                                .then(if (u.isPremium) Modifier.border(3.dp, premiumFrameColor(u.premiumFrame), CircleShape) else Modifier)
                                .padding(if (u.isPremium) 4.dp else 0.dp)
                                .clip(CircleShape).background(AccentMuted),
                            Alignment.Center
                        ) {
                            if (u.photoUrl.isNotEmpty() || u.photoData.isNotEmpty())
                                SmartImage(u.photoUrl, u.photoData, Modifier.fillMaxSize(), ContentScale.Crop)
                            else Text(u.displayName.firstOrNull()?.toString() ?: "?", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(u.displayName.ifEmpty { "Без имени" }, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                            if (u.isPremium && u.showPremiumBadge) { Spacer(Modifier.width(6.dp)); Text(premiumBadgeEmoji(u.premiumBadgeIcon), fontSize = 20.sp) }
                            if (u.profileSticker.isNotEmpty()) { Spacer(Modifier.width(6.dp)); Text(u.profileSticker, fontSize = 20.sp) }
                        }
                        if (u.username.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text("@${u.username}", style = MaterialTheme.typography.bodyMedium, color = AccentLight)
                        }
                        if (u.isPremium) {
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = Gold.copy(0.2f)) {
                                Text("${premiumBadgeEmoji(u.premiumBadgeIcon)} Premium", style = MaterialTheme.typography.labelMedium, color = Gold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (u.isOnline) "в сети" else formatLastSeen(u.lastSeen),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (u.isOnline) Accent else TextSecondary
                        )
                        if (u.status.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(u.status, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        }

                        Spacer(Modifier.height(28.dp))

                        Button(
                            onClick = {
                                val myUid = FirebaseRepo.currentUid ?: return@Button
                                starting = true
                                scope.launch {
                                    val chatId = FirebaseRepo.getOrCreatePrivateChat(myUid, uid)
                                    starting = false
                                    onOpenChat(chatId, u.displayName)
                                }
                            },
                            enabled = !starting,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
                        ) {
                            if (starting) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else { Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Написать сообщение") }
                        }
                    }
                }
            }
        }
    }
}
