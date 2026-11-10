@file:OptIn(ExperimentalMaterial3Api::class)

package com.rosechat.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rosechat.shared.data.MediaKind
import com.rosechat.shared.data.SharedRepo
import com.rosechat.shared.data.cloudinaryUpload
import com.rosechat.shared.data.decodeImageBitmap
import com.rosechat.shared.data.downloadAndOpen
import com.rosechat.shared.data.fetchBytes
import com.rosechat.shared.data.formatChatTime
import com.rosechat.shared.data.googleSignIn
import com.rosechat.shared.data.loadAccentPref
import com.rosechat.shared.data.loadDarkThemePref
import com.rosechat.shared.data.saveAccentPref
import com.rosechat.shared.data.saveDarkThemePref
import com.rosechat.shared.data.mediaStorageLabel
import com.rosechat.shared.data.pickFile
import com.rosechat.shared.model.SChatType
import com.rosechat.shared.model.SMessageType
import com.rosechat.shared.model.SharedChat
import com.rosechat.shared.model.SharedMessage
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.rosechat.shared.data.loadBoolPref
import com.rosechat.shared.data.saveBoolPref
import com.rosechat.shared.model.CHAT_STICKERS
import com.rosechat.shared.model.PREMIUM_FRAME_COLORS
import com.rosechat.shared.model.SChat
import com.rosechat.shared.model.SUser
import com.rosechat.shared.model.SharedUser
import com.rosechat.shared.model.clearedSecondsFor
import com.rosechat.shared.model.isArchivedFor
import com.rosechat.shared.model.isHiddenFor
import com.rosechat.shared.model.lastMessagePreviewFor
import com.rosechat.shared.model.tsSeconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Палитра десктопа (тёмная/светлая) — по мотивам макета из присланного ТЗ по дизайну:
// тёмная #18191A/#242526/#E4E6EB, светлая #FFFFFF/#F8F9FA/#1C1E21, экран настроек на
// сероватом #F5F6F8 с белыми карточками. Сама структура (палитра как @Composable get(),
// переключаемая через CompositionLocal) списана с LocalPingGramPalette в Android-теме —
// поэтому BgPrimary/Accent/... объявлены как "@Composable get()".
private data class ChatPalette(
    val bg1: Color,
    val bg2: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val bubbleIn: Color,
    val danger: Color,
    val online: Color,
    val card: Color,
    val settingsBg: Color,
    val hover: Color,
)

private val DarkChatPalette = ChatPalette(
    bg1 = Color(0xFF18191A),
    bg2 = Color(0xFF242526),
    accent = Color(0xFFFF2D78),
    textPrimary = Color(0xFFE4E6EB),
    textSecondary = Color(0xFFB0B3B8),
    bubbleIn = Color(0xFF3A3B3C),
    danger = Color(0xFFFF453A),
    online = Color(0xFF34C759),
    card = Color(0xFF242526),
    settingsBg = Color(0xFF18191A),
    hover = Color(0xFF2F3031),
)

private val LightChatPalette = ChatPalette(
    bg1 = Color(0xFFFFFFFF),
    bg2 = Color(0xFFF8F9FA),
    accent = Color(0xFFE91E63),
    textPrimary = Color(0xFF1C1E21),
    textSecondary = Color(0xFF65676B),
    bubbleIn = Color(0xFFE9EBEE),
    danger = Color(0xFFD70015),
    online = Color(0xFF248A3D),
    card = Color(0xFFFFFFFF),
    settingsBg = Color(0xFFF5F6F8),
    hover = Color(0xFFF0F2F5),
)

private val LocalChatPalette = staticCompositionLocalOf { DarkChatPalette }

// Акцентный цвет выбирается отдельно от тёмная/светлая — накладывается поверх палитры
// через .copy(accent = ...) в App(). Имя ("pink"/"blue"/...) хранится в java.util.prefs.
private data class AccentOption(val key: String, val label: String, val color: Color)

private val AccentOptions = listOf(
    AccentOption("pink", "Розовый", Color(0xFFFF2D78)),
    AccentOption("blue", "Синий", Color(0xFF007AFF)),
    AccentOption("green", "Зелёный", Color(0xFF2ECC71)),
    AccentOption("purple", "Фиолетовый", Color(0xFFBF5AF2)),
    AccentOption("orange", "Оранжевый", Color(0xFFFF9F0A)),
)

private fun accentColorFor(key: String): Color = AccentOptions.firstOrNull { it.key == key }?.color ?: AccentOptions[0].color

private val BgPrimary: Color @Composable get() = LocalChatPalette.current.bg1
private val BgSecondary: Color @Composable get() = LocalChatPalette.current.bg2
private val Accent: Color @Composable get() = LocalChatPalette.current.accent
private val TextPrimary: Color @Composable get() = LocalChatPalette.current.textPrimary
private val TextSecondary: Color @Composable get() = LocalChatPalette.current.textSecondary
private val BubbleIn: Color @Composable get() = LocalChatPalette.current.bubbleIn
private val DangerColor: Color @Composable get() = LocalChatPalette.current.danger
private val OnlineColor: Color @Composable get() = LocalChatPalette.current.online
private val CardBg: Color @Composable get() = LocalChatPalette.current.card
private val SettingsBg: Color @Composable get() = LocalChatPalette.current.settingsBg
private val HoverBg: Color @Composable get() = LocalChatPalette.current.hover

private sealed class Screen {
    data object Login : Screen()
    data object Main : Screen()
}

private enum class Section { Chats, Archive, Settings }

private data class OpenChat(val id: String, val title: String, val photoUrl: String)

