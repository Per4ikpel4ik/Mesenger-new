package com.rosechat.ui.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rosechat.data.model.User
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DeveloperViewModel : ViewModel() {
    private val _unlocked = MutableStateFlow(false)
    val unlocked = _unlocked.asStateFlow()
    private val _pin      = MutableStateFlow("")
    val pin = _pin.asStateFlow()
    private val _pinError = MutableStateFlow(false)
    val pinError = _pinError.asStateFlow()
    private val _users    = MutableStateFlow<List<User>>(emptyList())
    val users = _users.asStateFlow()

    private val PIN = "19051917"

    fun onPin(v: String) {
        _pin.value = v.filter { it.isDigit() }.take(8)
        _pinError.value = false
    }

    fun tryUnlock() {
        if (_pin.value == PIN) {
            _unlocked.value = true
            load()
        } else {
            _pinError.value = true
            _pin.value = ""
        }
    }

    private fun load() {
        viewModelScope.launch {
            _users.value = FirebaseRepo.getAllUsers()
        }
    }

    fun toggleDev(uid: String, cur: Boolean) {
        viewModelScope.launch {
            FirebaseRepo.updateUser(uid, mapOf("isDeveloper" to !cur))
            load()
        }
    }

    // То, ради чего в основном и нужен секретный вход в эту панель (см. CreditsFooter) —
    // выдать/забрать Premium вручную, без похода в консоль Firebase. В отличие от кода
    // "gg" (см. FirebaseRepo.activatePremium) — этот Premium бессрочный, поэтому явно
    // стираем premiumUntil, если он остался от временной активации через "gg".
    fun togglePremium(uid: String, cur: Boolean) {
        viewModelScope.launch {
            FirebaseRepo.updateUser(uid, mapOf(
                "isPremium" to !cur,
                "premiumUntil" to com.google.firebase.firestore.FieldValue.delete()
            ))
            load()
        }
    }

    fun deleteUser(uid: String) {
        viewModelScope.launch {
            FirebaseRepo.db.collection("users").document(uid).delete().await()
            load()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(onBack: () -> Unit, vm: DeveloperViewModel = viewModel()) {
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val users    by vm.users.collectAsStateWithLifecycle()
    val pin      by vm.pin.collectAsStateWithLifecycle()
    val pinError by vm.pinError.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, null, tint = Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Dev Panel", color = TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgSecondary)
            )
        }
    ) { padding ->
        if (!unlocked) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Lock, null, tint = Accent, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(24.dp))
                Text("Панель разработчика", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Spacer(Modifier.height(32.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { vm.onPin(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PIN-код", color = TextSecondary) },
                    isError = pinError,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor    = TextPrimary,
                        unfocusedTextColor  = TextPrimary,
                        focusedBorderColor  = Accent,
                        unfocusedBorderColor = BgTertiary,
                        errorBorderColor    = ErrorRed,
                        cursorColor         = Accent,
                        focusedContainerColor   = BgSecondary,
                        unfocusedContainerColor = BgSecondary,
                        errorContainerColor     = BgSecondary
                    )
                )

                if (pinError) {
                    Spacer(Modifier.height(4.dp))
                    Text("Неверный PIN", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { vm.tryUnlock() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = pin.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor   = Color.White,
                        disabledContainerColor = AccentMuted.copy(0.4f),
                        disabledContentColor   = Color.White.copy(0.4f)
                    )
                ) {
                    Text("Войти", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("👥", "Пользователи", users.size.toString(), Modifier.weight(1f))
                        StatCard("🟢", "Онлайн", users.count { it.isOnline }.toString(), Modifier.weight(1f))
                    }
                }
                item {
                    Text(
                        "Пользователи",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(users) { user ->
                    UserCard(
                        user = user,
                        onToggleDev = { vm.toggleDev(user.uid, user.isDeveloper) },
                        onTogglePremium = { vm.togglePremium(user.uid, user.isPremium) },
                        onDelete    = { vm.deleteUser(user.uid) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: User, onToggleDev: () -> Unit, onTogglePremium: () -> Unit, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BgSecondary)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (user.isDeveloper) Accent else AccentMuted),
                Alignment.Center
            ) {
                Text(
                    user.displayName.firstOrNull()?.toString() ?: "?",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.displayName.ifEmpty { "Без имени" },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    if (user.isPremium) {
                        Spacer(Modifier.width(4.dp))
                        Text(com.rosechat.ui.common.premiumBadgeEmoji(user.premiumBadgeIcon), fontSize = 12.sp)
                    }
                    if (user.isDeveloper) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = Accent) {
                            Text(
                                "DEV",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    if (user.username.isNotEmpty()) "@${user.username}"
                    else user.phone.ifEmpty { user.email },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                if (user.isOnline) {
                    Text("● онлайн", style = MaterialTheme.typography.bodySmall, color = Online)
                }
                // Диагностика пушей/бейджа "на глаз" — без токена в Firestore FCM физически
                // некуда слать уведомление этому пользователю, значит дело не в Cloud
                // Function и не в бейдже на иконке, а в том, что регистрация токена на
                // устройстве не прошла (разрешение на уведомления, Google Play Services
                // и т.п.) — сюда и стоит смотреть в первую очередь.
                Text(
                    if (user.fcmToken.isNotBlank()) "🔔 токен есть" else "🔕 нет FCM-токена",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (user.fcmToken.isNotBlank()) TextTertiary else ErrorRed
                )
            }
            IconButton(onClick = onTogglePremium) {
                Icon(
                    if (user.isPremium) Icons.Default.WorkspacePremium else Icons.Outlined.WorkspacePremium,
                    contentDescription = if (user.isPremium) "Забрать Premium" else "Выдать Premium",
                    tint = if (user.isPremium) Gold else Accent
                )
            }
            IconButton(onClick = onToggleDev) {
                Icon(
                    if (user.isDeveloper) Icons.Default.Star else Icons.Default.StarBorder,
                    null,
                    tint = if (user.isDeveloper) TextSecondary else Accent
                )
            }
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Default.DeleteForever, null, tint = ErrorRed)
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor   = BgSecondary,
            title   = { Text("Удалить пользователя?", color = TextPrimary) },
            text    = { Text("Нельзя отменить.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onDelete() }) {
                    Text("Удалить", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Отмена", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun StatCard(emoji: String, label: String, value: String, modifier: Modifier) {
    Card(
        modifier,
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgSecondary)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(emoji, fontSize = 28.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
