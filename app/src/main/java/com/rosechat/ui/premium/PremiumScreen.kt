package com.rosechat.ui.premium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rosechat.data.billing.BillingRepository
import com.rosechat.data.billing.findActivity
import com.rosechat.data.local.ChatSettingsState
import com.rosechat.data.model.User
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.ui.common.PREMIUM_BADGE_ICONS
import com.rosechat.ui.common.PREMIUM_BUBBLE_COLORS
import com.rosechat.ui.common.PREMIUM_FRAMES
import com.rosechat.ui.common.PremiumFrameGlow
import com.rosechat.ui.theme.*
import kotlinx.coroutines.launch

private data class PerkInfo(val icon: ImageVector, val title: String, val desc: String)

private val PERKS = listOf(
    PerkInfo(Icons.Default.Star, "Свой значок у имени", "⭐ 💎 или 👑 — выбери и покажи всем в чатах и профиле"),
    PerkInfo(Icons.Default.Mic, "Голосовые до 4 минут", "Обычным пользователям доступна 1 минута, у Premium — 4"),
    PerkInfo(Icons.Default.AutoAwesome, "Рамка аватара с эффектом", "9 цветов плюс неоновое свечение и вращающаяся радуга"),
    PerkInfo(Icons.Default.Palette, "Свой цвет пузырей чата", "Как выглядят твои сообщения — только для тебя"),
    PerkInfo(Icons.Default.VisibilityOff, "Просмотр удалённых сообщений", "Видишь текст сообщений собеседника, даже если он их удалил"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(onBack: () -> Unit) {
    var user by remember { mutableStateOf<User?>(null) }
    var price by remember { mutableStateOf<String?>(null) }
    var purchasing by remember { mutableStateOf(false) }
    var billingError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        FirebaseRepo.currentUid?.let { uid -> FirebaseRepo.observeUser(uid).collect { user = it } }
    }
    LaunchedEffect(Unit) {
        BillingRepository.refreshSubscriptionStatus(context) // подтягивает Premium, если подписка уже активна (переустановка/смена телефона)
        price = BillingRepository.fetchPrice(context)
    }
    val isPremium = user?.isPremium == true

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            TopAppBar(
                title = { Text("PingGram Premium", color = TextPrimary) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgSecondary)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(72.dp).clip(CircleShape).background(Gold.copy(0.18f)), Alignment.Center) {
                Text("⭐", fontSize = 34.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("PingGram Premium", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))

            if (isPremium) {
                Surface(shape = RoundedCornerShape(10.dp), color = Gold.copy(0.2f)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Gold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        val until = user?.premiumUntil
                        val label = if (until != null) {
                            val daysLeft = ((until.seconds * 1000 - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                            "Premium активен ещё $daysLeft дн."
                        } else "У тебя уже есть Premium"
                        Text(label, color = Gold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Text("Дополнительные возможности профиля", color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                // Кнопка оплаты показывается САМА, только когда Play Console реально настроен
                // (fetchPrice получил цену — значит подписка "pinggram_premium_monthly" создана
                // и опубликована). Пока это не сделано, price == null и кнопки просто нет —
                // никто не видит нерабочую покупку. Как только настроишь Play Console (см.
                // BillingRepository.kt), кнопка появится сама, без единой правки кода.
                if (price != null) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val activity = context.findActivity()
                            if (activity == null) { billingError = "Не удалось открыть окно оплаты"; return@Button }
                            purchasing = true; billingError = null
                            scope.launch {
                                BillingRepository.launchPurchase(activity)
                                purchasing = false
                                billingError = BillingRepository.lastError
                            }
                        },
                        enabled = !purchasing,
                        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        if (purchasing) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Text("Оформить Premium — $price/мес", fontWeight = FontWeight.Bold)
                        }
                    }
                    billingError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Подписка с автопродлением, отменить можно в любой момент в Google Play",
                        color = TextTertiary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(28.dp))

            PERKS.forEach { perk ->
                Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Gold.copy(0.15f)), Alignment.Center) {
                            Icon(perk.icon, null, tint = Gold, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(perk.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(perk.desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (isPremium) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Настройки Premium", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        Text("Цвет рамки аватара", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            PREMIUM_FRAMES.forEach { (id, color) ->
                                val selected = (user?.premiumFrame ?: "gold") == id
                                Box(contentAlignment = Alignment.Center) {
                                    if (id == "neon" || id == "rainbow") PremiumFrameGlow(id, avatarSize = 40.dp)
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape).background(color)
                                            .then(if (selected) Modifier.border(2.5.dp, TextPrimary, CircleShape) else Modifier)
                                            .clickable { FirebaseRepo.currentUid?.let { uid -> scope.launch { FirebaseRepo.setPremiumFrame(uid, id) } } },
                                        Alignment.Center
                                    ) { if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider(color = BgTertiary)
                        Spacer(Modifier.height(14.dp))
                        Text("Значок бейджа", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            PREMIUM_BADGE_ICONS.forEach { (id, emoji) ->
                                val selected = (user?.premiumBadgeIcon ?: "star") == id
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).background(if (selected) Gold.copy(0.25f) else BgTertiary)
                                        .then(if (selected) Modifier.border(2.dp, Gold, CircleShape) else Modifier)
                                        .clickable { FirebaseRepo.currentUid?.let { uid -> scope.launch { FirebaseRepo.setPremiumBadgeIcon(uid, id) } } },
                                    Alignment.Center
                                ) { Text(emoji, fontSize = 20.sp) }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider(color = BgTertiary)
                        Spacer(Modifier.height(14.dp))
                        Text("Цвет своих пузырей в чате", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Text("Виден только тебе, собеседник видит как обычно", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            PREMIUM_BUBBLE_COLORS.forEach { (id, color) ->
                                val selected = (user?.premiumBubbleColor ?: "default") == id
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(color ?: BubbleOut)
                                        .then(if (selected) Modifier.border(2.5.dp, TextPrimary, CircleShape) else Modifier)
                                        .clickable { FirebaseRepo.currentUid?.let { uid -> scope.launch { FirebaseRepo.setPremiumBubbleColor(uid, id) } } },
                                    Alignment.Center
                                ) { if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider(color = BgTertiary)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Показывать бейдж у имени", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Text("Виден остальным в чатах и профиле", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Switch(
                                checked = user?.showPremiumBadge ?: true,
                                onCheckedChange = { v -> FirebaseRepo.currentUid?.let { uid -> scope.launch { FirebaseRepo.setShowPremiumBadge(uid, v) } } },
                                colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Gold.copy(0.4f))
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider(color = BgTertiary)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Показывать удалённые сообщения", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Text("Видишь текст, даже если собеседник его удалил — заметно только тебе", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Switch(
                                checked = ChatSettingsState.showDeletedMessages,
                                onCheckedChange = { v -> ChatSettingsState.setShowDeletedMessages(context, v) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Gold, checkedTrackColor = Gold.copy(0.4f))
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