@Composable
fun App() {
    var isDark by remember { mutableStateOf(true) }
    var accentKey by remember { mutableStateOf("pink") }
    LaunchedEffect(Unit) {
        isDark = loadDarkThemePref()
        accentKey = loadAccentPref()
    }
    val palette = (if (isDark) DarkChatPalette else LightChatPalette).copy(accent = accentColorFor(accentKey))

    CompositionLocalProvider(LocalChatPalette provides palette) {
        MaterialTheme(
            colorScheme = if (isDark) {
                darkColorScheme(primary = palette.accent, background = palette.bg1, surface = palette.bg2, onBackground = palette.textPrimary, onSurface = palette.textPrimary)
            } else {
                lightColorScheme(primary = palette.accent, background = palette.bg1, surface = palette.bg2, onBackground = palette.textPrimary, onSurface = palette.textPrimary)
            },
        ) {
            var screen by remember { mutableStateOf<Screen>(Screen.Login) }

            // Сессия могла сохраниться с прошлого запуска (см. FirebaseInit.desktop.kt —
            // логин пишется на диск), тогда сразу открываем главный экран, а не форму входа.
            LaunchedEffect(Unit) {
                if (SharedRepo.currentUid != null) screen = Screen.Main
            }

            Surface(modifier = Modifier.fillMaxSize(), color = BgPrimary) {
                when (screen) {
                    is Screen.Login -> LoginScreen(onLoggedIn = { screen = Screen.Main })
                    is Screen.Main -> MainShell(
                        isDark = isDark,
                        onToggleDark = { checked -> isDark = checked; saveDarkThemePref(checked) },
                        accentKey = accentKey,
                        onSelectAccent = { key -> accentKey = key; saveAccentPref(key) },
                        onSignedOut = { screen = Screen.Login },
                    )
                }
            }
        }
    }
}

// Каркас в стиле Telegram Desktop: узкая полоса с иконками слева, дальше — список чатов и
// окно переписки рядом друг с другом (а не по очереди, как было раньше), либо настройки.
@Composable
private fun MainShell(
    isDark: Boolean,
    onToggleDark: (Boolean) -> Unit,
    accentKey: String,
    onSelectAccent: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
    var section by remember { mutableStateOf(Section.Chats) }
    var openChat by remember { mutableStateOf<OpenChat?>(null) }

    Row(Modifier.fillMaxSize()) {
        NavRail(section) { section = it }
        Box(Modifier.width(1.dp).fillMaxHeight().background(HoverBg))
        when (section) {
            Section.Chats, Section.Archive -> {
                Box(Modifier.width(340.dp).fillMaxHeight().background(BgPrimary)) {
                    key(section) {
                        ChatListPane(
                            archived = section == Section.Archive,
                            selectedId = openChat?.id,
                            onOpenChat = { openChat = it },
                            onChatRemoved = { id -> if (openChat?.id == id) openChat = null },
                        )
                    }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(HoverBg))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val chat = openChat
                    if (chat == null) {
                        EmptyChatPlaceholder()
                    } else {
                        // key(chat.id) — чтобы при переключении чата состояние переписки
                        // (черновик, список сообщений) не переезжало из предыдущего чата.
                        key(chat.id) { ChatScreen(chat.id, chat.title, chat.photoUrl, onClose = { openChat = null }) }
                    }
                }
            }
            Section.Settings -> Box(Modifier.weight(1f).fillMaxHeight()) {
                SettingsScreen(
                    onSignedOut = onSignedOut,
                    isDark = isDark,
                    onToggleDark = onToggleDark,
                    accentKey = accentKey,
                    onSelectAccent = onSelectAccent,
                )
            }
        }
    }
}

@Composable
private fun NavRail(section: Section, onSelect: (Section) -> Unit) {
    Column(
        Modifier.width(68.dp).fillMaxHeight().background(BgSecondary).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RailButton(Icons.Default.ChatBubble, "Чаты", section == Section.Chats) { onSelect(Section.Chats) }
        RailButton(Icons.Default.Archive, "Архив", section == Section.Archive) { onSelect(Section.Archive) }
        RailButton(Icons.Default.Settings, "Настройки", section == Section.Settings) { onSelect(Section.Settings) }
    }
}

// Круглая кнопка с подсветкой при наведении курсора.
@Composable
private fun RailButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(if (selected) Accent.copy(alpha = 0.18f) else if (hovered) HoverBg else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (selected) Accent else TextSecondary)
    }
}

@Composable
private fun EmptyChatPlaceholder() {
    Box(Modifier.fillMaxSize().background(BgPrimary), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ChatBubble, null, tint = TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("Выберите чат, чтобы начать переписку", color = TextSecondary)
        }
    }
}

