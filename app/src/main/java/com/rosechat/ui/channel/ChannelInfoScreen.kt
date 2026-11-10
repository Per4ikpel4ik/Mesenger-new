package com.rosechat.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rosechat.data.model.Chat
import com.rosechat.data.model.ChatType
import com.rosechat.data.model.User
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChannelInfoViewModel(val chatId: String) : ViewModel() {
    val chat: StateFlow<Chat?> = FirebaseRepo.getChat(chatId).stateIn(viewModelScope, SharingStarted.Lazily, null)
    private val _members = MutableStateFlow<List<User>>(emptyList())
    val members = _members.asStateFlow()
    private val _addResults = MutableStateFlow<List<User>>(emptyList())
    val addResults = _addResults.asStateFlow()
    private val _addQuery = MutableStateFlow("")
    val addQuery = _addQuery.asStateFlow()

    private var lastMemberIds: List<String> = emptyList()

    init {
        viewModelScope.launch {
            chat.collect { c ->
                if (c != null && c.memberIds != lastMemberIds) {
                    lastMemberIds = c.memberIds
                    _members.value = c.memberIds.mapNotNull { FirebaseRepo.getUser(it) }
                }
            }
        }
    }

    fun onAddQueryChanged(q: String) {
        _addQuery.value = q
        if (q.length < 2) { _addResults.value = emptyList(); return }
        viewModelScope.launch {
            val myUid = FirebaseRepo.currentUid ?: return@launch
            val found = FirebaseRepo.searchByName(q, myUid)
            _addResults.value = found.filter { u -> chat.value?.memberIds?.contains(u.uid) != true }
        }
    }

    fun addMember(uid: String) {
        viewModelScope.launch {
            FirebaseRepo.addChannelMember(chatId, uid)
            _addQuery.value = ""; _addResults.value = emptyList()
        }
    }

    fun toggleAdmin(uid: String, isAdmin: Boolean) {
        viewModelScope.launch { FirebaseRepo.setChannelAdmin(chatId, uid, !isAdmin) }
    }

    fun togglePublic(nowPublic: Boolean) {
        viewModelScope.launch { FirebaseRepo.updateChannelInfo(chatId, mapOf("isPublic" to nowPublic)) }
    }

    fun toggleBroadcastOnly(broadcastOnly: Boolean) {
        viewModelScope.launch { FirebaseRepo.updateChannelInfo(chatId, mapOf("broadcastOnly" to broadcastOnly)) }
    }

    fun leave(onDone: () -> Unit) {
        val uid = FirebaseRepo.currentUid ?: return
        viewModelScope.launch { FirebaseRepo.leaveChannel(chatId, uid); onDone() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelInfoScreen(chatId: String, onBack: () -> Unit, onLeft: () -> Unit) {
    val vm: ChannelInfoViewModel = viewModel(key = "info_$chatId", factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(mc: Class<T>): T { @Suppress("UNCHECKED_CAST") return ChannelInfoViewModel(chatId) as T }
    })
    val chat       by vm.chat.collectAsStateWithLifecycle()
    val members    by vm.members.collectAsStateWithLifecycle()
    val addQuery   by vm.addQuery.collectAsStateWithLifecycle()
    val addResults by vm.addResults.collectAsStateWithLifecycle()
    val myUid = FirebaseRepo.currentUid
    val isAdmin = myUid != null && chat?.adminIds?.contains(myUid) == true
    val isChannel = chat?.type == ChatType.CHANNEL
    var showAddDialog by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }

    Scaffold(containerColor = BgPrimary,
        topBar = {
            TopAppBar(
                title = { Text(if (isChannel) "О канале" else "О группе", color = TextPrimary) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgSecondary)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(AccentMuted), Alignment.Center) {
                        Icon(if (isChannel) Icons.Default.Campaign else Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(chat?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    if (isChannel && chat?.isPublic == true && chat?.channelUsername?.isNotEmpty() == true) {
                        Text("@${chat?.channelUsername}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    if (chat?.description?.isNotEmpty() == true) {
                        Spacer(Modifier.height(6.dp))
                        Text(chat!!.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${members.size} ${if (isChannel) "подписчиков" else "участников"}",
                        style = MaterialTheme.typography.bodySmall, color = TextTertiary
                    )
                }
            }

            // ── Настройки канала (только для админа) ─────────────
            if (isChannel && isAdmin) {
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                        Column(Modifier.padding(4.dp)) {
                            SettingRow("Публичный канал", "Виден в глобальном поиске", chat?.isPublic == true) { vm.togglePublic(it) }
                            HorizontalDivider(color = Divider, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            SettingRow("Только админы пишут", "Как классический канал-рассылка", chat?.broadcastOnly == true) { vm.toggleBroadcastOnly(it) }
                        }
                    }
                }
            }

            // ── Участники ──────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isChannel) "Подписчики" else "Участники",
                        style = MaterialTheme.typography.titleMedium, color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    if (isAdmin) {
                        TextButton({ showAddDialog = true }) {
                            Icon(Icons.Default.PersonAdd, null, tint = Accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text("Добавить", color = Accent)
                        }
                    }
                }
            }

            items(members, key = { it.uid }) { user ->
                val userIsAdmin = chat?.adminIds?.contains(user.uid) == true
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(AccentMuted), Alignment.Center) {
                        Text(user.displayName.firstOrNull()?.toString() ?: "?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user.displayName, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            if (userIsAdmin) {
                                Spacer(Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(6.dp), color = Accent) {
                                    Text("админ", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                                }
                            }
                        }
                        if (user.username.isNotEmpty()) Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    if (isAdmin && user.uid != myUid) {
                        IconButton({ vm.toggleAdmin(user.uid, userIsAdmin) }) {
                            Icon(if (userIsAdmin) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (userIsAdmin) Accent else TextSecondary)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                OutlinedButton({ showLeaveConfirm = true }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed)) {
                    Icon(Icons.Default.Logout, null, tint = ErrorRed); Spacer(Modifier.width(8.dp))
                    Text(if (isChannel) "Отписаться" else "Покинуть группу", color = ErrorRed)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ── Диалог добавления участника ───────────────────────────
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; vm.onAddQueryChanged("") },
            containerColor = BgSecondary,
            title = { Text("Добавить участника", color = TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = addQuery, onValueChange = { vm.onAddQueryChanged(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Имя, @username или +7номер", color = TextSecondary) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Accent, unfocusedBorderColor = BgTertiary, cursorColor = Accent,
                            focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max = 260.dp)) {
                        addResults.forEach { u ->
                            Row(Modifier.fillMaxWidth().clickable { vm.addMember(u.uid); showAddDialog = false }
                                .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(AccentMuted), Alignment.Center) {
                                    Text(u.displayName.firstOrNull()?.toString() ?: "?", color = Color.White, fontSize = 14.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(u.displayName, color = TextPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ showAddDialog = false; vm.onAddQueryChanged("") }) { Text("Закрыть", color = TextSecondary) } }
        )
    }

    // ── Подтверждение выхода ──────────────────────────────────
    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            containerColor = BgSecondary,
            title = { Text(if (isChannel) "Отписаться от канала?" else "Покинуть группу?", color = TextPrimary) },
            confirmButton = {
                TextButton({ showLeaveConfirm = false; vm.leave(onLeft) }) { Text("Да", color = ErrorRed) }
            },
            dismissButton = { TextButton({ showLeaveConfirm = false }) { Text("Отмена", color = TextSecondary) } }
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent))
    }
}
