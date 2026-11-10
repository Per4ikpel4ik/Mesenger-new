package com.rosechat.ui.archive

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rosechat.data.model.Chat
import com.rosechat.data.model.ChatType
import com.rosechat.data.model.PhotoRef
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.ui.home.ChatRow
import com.rosechat.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArchiveViewModel : ViewModel() {
    private val _chats  = MutableStateFlow<List<Chat>>(emptyList())
    val chats = _chats.asStateFlow()
    private val _names  = MutableStateFlow<Map<String, String>>(emptyMap())
    val names = _names.asStateFlow()
    private val _photos = MutableStateFlow<Map<String, PhotoRef>>(emptyMap())
    val photos = _photos.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = FirebaseRepo.currentUid ?: return@launch
            FirebaseRepo.getChats(uid).collect { list ->
                val archived = list.filter { it.type != ChatType.SAVED && it.archivedBy.contains(uid) }
                _chats.value = archived
                archived.forEach { chat ->
                    if (_names.value[chat.id] == null) {
                        _names.update { it + (chat.id to FirebaseRepo.getChatPartnerName(chat, uid)) }
                        _photos.update { it + (chat.id to FirebaseRepo.getChatPartnerPhoto(chat, uid)) }
                    }
                }
            }
        }
    }

    fun unarchive(chatId: String) {
        val uid = FirebaseRepo.currentUid ?: return
        viewModelScope.launch { FirebaseRepo.unarchiveChat(chatId, uid) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(onBack: () -> Unit, onOpenChat: (String, String) -> Unit, vm: ArchiveViewModel = viewModel()) {
    val chats  by vm.chats.collectAsStateWithLifecycle()
    val names  by vm.names.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            TopAppBar(
                title = { Text("Архив", color = TextPrimary) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgSecondary)
            )
        }
    ) { padding ->
        if (chats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inventory2, null, tint = TextTertiary, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Архив пуст", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Text("Долгое нажатие на чат в списке — «В архив»", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(chats, key = { it.id }) { chat ->
                    val name  = names[chat.id]  ?: chat.name.ifEmpty { "Чат" }
                    val photo = photos[chat.id] ?: PhotoRef()
                    var showMenu by remember(chat.id) { mutableStateOf(false) }
                    Box {
                        ChatRow(
                            chat = chat, name = name, photo = photo, myUid = FirebaseRepo.currentUid,
                            onClick = { onOpenChat(chat.id, name) },
                            onLongClick = { showMenu = true }
                        )
                        DropdownMenu(showMenu, { showMenu = false }, Modifier.background(BgSecondary)) {
                            DropdownMenuItem(
                                { Text("Разархивировать", color = TextPrimary) },
                                { showMenu = false; vm.unarchive(chat.id) },
                                leadingIcon = { Icon(Icons.Default.Unarchive, null, tint = TextSecondary) }
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 82.dp), 0.5.dp, Divider)
                }
            }
        }
    }
}