@Composable
private fun LoginScreen(onLoggedIn: () -> Unit) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("PingGram", color = Accent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (isRegisterMode) "Создать аккаунт" else "Вход в аккаунт",
            color = TextSecondary,
        )
        Spacer(Modifier.height(32.dp))

        Column(modifier = Modifier.widthIn(max = 360.dp)) {
            if (isRegisterMode) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("@username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = DangerColor)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    error = null
                    loading = true
                    scope.launch {
                        val result = if (isRegisterMode) {
                            SharedRepo.register(email.trim(), password, name, username)
                        } else {
                            SharedRepo.signIn(email.trim(), password)
                        }
                        loading = false
                        result.onSuccess { onLoggedIn() }
                            .onFailure { error = it.message ?: "Что-то пошло не так" }
                    }
                },
                enabled = !loading && email.isNotBlank() && password.isNotBlank() &&
                    (!isRegisterMode || name.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text(if (isRegisterMode) "Зарегистрироваться" else "Войти")
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = TextSecondary.copy(alpha = 0.3f))
                Text("  или  ", color = TextSecondary)
                HorizontalDivider(modifier = Modifier.weight(1f), color = TextSecondary.copy(alpha = 0.3f))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    error = null
                    loading = true
                    scope.launch {
                        val result = googleSignIn()
                        loading = false
                        result.onSuccess { onLoggedIn() }
                            .onFailure { error = it.message ?: "Не удалось войти через Google" }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Войти через Google")
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { isRegisterMode = !isRegisterMode; error = null }) {
                Text(
                    if (isRegisterMode) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться",
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ChatListPane(archived: Boolean, selectedId: String?, onOpenChat: (OpenChat) -> Unit, onChatRemoved: (String) -> Unit) {
    val myUid = SharedRepo.currentUid
    var chats by remember { mutableStateOf<List<SharedChat>>(emptyList()) }
    var displays by remember { mutableStateOf<Map<String, SharedRepo.ChatDisplay>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, SharedChat>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(myUid) {
        if (myUid == null) return@LaunchedEffect
        SharedRepo.observeChats(myUid).collect { list ->
            chats = list
            scope.launch {
                val updated = displays.toMutableMap()
                list.forEach { chat ->
                    if (updated[chat.id] == null) {
                        updated[chat.id] = SharedRepo.chatDisplayInfo(chat, myUid)
                    }
                }
                displays = updated
            }
        }
    }

    fun titleOf(chat: SharedChat): String =
        if (chat.data.type == SChatType.SAVED) "Избранное" else (displays[chat.id]?.name ?: chat.data.name.ifBlank { "…" })

    // Скрытые ("Удалить чат") не показываем; архив — отдельная вкладка; Избранное всегда в основном списке.
    val visibleChats = remember(chats, archived, myUid) {
        chats.filter { c ->
            if (c.data.isHiddenFor(myUid)) false
            else if (c.data.type == SChatType.SAVED) !archived
            else c.data.isArchivedFor(myUid) == archived
        }.sortedByDescending { it.data.type == SChatType.SAVED }
    }
    val filteredChats = remember(visibleChats, displays, query) {
        if (query.isBlank()) visibleChats
        else visibleChats.filter { chat -> titleOf(chat).contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (archived) "Архив" else "PingGram",
                color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            if (!archived) {
                IconButton(onClick = { showNew = true }) { Icon(Icons.Default.Add, "Новый чат", tint = Accent) }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            placeholder = { Text("Поиск...", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null, tint = TextSecondary) }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = HoverBg, unfocusedContainerColor = HoverBg,
                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            ),
        )
        if (filteredChats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (visibleChats.isEmpty()) (if (archived) "Архив пуст" else "Пока нет чатов") else "Ничего не найдено", color = TextSecondary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filteredChats, key = { it.id }) { chat ->
                    val display = displays[chat.id]
                    val title = titleOf(chat)
                    val photo = display?.photoUrl ?: ""
                    val preview = chat.data.lastMessagePreviewFor(myUid)
                    val prefix = if (myUid != null && chat.data.lastSenderId == myUid && preview.isNotBlank()) "Вы: " else ""
                    val seconds = tsSeconds(chat.data.lastMessageTime)
                    val unread = if (myUid != null) (chat.data.unreadCount[myUid] ?: 0) else 0
                    val isSaved = chat.data.type == SChatType.SAVED
                    val actions = buildList<Pair<String, () -> Unit>> {
                        if (!isSaved) {
                            if (archived) add("Вернуть из архива" to { scope.launch { if (myUid != null) SharedRepo.unarchiveChat(chat.id, myUid) } })
                            else add("В архив" to { scope.launch { if (myUid != null) SharedRepo.archiveChat(chat.id, myUid) } })
                        }
                        add("Очистить историю" to { pending = "clear" to chat })
                        if (!isSaved) add("Удалить чат" to { pending = "delete" to chat })
                    }
                    ChatRow(
                        title = title,
                        photoUrl = photo,
                        frame = display?.frame,
                        lastMessage = if (preview.isBlank()) "Нет сообщений" else prefix + preview,
                        timeText = if (seconds != null) formatChatTime(seconds) else "",
                        unread = unread,
                        selected = chat.id == selectedId,
                        actions = actions,
                        onClick = { onOpenChat(OpenChat(chat.id, title, photo)) },
                    )
                }
            }
        }
    }

    if (showNew) {
        NewChatDialog(onDismiss = { showNew = false }, onOpen = { onOpenChat(it) })
    }
    pending?.let { (action, chat) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (action == "clear") "Очистить историю?" else "Удалить чат?") },
            text = { Text("Это действие только для вас — у собеседника сообщения останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    if (myUid != null) scope.launch {
                        if (action == "clear") SharedRepo.clearChatHistory(chat.id, myUid)
                        else { SharedRepo.deleteChatForMe(chat.id, myUid); onChatRemoved(chat.id) }
                    }
                }) { Text("Да", color = DangerColor) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Отмена") } },
        )
    }
}

