package com.rosechat.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rosechat.data.model.Chat
import com.rosechat.data.model.ChatType
import com.rosechat.data.model.PhotoRef
import com.rosechat.data.model.User
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.ui.common.SmartImage
import com.rosechat.ui.common.hasImage
import com.rosechat.ui.common.premiumBadgeEmoji
import com.rosechat.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeViewModel : ViewModel() {
    private val _me        = MutableStateFlow<User?>(null)
    val me = _me.asStateFlow()
    private val _chats     = MutableStateFlow<List<Chat>>(emptyList())
    val chats = _chats.asStateFlow()
    private val _names     = MutableStateFlow<Map<String, String>>(emptyMap())
    val names = _names.asStateFlow()
    private val _photos    = MutableStateFlow<Map<String, PhotoRef>>(emptyMap())
    val photos = _photos.asStateFlow()
    private val _query     = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val _results   = MutableStateFlow<List<User>>(emptyList())
    val results = _results.asStateFlow()
    private val _channelResults = MutableStateFlow<List<Chat>>(emptyList())
    val channelResults = _channelResults.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching = _searching.asStateFlow()
    private val _searchErr = MutableStateFlow<String?>(null)
    val searchErr = _searchErr.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val uid = FirebaseRepo.currentUid ?: return@launch
            FirebaseRepo.observeUser(uid).collect { _me.value = it }
        }
        viewModelScope.launch {
            val uid = FirebaseRepo.currentUid ?: return@launch
            FirebaseRepo.setOnline(uid, true)
        }
        viewModelScope.launch {
            val uid = FirebaseRepo.currentUid ?: return@launch
            FirebaseRepo.getChats(uid)
                .catch { e -> _openError.value = "Список чатов: ${e.message ?: "ошибка загрузки"}" }
                .collect { list ->
                    _chats.value = list
                    list.forEach { chat ->
                        if (_names.value[chat.id] == null) {
                            val n = FirebaseRepo.getChatPartnerName(chat, uid)
                            val p = FirebaseRepo.getChatPartnerPhoto(chat, uid)
                            _names.update  { it + (chat.id to n) }
                            _photos.update { it + (chat.id to p) }
                        }
                    }
                }
        }
    }

    fun onQueryChanged(q: String) {
        _query.value = q
        _searchErr.value = null
        if (q.isBlank()) { _results.value = emptyList(); _channelResults.value = emptyList(); return }

        // Debounce 400ms
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            if (q.length < 2) { _results.value = emptyList(); _channelResults.value = emptyList(); return@launch }
            _searching.value = true
            try {
                val myUid = FirebaseRepo.currentUid ?: return@launch
                val found = mutableListOf<User>()

                // 1. По @username — точный поиск через коллекцию usernames
                val clean = q.removePrefix("@").trim()
                val byUsername = FirebaseRepo.findByUsername(clean)
                if (byUsername != null && byUsername.uid != myUid) found.add(byUsername)

                // 2. По номеру телефона
                if (found.isEmpty() && q.startsWith("+")) {
                    val byPhone = FirebaseRepo.findByPhone(q)
                    if (byPhone != null && byPhone.uid != myUid) found.add(byPhone)
                }

                // 3. По email
                if (found.isEmpty() && q.contains("@") && q.contains(".")) {
                    val byEmail = FirebaseRepo.findByEmail(q)
                    if (byEmail != null && byEmail.uid != myUid) found.add(byEmail)
                }

                // 4. По имени — локальный поиск среди всех пользователей
                if (found.isEmpty()) {
                    val byName = FirebaseRepo.searchByName(clean, myUid)
                    found.addAll(byName)
                }

                _results.value = found.distinctBy { it.uid }
                // 5. Публичные каналы по названию/юзернейму
                _channelResults.value = FirebaseRepo.searchChannels(q).filter { !it.memberIds.contains(myUid) }

                if (found.isEmpty() && _channelResults.value.isEmpty()) _searchErr.value = "Никого не найдено. Попробуй @username или +7номер"
            } catch (e: Exception) {
                _searchErr.value = "Ошибка поиска: ${e.message}"
            } finally {
                _searching.value = false
            }
        }
    }

    private val _openError = MutableStateFlow<String?>(null)
    val openError = _openError.asStateFlow()
    fun clearOpenError() = _openError.update { null }

    // Раньше исключение тут проглатывалось молча ("" to "") — если запрос падал
    // (например, не создан индекс в Firestore), тап по пользователю просто ничего
    // не делал без единого объяснения. Теперь реальная причина уходит в openError,
    // и UI показывает её снекбаром.
    suspend fun openChat(other: User): Pair<String, String> {
        return try {
            val myUid = FirebaseRepo.currentUid ?: return "" to ""
            val id = FirebaseRepo.getOrCreatePrivateChat(myUid, other.uid)
            id to other.displayName
        } catch (e: Exception) { _openError.value = e.message ?: "Не удалось открыть чат"; "" to "" }
    }

    // Тап по публичному каналу в поиске: сразу подписываем и открываем
    suspend fun openChannel(channel: Chat): Pair<String, String> {
        return try {
            val myUid = FirebaseRepo.currentUid ?: return "" to ""
            FirebaseRepo.joinChannel(channel.id, myUid)
            channel.id to channel.name
        } catch (e: Exception) { _openError.value = e.message ?: "Не удалось открыть канал"; "" to "" }
    }

    suspend fun createGroup(name: String): String {
        return try {
            FirebaseRepo.createGroup(name, emptyList(), FirebaseRepo.currentUid ?: return "")
        } catch (_: Exception) { "" }
    }

    suspend fun createChannel(name: String, description: String, isPublic: Boolean, username: String, broadcastOnly: Boolean): Result<String> {
        val myUid = FirebaseRepo.currentUid ?: return Result.failure(Exception("Не авторизован"))
        return FirebaseRepo.createChannel(name, description, isPublic, username, broadcastOnly, myUid)
    }

    fun archiveChat(chatId: String) {
        val uid = FirebaseRepo.currentUid ?: return
        viewModelScope.launch { FirebaseRepo.archiveChat(chatId, uid) }
    }

    fun unarchiveChat(chatId: String) {
        val uid = FirebaseRepo.currentUid ?: return
        viewModelScope.launch { FirebaseRepo.unarchiveChat(chatId, uid) }
    }

    override fun onCleared() {
        FirebaseRepo.currentUid?.let { FirebaseRepo.setOnline(it, false) }
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenChat: (String, String) -> Unit,
    onOpenCalls: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenStoryViewer: (Int) -> Unit = {},
    vm: HomeViewModel = viewModel()
) {
    val chats     by vm.chats.collectAsStateWithLifecycle()
    val names     by vm.names.collectAsStateWithLifecycle()
    val photos    by vm.photos.collectAsStateWithLifecycle()
    val me        by vm.me.collectAsStateWithLifecycle()
    val query     by vm.query.collectAsStateWithLifecycle()
    val results   by vm.results.collectAsStateWithLifecycle()
    val channelResults by vm.channelResults.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val searchErr by vm.searchErr.collectAsStateWithLifecycle()
    val openError by vm.openError.collectAsStateWithLifecycle()
    var showSearch     by remember { mutableStateOf(false) }
    var showNewGroup   by remember { mutableStateOf(false) }
    var showNewChannel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(openError) {
        openError?.let { snackbarHost.showSnackbar(it); vm.clearOpenError() }
    }

    Scaffold(
        containerColor = BgPrimary,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            if (showSearch) {
                Column(Modifier.background(BgSecondary)) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showSearch = false; vm.onQueryChanged("") }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { vm.onQueryChanged(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("@username, имя или +7номер", color = TextSecondary) },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                focusedBorderColor = Accent, unfocusedBorderColor = BgTertiary,
                                cursorColor = Accent,
                                focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary
                            )
                        )
                        if (searching) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                        }
                    }
                    // Подсказка как искать
                    if (query.isEmpty()) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("@username") { vm.onQueryChanged("@") }
                            Chip("+7номер") { vm.onQueryChanged("+7") }
                            Chip("Имя") { }
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(com.rosechat.R.drawable.logo_pinggram_mark),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("PingGram", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            if (me?.isPremium == true && me?.showPremiumBadge != false) { Spacer(Modifier.width(6.dp)); Text(premiumBadgeEmoji(me?.premiumBadgeIcon ?: "star"), fontSize = 16.sp) }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, null, tint = TextSecondary) }
                        IconButton(onClick = onOpenCalls) { Icon(Icons.Default.Call, null, tint = TextSecondary) }
                        if (me?.isDeveloper == true) {
                            IconButton(onClick = onOpenDeveloper) { Icon(Icons.Default.Code, null, tint = Accent) }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BgSecondary)
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = { showNewChannel = true },
                    containerColor = BgSecondary, contentColor = Accent, shape = RoundedCornerShape(12.dp)
                ) { Icon(Icons.Default.Campaign, null) }
                SmallFloatingActionButton(
                    onClick = { showNewGroup = true },
                    containerColor = BgSecondary, contentColor = Accent, shape = RoundedCornerShape(12.dp)
                ) { Icon(Icons.Default.Group, null) }
                FloatingActionButton(
                    onClick = { showSearch = true },
                    containerColor = Accent, contentColor = Color.White, shape = RoundedCornerShape(16.dp)
                ) { Icon(Icons.Default.Edit, null) }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 4.dp)) {

            // ── Результаты поиска ─────────────────────────
            if (showSearch) {
                if (query.length >= 2 && results.isEmpty() && channelResults.isEmpty() && !searching && searchErr != null) {
                    val isRealError = searchErr!!.startsWith("Ошибка поиска")
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (isRealError) "⚠️" else "🔍", fontSize = 40.sp)
                                Spacer(Modifier.height(12.dp))
                                Text(if (isRealError) "Ошибка поиска" else "Никого не найдено", color = TextSecondary, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (isRealError) searchErr!! else "Попробуй:\n• @username\n• +79001234567\n• Имя пользователя",
                                    color = TextTertiary, style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                items(results, key = { "u_" + it.uid }) { user ->
                    UserRow(user = user, onClick = {
                        scope.launch {
                            val (id, name) = vm.openChat(user)
                            if (id.isNotEmpty()) { showSearch = false; onOpenChat(id, name) }
                        }
                    })
                    HorizontalDivider(Modifier.padding(start = 76.dp), 0.5.dp, Divider)
                }
                items(channelResults, key = { "c_" + it.id }) { channel ->
                    ChannelResultRow(channel = channel, onClick = {
                        scope.launch {
                            val (id, name) = vm.openChannel(channel)
                            if (id.isNotEmpty()) { showSearch = false; onOpenChat(id, name) }
                        }
                    })
                    HorizontalDivider(Modifier.padding(start = 76.dp), 0.5.dp, Divider)
                }
            }

            // ── Список чатов ─────────────────────────────
            if (!showSearch) {
                val myUid = me?.uid
                item(key = "stories_bar") {
                    val contactUids = remember(chats, myUid) {
                        chats.filter { it.type == ChatType.PRIVATE || it.type == ChatType.GROUP }
                            .flatMap { it.memberIds }.filter { it != myUid }.toSet()
                    }
                    com.rosechat.ui.stories.StoriesBar(me = me, contactUids = contactUids, onOpenViewer = onOpenStoryViewer)
                    HorizontalDivider(Modifier.padding(top = 4.dp), 0.5.dp, Divider)
                }
                val visibleChats = chats.filter { it.type == ChatType.SAVED || myUid == null || !it.archivedBy.contains(myUid) }
                val archivedCount = chats.count { it.type != ChatType.SAVED && myUid != null && it.archivedBy.contains(myUid) }

                if (archivedCount > 0) {
                    item(key = "archive_row") {
                        Row(
                            Modifier.fillMaxWidth().clickable(onClick = onOpenArchive).padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(54.dp).clip(CircleShape).background(BgTertiary), Alignment.Center) {
                                Icon(Icons.Default.Archive, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Text("Архив", style = MaterialTheme.typography.titleMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(10.dp), color = BgTertiary) {
                                Text("$archivedCount", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 82.dp), 0.5.dp, Divider)
                    }
                }

                items(visibleChats, key = { it.id }) { chat ->
                    val name  = names[chat.id]  ?: chat.name.ifEmpty { "Чат" }
                    val photo = photos[chat.id] ?: PhotoRef()
                    var showChatMenu by remember(chat.id) { mutableStateOf(false) }
                    Box {
                        ChatRow(
                            chat = chat, name = name, photo = photo, myUid = myUid,
                            onClick = { onOpenChat(chat.id, name) },
                            onLongClick = if (chat.type != ChatType.SAVED) ({ showChatMenu = true }) else null
                        )
                        DropdownMenu(showChatMenu, { showChatMenu = false }, Modifier.background(BgSecondary)) {
                            DropdownMenuItem(
                                { Text("В архив", color = TextPrimary) },
                                { showChatMenu = false; vm.archiveChat(chat.id) },
                                leadingIcon = { Icon(Icons.Default.Archive, null, tint = TextSecondary) }
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 82.dp), 0.5.dp, Divider)
                }

                if (chats.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 80.dp), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("💬", fontSize = 56.sp)
                                Spacer(Modifier.height(16.dp))
                                Text("Нет чатов", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                                Text("Нажми ✏️ чтобы найти друга\nпо @username или номеру",
                                    color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Диалог новой группы ───────────────────────────────
    if (showNewGroup) {
        var groupName by remember { mutableStateOf("") }
        var creating  by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!creating) showNewGroup = false },
            containerColor = BgSecondary,
            title = { Text("Новая группа", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = groupName, onValueChange = { groupName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название группы", color = TextSecondary) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Accent, unfocusedBorderColor = BgTertiary,
                        cursorColor = Accent,
                        focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (groupName.isNotBlank() && !creating) {
                            creating = true
                            scope.launch {
                                val id = vm.createGroup(groupName)
                                creating = false
                                showNewGroup = false
                                if (id.isNotEmpty()) onOpenChat(id, groupName)
                            }
                        }
                    },
                    enabled = groupName.isNotBlank() && !creating
                ) {
                    if (creating) CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                    else Text("Создать", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewGroup = false }) { Text("Отмена", color = TextSecondary) }
            }
        )
    }

    // ── Диалог нового канала ───────────────────────────────
    if (showNewChannel) {
        var channelName by remember { mutableStateOf("") }
        var channelDesc by remember { mutableStateOf("") }
        var isPublic    by remember { mutableStateOf(true) }
        var username    by remember { mutableStateOf("") }
        var broadcastOnly by remember { mutableStateOf(true) }
        var creating    by remember { mutableStateOf(false) }
        var error       by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!creating) showNewChannel = false },
            containerColor = BgSecondary,
            title = { Text("Новый канал", color = TextPrimary) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = channelName, onValueChange = { channelName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название канала", color = TextSecondary) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Accent, unfocusedBorderColor = BgTertiary, cursorColor = Accent,
                            focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = channelDesc, onValueChange = { channelDesc = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Описание (необязательно)", color = TextSecondary) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Accent, unfocusedBorderColor = BgTertiary, cursorColor = Accent,
                            focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Публичный", color = TextPrimary)
                            Text("Виден в поиске, вступить может любой", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(isPublic, { isPublic = it }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
                    }
                    if (isPublic) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = username, onValueChange = { username = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(24) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("@username канала", color = TextSecondary) },
                            singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                                focusedBorderColor = Accent, unfocusedBorderColor = BgTertiary, cursorColor = Accent,
                                focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary
                            )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Только админы пишут", color = TextPrimary)
                            Text("Классический канал-рассылка", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(broadcastOnly, { broadcastOnly = it }, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
                    }
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (channelName.isNotBlank() && !creating && (!isPublic || username.length >= 5)) {
                            creating = true; error = null
                            scope.launch {
                                val result = vm.createChannel(channelName, channelDesc, isPublic, username, broadcastOnly)
                                creating = false
                                result.onSuccess { id ->
                                    showNewChannel = false
                                    onOpenChat(id, channelName)
                                }.onFailure { e -> error = e.message }
                            }
                        }
                    },
                    enabled = channelName.isNotBlank() && !creating
                ) {
                    if (creating) CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                    else Text("Создать", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewChannel = false }) { Text("Отмена", color = TextSecondary) }
            }
        )
    }
}



@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = BgTertiary,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun ChatRow(chat: Chat, name: String, photo: PhotoRef, myUid: String?, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val time = chat.lastMessageTime?.toDate()
        ?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: ""
    val isSaved = chat.type == ChatType.SAVED
    val unread = myUid?.let { chat.unreadCount[it] } ?: 0

    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Аватар (+ бейдж непрочитанных снизу-справа, как в Telegram)
        Box {
            Box(
                Modifier.size(54.dp).clip(CircleShape)
                    .background(when {
                        isSaved -> Accent
                        chat.type == ChatType.GROUP || chat.type == ChatType.CHANNEL -> AccentMuted
                        else -> BgTertiary
                    }),
                Alignment.Center
            ) {
                when {
                    isSaved -> Icon(Icons.Default.Bookmarks, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    photo.url.isNotEmpty() || photo.data.isNotEmpty() -> SmartImage(photo.url, photo.data, Modifier.fillMaxSize(), ContentScale.Crop)
                    chat.type == ChatType.CHANNEL -> Icon(Icons.Default.Campaign, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    chat.type == ChatType.GROUP -> Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    else -> Text(name.firstOrNull()?.toString() ?: "?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            if (unread > 0) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape)
                        .background(Color(0xFFE53935)),
                    Alignment.Center
                ) {
                    Text(
                        if (unread > 99) "99+" else "$unread",
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSaved) Accent else TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(time, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                chat.lastMessage.ifEmpty { "Нет сообщений" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (unread > 0) TextPrimary else TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ChannelResultRow(channel: Chat, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(AccentMuted), Alignment.Center) {
            Icon(Icons.Default.Campaign, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "@${channel.channelUsername} · ${channel.memberIds.size} подписчиков",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary
            )
        }
        Surface(shape = RoundedCornerShape(8.dp), color = BgTertiary) {
            Text("Канал", style = MaterialTheme.typography.labelSmall, color = Accent, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun UserRow(user: User, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(AccentMuted), Alignment.Center) {
            if (hasImage(user.photoUrl, user.photoData))
                SmartImage(user.photoUrl, user.photoData, Modifier.fillMaxSize(), ContentScale.Crop)
            else
                Text(user.displayName.firstOrNull()?.toString() ?: "?",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.displayName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                if (user.isPremium && user.showPremiumBadge) { Spacer(Modifier.width(4.dp)); Text(premiumBadgeEmoji(user.premiumBadgeIcon), fontSize = 14.sp) }
                if (user.profileSticker.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Text(user.profileSticker, fontSize = 14.sp) }
            }
            Text(
                buildString {
                    if (user.username.isNotEmpty()) append("@${user.username}")
                    if (user.phone.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(user.phone)
                    }
                }.ifEmpty { user.email },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        if (user.isOnline) Box(Modifier.size(10.dp).clip(CircleShape).background(Online))
    }
}