// Новый личный чат (поиск по имени/@нику/email) или новая группа.
@Composable
private fun NewChatDialog(onDismiss: () -> Unit, onOpen: (OpenChat) -> Unit) {
    val myUid = SharedRepo.currentUid
    var group by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SharedUser>>(emptyList()) }
    var groupName by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Map<String, SharedUser>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        if (myUid == null) return@LaunchedEffect
        delay(300)
        results = SharedRepo.searchUsers(query, myUid)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group) "Новая группа" else "Новый чат") },
        text = {
            Column(Modifier.width(420.dp)) {
                if (group) {
                    OutlinedTextField(groupName, { groupName = it }, label = { Text("Название группы") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(query, { query = it }, label = { Text("Имя, @username или email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (group && selected.isNotEmpty()) {
                    Text("Выбрано: " + selected.values.joinToString { it.data.displayName.ifBlank { it.data.username } }, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                error?.let { Text(it, color = DangerColor, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().height(260.dp)) {
                    items(results, key = { it.uid }) { u ->
                        val name = u.data.displayName.ifBlank { u.data.username.ifBlank { "Пользователь" } }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                if (group) {
                                    selected = if (selected.containsKey(u.uid)) selected - u.uid else selected + (u.uid to u)
                                } else if (myUid != null) {
                                    scope.launch {
                                        try {
                                            val id = SharedRepo.getOrCreatePrivateChat(myUid, u.uid)
                                            onOpen(OpenChat(id, name, u.data.photoUrl))
                                            onDismiss()
                                        } catch (e: Exception) { error = e.message ?: "Не удалось открыть чат" }
                                    }
                                }
                            }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(u.data.photoUrl, name, 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                if (u.data.username.isNotBlank()) Text("@" + u.data.username, color = TextSecondary, fontSize = 12.sp)
                            }
                            if (group && selected.containsKey(u.uid)) Icon(Icons.Default.Check, null, tint = Accent)
                        }
                    }
                }
                TextButton(onClick = { group = !group; error = null }) { Text(if (group) "← Обычный чат" else "Создать группу") }
            }
        },
        confirmButton = {
            if (group) {
                TextButton(
                    enabled = groupName.isNotBlank() && selected.isNotEmpty() && myUid != null,
                    onClick = {
                        if (myUid == null) return@TextButton
                        scope.launch {
                            try {
                                val id = SharedRepo.createGroup(groupName, selected.keys.toList(), myUid)
                                onOpen(OpenChat(id, groupName.trim(), ""))
                                onDismiss()
                            } catch (e: Exception) { error = e.message ?: "Не удалось создать группу" }
                        }
                    },
                ) { Text("Создать") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun ChatRow(
    title: String, photoUrl: String, frame: String?, lastMessage: String, timeText: String,
    unread: Int, selected: Boolean, actions: List<Pair<String, () -> Unit>>, onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(false) }
    val bg = if (selected) Accent else if (hovered) HoverBg else Color.Transparent
    val nameColor = if (selected) Color.White else TextPrimary
    val subColor = if (selected) Color.White.copy(alpha = 0.85f) else TextSecondary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(photoUrl = photoUrl, label = title, size = 48.dp, frame = frameColorFor(frame))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = nameColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (timeText.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(timeText, color = subColor, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lastMessage, color = subColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (unread > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(CircleShape)
                            .background(if (selected) Color.White else Accent).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (unread > 99) "99+" else unread.toString(), color = if (selected) Accent else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.MoreVert, "Меню", tint = if (hovered || menu) subColor else Color.Transparent, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                actions.forEach { (label, act) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; act() })
                }
            }
        }
    }
}

private fun frameColorFor(key: String?): Color? {
    if (key == null) return null
    val v = PREMIUM_FRAME_COLORS.firstOrNull { it.first == key }?.second ?: 0xFFFFD700
    return Color(v)
}

// Кружок-аватарка: если есть photoUrl — реальное фото; нет ссылки или не удалось
// загрузить — кружок с первой буквой имени, с градиентом и лёгкой тенью для объёма.
@Composable
private fun Avatar(photoUrl: String, label: String, size: androidx.compose.ui.unit.Dp, frame: Color? = null) {
    if (frame == null) {
        AvatarCore(photoUrl, label, size)
    } else {
        Box(Modifier.border(2.dp, frame, CircleShape).padding(3.dp)) { AvatarCore(photoUrl, label, size - 10.dp) }
    }
}

@Composable
private fun AvatarCore(photoUrl: String, label: String, size: androidx.compose.ui.unit.Dp) {
    var bitmap by remember(photoUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(photoUrl) {
        bitmap = null
        if (photoUrl.isNotBlank()) {
            fetchBytes(photoUrl).onSuccess { bytes -> bitmap = decodeImageBitmap(bytes) }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bmp, contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val hue = label.hashCode().mod(6)
        val avatarColor = listOf(
            Color(0xFFFF2D78), Color(0xFF5E5CE6), Color(0xFF32ADE6),
            Color(0xFF34C759), Color(0xFFFF9F0A), Color(0xFFBF5AF2),
        )[if (hue < 0) hue + 6 else hue]
        Box(
            modifier = Modifier.size(size).shadow(3.dp, CircleShape).clip(CircleShape)
                .background(Brush.linearGradient(listOf(avatarColor, lerp(avatarColor, Color.Black, 0.3f)))),
            contentAlignment = Alignment.Center,
        ) { Text(initial, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value / 2.2).sp) }
    }
}

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        shadowElevation = 2.dp,
    ) { content() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = TextSecondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun SettingsScreen(onSignedOut: () -> Unit, isDark: Boolean, onToggleDark: (Boolean) -> Unit, accentKey: String, onSelectAccent: (String) -> Unit) {
    val myUid = SharedRepo.currentUid
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var uploadingPhoto by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var userData by remember { mutableStateOf<SUser?>(null) }
    var nameEdit by remember { mutableStateOf("") }
    var usernameEdit by remember { mutableStateOf("") }
    var statusEdit by remember { mutableStateOf("") }
    var profileMsg by remember { mutableStateOf<String?>(null) }
    var blockedNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var sendReceipts by remember { mutableStateOf(loadBoolPref("send_read_receipts", true)) }
    var showDeleted by remember { mutableStateOf(loadBoolPref("show_deleted", false)) }

    LaunchedEffect(myUid) {
        if (myUid != null) {
            val u0 = SharedRepo.getUser(myUid)?.data
            nameEdit = u0?.displayName ?: ""
            usernameEdit = u0?.username ?: ""
            statusEdit = u0?.status ?: ""
            SharedRepo.observeUser(myUid).collect { userData = it }
        }
    }
    LaunchedEffect(userData?.blockedUsers) {
        val ids = userData?.blockedUsers ?: emptyList()
        blockedNames = ids.associateWith { id ->
            SharedRepo.getUser(id)?.data?.let { it.displayName.ifBlank { it.username } } ?: id
        }
    }

    LaunchedEffect(myUid) {
        if (myUid != null) {
            val user = SharedRepo.getUser(myUid)
            email = user?.data?.email ?: ""
            displayName = user?.data?.displayName ?: ""
            photoUrl = user?.data?.photoUrl ?: ""
        }
    }

    // Своя аватарка — тот же Cloudinary-аплоад, что и для медиа в чате, результат пишется в
    // users/{uid}.photoUrl (то же поле читает и Android).
    fun changePhoto() {
        if (myUid == null || uploadingPhoto) return
        scope.launch {
            val picked = try { pickFile(MediaKind.IMAGE) } catch (e: Exception) { photoError = e.message; null } ?: return@launch
            uploadingPhoto = true
            photoError = null
            cloudinaryUpload(picked.bytes, picked.name, "image", picked.mimeType)
                .onSuccess { url -> SharedRepo.updateProfilePhoto(myUid, url); photoUrl = url }
                .onFailure { e -> photoError = e.message ?: "Не удалось загрузить фото" }
            uploadingPhoto = false
        }
    }

    Box(Modifier.fillMaxSize().background(SettingsBg), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("Настройки", color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            // Профиль — вся карточка кликабельна (смена фото).
            SettingsCard(Modifier.clip(RoundedCornerShape(16.dp)).clickable { changePhoto() }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Avatar(photoUrl = photoUrl, label = displayName.ifBlank { email }, size = 64.dp)
                        if (uploadingPhoto) {
                            Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)), Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(displayName.ifBlank { "Профиль" }, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(email.ifBlank { "—" }, color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Изменить фото", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            photoError?.let { Text(it, color = DangerColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp, start = 6.dp)) }

            SectionTitle("ОФОРМЛЕНИЕ")
            SettingsCard {
                Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Тёмная тема", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(if (isDark) "Включена" else "Выключена — светлая тема", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = isDark,
                        onCheckedChange = onToggleDark,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = Accent, checkedBorderColor = Color.Transparent,
                            uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFBFC3C9), uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Text("Акцентный цвет", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AccentOptions.forEach { option ->
                            val selected = option.key == accentKey
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape)
                                    .background(option.color)
                                    .then(if (selected) Modifier.border(2.dp, TextPrimary, CircleShape) else Modifier)
                                    .clickable { onSelectAccent(option.key) },
                                contentAlignment = Alignment.Center,
                            ) { if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }

            SectionTitle("ПРОФИЛЬ")
            SettingsCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(nameEdit, { nameEdit = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(usernameEdit, { usernameEdit = it }, label = { Text("@username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(statusEdit, { statusEdit = it }, label = { Text("Статус") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (myUid == null) return@Button
                            scope.launch {
                                profileMsg = null
                                SharedRepo.updateProfile(myUid, nameEdit, statusEdit)
                                displayName = nameEdit.trim()
                                val cleanNew = usernameEdit.removePrefix("@").lowercase().trim()
                                if (cleanNew != (userData?.username ?: "") && cleanNew.isNotEmpty()) {
                                    SharedRepo.updateUsername(myUid, cleanNew)
                                        .onSuccess { profileMsg = "Сохранено" }
                                        .onFailure { profileMsg = it.message ?: "Не удалось сменить @username" }
                                } else profileMsg = "Сохранено"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    ) { Text("Сохранить") }
                    profileMsg?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
                }
            }

            if (userData?.isPremium == true) {
                SectionTitle("PREMIUM")
                SettingsCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Цвет рамки аватара", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PREMIUM_FRAME_COLORS.forEach { (key, argb) ->
                                val selected = (userData?.premiumFrame ?: "gold") == key
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(argb))
                                        .then(if (selected) Modifier.border(2.dp, TextPrimary, CircleShape) else Modifier)
                                        .clickable { if (myUid != null) scope.launch { SharedRepo.setPremiumFrame(myUid, key) } },
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Показывать удалённые сообщения", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Действует после открытия чата заново", color = TextSecondary, fontSize = 12.sp)
                            }
                            Switch(checked = showDeleted, onCheckedChange = { showDeleted = it; saveBoolPref("show_deleted", it) })
                        }
                    }
                }
            }

            SectionTitle("КОНФИДЕНЦИАЛЬНОСТЬ")
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Отчёты о прочтении", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Собеседник видит ✓✓, когда вы прочитали сообщение", color = TextSecondary, fontSize = 12.sp)
                        }
                        Switch(checked = sendReceipts, onCheckedChange = { sendReceipts = it; saveBoolPref("send_read_receipts", it) })
                    }
                    if (blockedNames.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text("Заблокированные", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        blockedNames.forEach { (uid, name) ->
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, color = TextPrimary, modifier = Modifier.weight(1f))
                                TextButton(onClick = { if (myUid != null) scope.launch { SharedRepo.unblockUser(myUid, uid) } }) { Text("Разблокировать", color = Accent) }
                            }
                        }
                    }
                }
            }

            SectionTitle("ХРАНИЛИЩЕ МЕДИА")
            SettingsCard {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDone, null, tint = OnlineColor, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Хранилище медиа", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(mediaStorageLabel, color = TextSecondary, fontSize = 12.sp)
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = OnlineColor.copy(alpha = 0.18f)) {
                        Text("Подключено", color = OnlineColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            TextButton(
                onClick = { scope.launch { SharedRepo.signOut(); onSignedOut() } },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Выйти из аккаунта", color = DangerColor, fontWeight = FontWeight.SemiBold) }
        }
    }
}


@Composable
private fun ChatScreen(chatId: String, title: String, photoUrl: String, onClose: () -> Unit) {
    val myUid = SharedRepo.currentUid
    var chat by remember { mutableStateOf<SChat?>(null) }
    var me by remember { mutableStateOf<SUser?>(null) }
    var partner by remember { mutableStateOf<SUser?>(null) }
    var messages by remember { mutableStateOf(emptyList<SharedMessage>()) }
    var draft by remember { mutableStateOf("") }
    var showAttach by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SharedMessage?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var typingSent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val sendReceipts = remember { loadBoolPref("send_read_receipts", true) }
    val showDeletedPref = remember { loadBoolPref("show_deleted", false) }

    LaunchedEffect(chatId) { SharedRepo.observeChat(chatId).collect { chat = it } }
    LaunchedEffect(chatId) { SharedRepo.observeMessages(chatId).collect { messages = it } }
    LaunchedEffect(myUid) { if (myUid != null) SharedRepo.observeUser(myUid).collect { me = it } }

    val c = chat
    val partnerUid = if (c != null && c.type == SChatType.PRIVATE) c.memberIds.firstOrNull { it != myUid } else null
    LaunchedEffect(partnerUid) {
        partner = null
        if (partnerUid != null) SharedRepo.observeUser(partnerUid).collect { partner = it }
    }

    val iBlocked = partnerUid != null && me?.blockedUsers?.contains(partnerUid) == true
    val blockedByPartner = myUid != null && partner?.blockedUsers?.contains(myUid) == true
    val channelReadOnly = c != null && c.type == SChatType.CHANNEL && c.broadcastOnly && (myUid == null || !c.adminIds.contains(myUid))
    val canSeeDeleted = showDeletedPref && me?.isPremium == true
    val clearedSec = c?.clearedSecondsFor(myUid)
    val visible = remember(messages, clearedSec, canSeeDeleted) {
        messages.filter { m ->
            (canSeeDeleted || !m.data.isDeleted) &&
                (clearedSec == null || (tsSeconds(m.data.timestamp) ?: Long.MAX_VALUE) >= clearedSec)
        }
    }
    val pinned = c?.pinnedMessageId?.takeIf { it.isNotBlank() }?.let { pid -> visible.firstOrNull { it.id == pid } }
    val someoneTyping = c?.typingBy?.any { it != myUid } == true
    val partnerReadSec = if (partnerUid != null) tsSeconds(c?.lastReadAt?.get(partnerUid)) else null

    // Новые сообщения → прокрутка вниз; чат открыт → сбрасываем свой счётчик и (если не отключено) шлём ✓✓.
    LaunchedEffect(visible.size) {
        if (visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex)
    }
    LaunchedEffect(chatId, messages.size) {
        if (myUid != null) {
            SharedRepo.clearUnread(chatId, myUid)
            if (sendReceipts) SharedRepo.markChatRead(chatId, myUid)
        }
    }
    // "Печатает…": включаем при наборе, гасим через 3 с бездействия, при отправке и при закрытии чата.
    LaunchedEffect(draft) {
        if (myUid == null) return@LaunchedEffect
        if (draft.isNotEmpty() && editing == null) {
            if (!typingSent) { typingSent = true; SharedRepo.setTyping(chatId, myUid, true) }
            delay(3000)
        }
        if (typingSent) { typingSent = false; SharedRepo.setTyping(chatId, myUid, false) }
    }
    LaunchedEffect(chatId) {
        try { awaitCancellation() } finally {
            if (myUid != null) withContext(NonCancellable) { SharedRepo.setTyping(chatId, myUid, false) }
        }
    }

    fun uploadAndSend(kind: MediaKind) {
        showAttach = false
        if (myUid == null) return
        scope.launch {
            val picked = try { pickFile(kind) } catch (e: Exception) { error = e.message; null } ?: return@launch
            uploading = true
            error = null
            val resourceType = when (kind) { MediaKind.IMAGE -> "image"; MediaKind.VIDEO -> "video"; MediaKind.FILE -> "raw" }
            val messageType = when (kind) { MediaKind.IMAGE -> SMessageType.IMAGE; MediaKind.VIDEO -> SMessageType.VIDEO; MediaKind.FILE -> SMessageType.FILE }
            cloudinaryUpload(picked.bytes, picked.name, resourceType, picked.mimeType)
                .onSuccess { url ->
                    val name = SharedRepo.getUser(myUid)?.data?.displayName ?: ""
                    try { SharedRepo.sendMedia(chatId, messageType, myUid, name, url, picked.name, picked.size) }
                    catch (e: Exception) { error = e.message ?: "Не удалось отправить" }
                }
                .onFailure { e -> error = e.message ?: "Не удалось загрузить файл" }
            uploading = false
        }
    }

    fun sendDraft() {
        val text = draft.trim()
        if (text.isEmpty() || myUid == null) return
        val ed = editing
        draft = ""
        editing = null
        scope.launch {
            if (ed != null) {
                SharedRepo.editMessage(chatId, ed.id, text).onFailure { error = it.message ?: "Не удалось изменить" }
            } else {
                try {
                    val name = SharedRepo.getUser(myUid)?.data?.displayName ?: ""
                    SharedRepo.sendText(chatId, text, myUid, name)
                } catch (e: Exception) { error = e.message ?: "Не удалось отправить" }
            }
        }
    }

    fun sendSticker(s: String) {
        showStickers = false
        if (myUid == null) return
        scope.launch {
            try {
                val name = SharedRepo.getUser(myUid)?.data?.displayName ?: ""
                SharedRepo.sendSticker(chatId, s, myUid, name)
            } catch (e: Exception) { error = e.message ?: "Не удалось отправить" }
        }
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        Surface(color = BgSecondary, shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(photoUrl = photoUrl, label = title, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = when {
                        someoneTyping -> "печатает…"
                        c != null && c.type != SChatType.PRIVATE && c.type != SChatType.SAVED -> "${c.memberIds.size} участн."
                        partner?.isOnline == true -> "в сети"
                        else -> ""
                    }
                    if (sub.isNotEmpty()) Text(sub, color = if (someoneTyping) Accent else TextSecondary, fontSize = 12.sp)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Меню", tint = TextSecondary) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (c != null && c.type != SChatType.SAVED) {
                            val archivedNow = c.isArchivedFor(myUid)
                            DropdownMenuItem(
                                text = { Text(if (archivedNow) "Вернуть из архива" else "В архив") },
                                onClick = { showMenu = false; if (myUid != null) scope.launch { if (archivedNow) SharedRepo.unarchiveChat(chatId, myUid) else SharedRepo.archiveChat(chatId, myUid) } },
                            )
                        }
                        DropdownMenuItem(text = { Text("Очистить историю") }, onClick = { showMenu = false; confirm = "clear" })
                        if (c != null && c.type != SChatType.SAVED) {
                            DropdownMenuItem(text = { Text("Удалить чат", color = DangerColor) }, onClick = { showMenu = false; confirm = "delete" })
                        }
                        if (partnerUid != null) {
                            DropdownMenuItem(
                                text = { Text(if (iBlocked) "Разблокировать" else "Заблокировать", color = if (iBlocked) TextPrimary else DangerColor) },
                                onClick = {
                                    showMenu = false
                                    if (iBlocked) { if (myUid != null) scope.launch { SharedRepo.unblockUser(myUid, partnerUid) } } else confirm = "block"
                                },
                            )
                        }
                    }
                }
            }
        }

        if (pinned != null) {
            Surface(color = BgSecondary.copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PushPin, null, tint = Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        pinned.data.text.ifBlank { "Вложение" }, color = TextPrimary, fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clickable { scope.launch { listState.scrollToItem(visible.indexOf(pinned).coerceAtLeast(0)) } },
                    )
                    IconButton(onClick = { scope.launch { SharedRepo.pinMessage(chatId, "") } }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, "Открепить", tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (error != null) {
            Surface(color = DangerColor.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error ?: "", color = DangerColor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { error = null }) { Text("✕", color = DangerColor) }
                }
            }
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            items(visible, key = { it.id }) { msg ->
                val mine = msg.data.senderId == myUid
                val ticks = if (mine && partnerUid != null) {
                    val ms = tsSeconds(msg.data.timestamp)
                    if (ms != null && partnerReadSec != null && ms <= partnerReadSec) "✓✓" else "✓"
                } else ""
                MessageRow(
                    msg = msg, mine = mine, ticks = ticks, isPinned = msg.id == c?.pinnedMessageId, scope = scope,
                    onEdit = { editing = msg; draft = msg.data.text },
                    onDelete = { scope.launch { SharedRepo.deleteMessage(chatId, msg.id) } },
                    onPin = { scope.launch { SharedRepo.pinMessage(chatId, if (msg.id == c?.pinnedMessageId) "" else msg.id) } },
                    onCopy = { clipboard.setText(AnnotatedString(msg.data.text)) },
                )
            }
        }

        if (showStickers) {
            Surface(color = BgSecondary) {
                LazyVerticalGrid(columns = GridCells.Adaptive(46.dp), modifier = Modifier.fillMaxWidth().height(190.dp).padding(8.dp)) {
                    gridItems(CHAT_STICKERS) { s ->
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).clickable { sendSticker(s) }, contentAlignment = Alignment.Center) {
                            Text(s, fontSize = 26.sp)
                        }
                    }
                }
            }
        }

        val blockNote = when {
            iBlocked -> "Вы заблокировали этого пользователя"
            blockedByPartner -> "Этот пользователь ограничил вам переписку"
            channelReadOnly -> "В этом канале писать могут только администраторы"
            else -> null
        }
        Surface(color = BgSecondary) {
            if (blockNote != null) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(blockNote, color = TextSecondary, modifier = Modifier.weight(1f))
                    if (iBlocked && partnerUid != null && myUid != null) {
                        TextButton(onClick = { scope.launch { SharedRepo.unblockUser(myUid, partnerUid) } }) { Text("Разблокировать", color = Accent) }
                    }
                }
            } else {
                Column {
                    if (editing != null) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, null, tint = Accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Редактирование", color = Accent, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { editing = null; draft = "" }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Отмена", tint = TextSecondary, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { showAttach = !showAttach }, enabled = !uploading) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Прикрепить", tint = if (showAttach) Accent else TextSecondary)
                            }
                            DropdownMenu(expanded = showAttach, onDismissRequest = { showAttach = false }) {
                                DropdownMenuItem(text = { Text("Фото") }, leadingIcon = { Icon(Icons.Default.Image, null) }, onClick = { uploadAndSend(MediaKind.IMAGE) })
                                DropdownMenuItem(text = { Text("Видео") }, leadingIcon = { Icon(Icons.Default.Movie, null) }, onClick = { uploadAndSend(MediaKind.VIDEO) })
                                DropdownMenuItem(text = { Text("Файл") }, leadingIcon = { Icon(Icons.Default.Description, null) }, onClick = { uploadAndSend(MediaKind.FILE) })
                            }
                        }
                        IconButton(onClick = { showStickers = !showStickers }) {
                            Icon(Icons.Default.EmojiEmotions, "Стикеры", tint = if (showStickers) Accent else TextSecondary)
                        }
                        if (uploading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Сообщение", color = TextSecondary) },
                            singleLine = true,
                            shape = RoundedCornerShape(22.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { sendDraft() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = HoverBg, unfocusedContainerColor = HoverBg,
                                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { sendDraft() }, shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                            Text(if (editing != null) "Сохранить" else "Отправить")
                        }
                    }
                }
            }
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(when (action) { "clear" -> "Очистить историю?"; "delete" -> "Удалить чат?"; else -> "Заблокировать?" }) },
            text = { Text(if (action == "block") "Пользователь не сможет вам писать и звонить, пока вы его не разблокируете." else "Это действие только для вас — у собеседника сообщения останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (myUid != null) scope.launch {
                        when (action) {
                            "clear" -> SharedRepo.clearChatHistory(chatId, myUid)
                            "delete" -> { SharedRepo.deleteChatForMe(chatId, myUid); onClose() }
                            "block" -> if (partnerUid != null) SharedRepo.blockUser(myUid, partnerUid)
                        }
                    }
                }) { Text("Да", color = DangerColor) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
        )
    }
}

// Одна строка сообщения: пузырь (или стикер), время, ✓/✓✓, «изменено» и меню ⋮ при наведении.
@Composable
private fun MessageRow(
    msg: SharedMessage, mine: Boolean, ticks: String, isPinned: Boolean, scope: kotlinx.coroutines.CoroutineScope,
    onEdit: () -> Unit, onDelete: () -> Unit, onPin: () -> Unit, onCopy: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(false) }
    val d = msg.data
    val secs = tsSeconds(d.timestamp)
    val time = if (secs != null) formatChatTime(secs) else ""
    val fg = if (mine) Color.White else TextPrimary
    val meta = buildString {
        if (d.isEdited) append("изменено  ")
        append(time)
        if (ticks.isNotEmpty()) append("  ").append(ticks)
    }
    val menuButton: @Composable () -> Unit = {
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.MoreVert, "Меню", tint = if (hovered || menu) TextSecondary else Color.Transparent, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (d.type == SMessageType.TEXT && !d.isDeleted) DropdownMenuItem(text = { Text("Копировать") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menu = false; onCopy() })
                if (mine && d.type == SMessageType.TEXT && !d.isDeleted) DropdownMenuItem(text = { Text("Редактировать") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                if (!d.isDeleted) DropdownMenuItem(text = { Text(if (isPinned) "Открепить" else "Закрепить") }, leadingIcon = { Icon(Icons.Default.PushPin, null) }, onClick = { menu = false; onPin() })
                if (mine && !d.isDeleted) DropdownMenuItem(text = { Text("Удалить", color = DangerColor) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = DangerColor) }, onClick = { menu = false; onDelete() })
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).hoverable(interaction),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mine) menuButton()
        if (d.type == SMessageType.STICKER && d.sticker.isNotBlank() && !d.isDeleted) {
            Column(horizontalAlignment = Alignment.End) {
                Text(d.sticker, fontSize = 40.sp)
                Text(meta, color = TextSecondary, fontSize = 10.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .background(
                        if (mine) Accent else BubbleIn,
                        shape = RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (mine) 16.dp else 4.dp,
                            bottomEnd = if (mine) 4.dp else 16.dp,
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .widthIn(max = 460.dp),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    if (d.isDeleted) {
                        Text("удалено: " + d.text.ifBlank { "[вложение]" }, color = fg.copy(alpha = 0.6f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    } else {
                        MessageContent(msg, scope, fg = fg)
                    }
                    Text(meta, color = fg.copy(alpha = 0.7f), fontSize = 10.sp)
                }
            }
        }
        if (!mine) menuButton()
    }
}

// Содержимое одного пузыря — вынесено из ChatScreen, чтобы код отправки/приёма и код
// разбора типа сообщения не перепутывались в одной гигантской функции.
@Composable
private fun MessageContent(msg: SharedMessage, scope: kotlinx.coroutines.CoroutineScope, fg: Color) {
    when (msg.data.type) {
        SMessageType.TEXT -> Text(msg.data.text, color = fg)
        SMessageType.IMAGE, SMessageType.GIF -> ChatImage(msg.data.mediaUrl, msg.id)
        SMessageType.VIDEO -> MediaDownloadRow(
            icon = Icons.Default.Movie,
            actionIcon = Icons.Default.PlayArrow,
            label = "Видео",
            url = msg.data.mediaUrl,
            fileName = "video_${msg.id}.mp4",
            scope = scope,
            fg = fg,
        )
        SMessageType.VOICE -> MediaDownloadRow(
            icon = Icons.Default.PlayCircle,
            actionIcon = Icons.Default.PlayArrow,
            label = "Голосовое",
            url = msg.data.mediaUrl,
            fileName = "voice_${msg.id}.m4a",
            scope = scope,
            fg = fg,
        )
        SMessageType.VIDEO_NOTE -> MediaDownloadRow(
            icon = Icons.Default.PlayCircle,
            actionIcon = Icons.Default.PlayArrow,
            label = "Видео-кружок",
            url = msg.data.mediaUrl,
            fileName = "video_note_${msg.id}.mp4",
            scope = scope,
            fg = fg,
        )
        SMessageType.FILE -> MediaDownloadRow(
            icon = Icons.Default.Description,
            actionIcon = Icons.Default.Download,
            label = msg.data.fileName.ifBlank { "Файл" },
            url = msg.data.mediaUrl,
            fileName = msg.data.fileName.ifBlank { "file_${msg.id}" },
            scope = scope,
            fg = fg,
        )
        SMessageType.STICKER -> if (msg.data.mediaUrl.isNotBlank()) ChatImage(msg.data.mediaUrl, msg.id, size = 130.dp)
            else Text("[Стикер] — доступно в мобильном приложении", color = fg)
    }
}

// Фото/GIF/картиночный стикер — скачивает байты по Cloudinary-ссылке и рисует как
// Image(...); без mediaUrl показать нечего — это либо совсем старое тестовое сообщение
// из прошлых версий приложения (до перехода на Cloudinary), либо десктоп-клиент, в
// отличие от Android, не хранит Base64-запасной путь. Тапом по неудавшейся картинке
// можно попробовать перезагрузить — на случай, если проблема была в сети, а не в ссылке.
@Composable
private fun ChatImage(mediaUrl: String, key: String, size: androidx.compose.ui.unit.Dp = 220.dp) {
    var bitmap by remember(key) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var state by remember(key) { mutableStateOf(if (mediaUrl.isBlank()) "no_url" else "loading") }
    var reloadTick by remember(key) { mutableStateOf(0) }
    LaunchedEffect(mediaUrl, reloadTick) {
        if (mediaUrl.isBlank()) { state = "no_url"; return@LaunchedEffect }
        state = "loading"
        fetchBytes(mediaUrl)
            .onSuccess { bytes ->
                val decoded = decodeImageBitmap(bytes)
                if (decoded != null) { bitmap = decoded; state = "ok" } else state = "decode_error"
            }
            .onFailure { state = "network_error" }
    }
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(BubbleIn.copy(alpha = 0.6f))
            .let { if (state != "ok" && state != "loading") it.clickable { reloadTick++ } else it },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            "ok" -> androidx.compose.foundation.Image(bitmap!!, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            "loading" -> CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Accent, strokeWidth = 2.dp)
            "no_url" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Image, null, tint = TextSecondary)
                Text("старое сообщение", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Image, null, tint = TextSecondary)
                Text("нажмите, чтобы повторить", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// Видео/файл — на десктопе не проигрываем инлайн (лишняя зависимость ради одной кнопки),
// просто скачиваем в "Загрузки" и сразу открываем системным приложением по умолчанию
// (аналог MediaSaver.saveAndOpenFromUrl на Android).
@Composable
private fun MediaDownloadRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, url: String, fileName: String, scope: kotlinx.coroutines.CoroutineScope, actionIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Download, fg: Color = Color.White) {
    var downloading by remember(url) { mutableStateOf(false) }
    var failedMsg by remember(url) { mutableStateOf<String?>(null) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = fg, maxLines = 1)
            Spacer(Modifier.width(10.dp))
            if (url.isBlank()) {
                Text("старое сообщение", color = fg.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            } else if (downloading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = fg, strokeWidth = 2.dp)
            } else {
                // Проигрывателя прямо в чате пока нет (нужна отдельная нативная зависимость,
                // рискованно добавлять вслепую без возможности тут же собрать и проверить) —
                // "проиграть"/"открыть" на деле скачивает файл и сразу открывает его системным
                // плеером/просмотрщиком по умолчанию (см. downloadAndOpen).
                IconButton(onClick = {
                    downloading = true
                    failedMsg = null
                    scope.launch {
                        downloadAndOpen(url, fileName)
                            .onFailure { e -> failedMsg = e.message ?: "Не удалось открыть" }
                        downloading = false
                    }
                }, modifier = Modifier.size(28.dp)) {
                    Icon(actionIcon, contentDescription = "Открыть", tint = fg)
                }
            }
        }
        failedMsg?.let { Text(it, color = fg.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall) }
    }
}
