package com.rosechat.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rosechat.data.local.NotificationsState
import com.rosechat.data.model.PROFILE_STICKERS
import com.rosechat.data.model.User
import com.rosechat.data.repository.CloudinaryRepository
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.ui.auth.CreditsFooter
import com.rosechat.ui.auth.PingField
import com.rosechat.ui.common.SmartImage
import com.rosechat.ui.common.PremiumFrameGlow
import com.rosechat.ui.common.premiumBadgeEmoji
import com.rosechat.ui.common.premiumFrameColor
import com.rosechat.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val _user     = MutableStateFlow<User?>(null)
    val user = _user.asStateFlow()
    private val _name     = MutableStateFlow("")
    val name = _name.asStateFlow()
    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()
    private val _status   = MutableStateFlow("")
    val status = _status.asStateFlow()
    private val _saved    = MutableStateFlow(false)
    val saved = _saved.asStateFlow()
    private val _loading  = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error    = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _pwResetSent = MutableStateFlow(false)
    val pwResetSent = _pwResetSent.asStateFlow()

    // Пока это false — сохранять нельзя. Если нажать Save до того, как имя/статус
    // подтянулись из Firestore (поля ещё ""), save() запишет в базу пустые строки
    // поверх настоящих данных — отсюда защита.
    private val _loaded   = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()

    init {
        viewModelScope.launch {
            val uid = FirebaseRepo.currentUid ?: return@launch
            FirebaseRepo.observeUser(uid).collect { u ->
                _user.value = u
                if (!_loaded.value) {
                    _name.value = u?.displayName ?: ""; _username.value = u?.username ?: ""; _status.value = u?.status ?: ""
                    _loaded.value = true
                }
            }
        }
    }

    fun onNameChanged(v: String)     = _name.update { v }
    fun onUsernameChanged(v: String) = _username.update { v.filter { c -> c.isLetterOrDigit() || c == '_' }.take(20) }
    fun onStatusChanged(v: String)   = _status.update { v }
    fun clearError()                 = _error.update { null }

    fun setSticker(s: String) {
        val uid = FirebaseRepo.currentUid ?: return
        viewModelScope.launch {
            FirebaseRepo.updateUser(uid, mapOf("profileSticker" to s))
            _user.update { it?.copy(profileSticker = s) }
        }
    }

    fun save() {
        if (!_loaded.value) return  // защита от гонки — см. комментарий у _loaded
        val uid = FirebaseRepo.currentUid ?: return
        viewModelScope.launch {
            _loading.value = true; _error.value = null
            try {
                val cur = _user.value?.username ?: ""
                val new = _username.value
                if (new != cur && new.length >= 3) {
                    FirebaseRepo.updateUsername(uid, new)
                        .onFailure { e -> _error.value = e.message; _loading.value = false; return@launch }
                }
                FirebaseRepo.updateUser(uid, mapOf("displayName" to _name.value, "status" to _status.value))
                _user.value = FirebaseRepo.getUser(uid)
                _saved.value = true; delay(2000); _saved.value = false
            } catch (e: Exception) { _error.value = e.message }
            finally { _loading.value = false }
        }
    }

    fun uploadAvatar(context: Context, uri: Uri) {
        _loading.value = true; _error.value = null
        val uid = FirebaseRepo.currentUid ?: return
        viewModelScope.launch {
            FirebaseRepo.uploadAvatar(context, uid, uri).onFailure { e -> _error.value = "Не удалось загрузить фото: ${e.message}" }
            _loading.value = false
        }
    }

    fun uploadBackground(context: Context, uri: Uri) {
        _loading.value = true; _error.value = null
        val uid = FirebaseRepo.currentUid ?: return
        viewModelScope.launch {
            FirebaseRepo.uploadChatBackground(context, uid, uri).onFailure { e -> _error.value = "Не удалось загрузить фон: ${e.message}" }
            _loading.value = false
        }
    }

    // Раньше карточка "Фон чата" умела только ставить новое фото — убрать уже выбранный фон
    // было нечем (см. чат: "можно было убрать фон обоев").
    fun clearBackground() {
        val uid = FirebaseRepo.currentUid ?: return
        viewModelScope.launch { FirebaseRepo.clearChatBackground(uid).onFailure { e -> _error.value = "Не удалось убрать фон: ${e.message}" } }
    }

    // Встроенные градиенты (CHAT_WALLPAPER_PRESETS) — быстрый выбор без загрузки своей фотки,
    // см. чат: "свои обои чатов ... чтобы их можно было выбрать".
    fun setWallpaperPreset(id: String) {
        val uid = FirebaseRepo.currentUid ?: return
        viewModelScope.launch { FirebaseRepo.setChatWallpaperPreset(uid, id).onFailure { e -> _error.value = "Не удалось применить фон: ${e.message}" } }
    }

    fun sendPasswordReset() {
        val email = _user.value?.email ?: return
        if (email.isBlank()) return
        viewModelScope.launch {
            FirebaseRepo.resetPassword(email)
                .onSuccess { _pwResetSent.value = true; delay(4000); _pwResetSent.value = false }
                .onFailure { e -> _error.value = e.message }
        }
    }

    fun signOut() { FirebaseRepo.signOut() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSignOut: () -> Unit, onOpenPremium: () -> Unit, onOpenChatSettings: () -> Unit, onOpenDeveloper: () -> Unit = {}, vm: SettingsViewModel = viewModel()) {
    val user       by vm.user.collectAsStateWithLifecycle()
    val name       by vm.name.collectAsStateWithLifecycle()
    val username   by vm.username.collectAsStateWithLifecycle()
    val status     by vm.status.collectAsStateWithLifecycle()
    val saved      by vm.saved.collectAsStateWithLifecycle()
    val loading    by vm.loading.collectAsStateWithLifecycle()
    val error      by vm.error.collectAsStateWithLifecycle()
    val pwResetSent by vm.pwResetSent.collectAsStateWithLifecycle()
    var showStickerPicker by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.uploadAvatar(ctx, it) } }
    val bgPicker     = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { vm.uploadBackground(ctx, it) } }

    val versionName = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { null } ?: "1.0.0.1"
    }

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            TopAppBar(
                title = { Text("Настройки", color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgSecondary)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {

            // ── Профиль ────────────────────────────────────────
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    if (user?.isPremium == true) PremiumFrameGlow(user?.premiumFrame ?: "gold", avatarSize = 88.dp)
                    Box(
                        Modifier.size(88.dp).clip(CircleShape)
                            .then(if (user?.isPremium == true) Modifier.border(2.5.dp, premiumFrameColor(user?.premiumFrame ?: "gold"), CircleShape) else Modifier)
                            .padding(if (user?.isPremium == true) 3.dp else 0.dp)
                            .clip(CircleShape).background(AccentMuted).clickable { avatarPicker.launch("image/*") },
                        Alignment.Center
                    ) {
                        if (user?.photoUrl?.isNotEmpty() == true || user?.photoData?.isNotEmpty() == true)
                            SmartImage(user?.photoUrl ?: "", user?.photoData ?: "", Modifier.fillMaxSize(), ContentScale.Crop)
                        else Text(name.firstOrNull()?.toString() ?: "?", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        if (loading) Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.5.dp)
                        }
                    }
                    // Камера — загрузить фото
                    Box(Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(Accent).clickable { avatarPicker.launch("image/*") }, Alignment.Center) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    // Стикер на аватар — доступно всем, не только Premium
                    Box(
                        Modifier.align(Alignment.BottomStart).size(26.dp).clip(CircleShape)
                            .background(BgSecondary).border(1.dp, BgTertiary, CircleShape)
                            .clickable { showStickerPicker = !showStickerPicker },
                        Alignment.Center
                    ) {
                        if (user?.profileSticker?.isNotEmpty() == true) Text(user!!.profileSticker, fontSize = 14.sp)
                        else Icon(Icons.Default.EmojiEmotions, null, tint = Accent, modifier = Modifier.size(14.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(name.ifEmpty { "Имя" }, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    if (user?.isPremium == true && user?.showPremiumBadge != false) { Spacer(Modifier.width(6.dp)); Text(premiumBadgeEmoji(user?.premiumBadgeIcon ?: "star"), fontSize = 18.sp) }
                }

                if (user?.isPremium == true) {
                    Spacer(Modifier.height(4.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = Gold.copy(0.2f)) {
                        Text("${premiumBadgeEmoji(user?.premiumBadgeIcon ?: "star")} Premium", style = MaterialTheme.typography.labelMedium, color = Gold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }

                if (showStickerPicker) {
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgTertiary)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Стикер для аватара", style = MaterialTheme.typography.labelMedium, color = TextSecondary, modifier = Modifier.weight(1f))
                                if (user?.profileSticker?.isNotEmpty() == true) {
                                    Text("Убрать", style = MaterialTheme.typography.labelMedium, color = ErrorRed,
                                        modifier = Modifier.clickable { vm.setSticker(""); showStickerPicker = false })
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PROFILE_STICKERS.take(8).forEach { s ->
                                    Text(s, fontSize = 26.sp, modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (user?.profileSticker == s) Accent.copy(0.2f) else Color.Transparent)
                                        .clickable { vm.setSticker(s); showStickerPicker = false }
                                        .padding(6.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PROFILE_STICKERS.drop(8).forEach { s ->
                                    Text(s, fontSize = 26.sp, modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (user?.profileSticker == s) Accent.copy(0.2f) else Color.Transparent)
                                        .clickable { vm.setSticker(s); showStickerPicker = false }
                                        .padding(6.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(0.15f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = ErrorRed, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(error!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton({ vm.clearError() }, modifier = Modifier.size(20.dp)) { Icon(Icons.Default.Close, null, tint = ErrorRed, modifier = Modifier.size(14.dp)) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            PingField(name, { vm.onNameChanged(it) }, "Имя", leadingIcon = { Icon(Icons.Default.Person, null, tint = Accent) })
            Spacer(Modifier.height(12.dp))
            PingField(username, { vm.onUsernameChanged(it) }, "@username", "мой_ник", leadingIcon = { Icon(Icons.Default.AlternateEmail, null, tint = Accent) })
            Spacer(Modifier.height(12.dp))
            PingField(status, { vm.onStatusChanged(it) }, "Статус", "О себе...", leadingIcon = { Icon(Icons.Default.Info, null, tint = Accent) })
            Spacer(Modifier.height(14.dp))

            Button(
                onClick = { vm.save() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White, disabledContainerColor = AccentMuted.copy(0.4f))
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (saved) "Сохранено ✓" else "Сохранить", style = MaterialTheme.typography.titleMedium)
            }

            SectionLabel("Premium")

            Card(Modifier.fillMaxWidth().clickable { onOpenPremium() }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Gold.copy(0.18f)), Alignment.Center) {
                        Text("⭐", fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PingGram Premium", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(if (user?.isPremium == true) "У тебя есть Premium" else "Посмотреть возможности", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
                }
            }

            SectionLabel("Чаты")

            Card(Modifier.fillMaxWidth().clickable { onOpenChatSettings() }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(BgTertiary), Alignment.Center) {
                        Icon(Icons.Default.Forum, null, tint = Accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Настройки чатов", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text("Отправка по Enter, размер текста", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
                }
            }

            SectionLabel("Внешний вид")

            SettingsCard(
                icon = if (ThemeState.isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                title = "Тёмная тема",
                subtitle = if (ThemeState.isDark) "Включена" else "Выключена"
            ) {
                Switch(
                    checked = ThemeState.isDark,
                    onCheckedChange = { ThemeState.setDark(ctx, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Раньше единственным цветом темы был розовый — просили "изменение цвета не только
            // чёрно-розовый, но и бело-чёрный и так далее". Пять пресетов, влияют на акцент
            // (кнопки, вкладки, свои сообщения по умолчанию) по всему приложению.
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Цвет темы", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        com.rosechat.ui.theme.AccentTheme.entries.forEach { t ->
                            val selected = ThemeState.accentTheme == t
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(t.accent)
                                    .then(if (selected) Modifier.border(2.dp, TextPrimary, CircleShape) else Modifier)
                                    .clickable { ThemeState.setAccent(ctx, t) },
                                Alignment.Center
                            ) { if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            val hasWallpaper = com.rosechat.ui.common.hasWallpaper(user?.chatWallpaperPreset ?: "", user?.chatBackground ?: "", user?.chatBackgroundData ?: "")
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { bgPicker.launch("image/*") }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(BgTertiary), Alignment.Center) {
                            if (hasWallpaper) com.rosechat.ui.common.ChatWallpaperView(user?.chatWallpaperPreset ?: "", user?.chatBackground ?: "", user?.chatBackgroundData ?: "", Modifier.fillMaxSize())
                            else Icon(Icons.Default.Wallpaper, null, tint = Accent, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Фон чата", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Своя фотография", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        if (hasWallpaper) {
                            IconButton({ vm.clearBackground() }) { Icon(Icons.Default.Close, "Убрать фон", tint = TextSecondary) }
                        } else {
                            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Или готовый вариант", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    // Пресетов стало больше (добавились "Каракули" и "Горы") — на узких экранах
                    // ряд уже не помещается по ширине без скролла, оборачиваем в horizontalScroll.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        com.rosechat.ui.common.CHAT_WALLPAPER_PRESETS.forEach { (id, brush) ->
                            val selected = user?.chatWallpaperPreset == id
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(brush)
                                    .then(if (selected) Modifier.border(2.dp, TextPrimary, RoundedCornerShape(10.dp)) else Modifier)
                                    .clickable { vm.setWallpaperPreset(id) },
                                Alignment.Center
                            ) { if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }

            SectionLabel("Уведомления")

            SettingsCard(
                icon = if (NotificationsState.enabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                title = "Уведомления о сообщениях",
                subtitle = if (NotificationsState.enabled) "Включены" else "Выключены"
            ) {
                Switch(
                    checked = NotificationsState.enabled,
                    onCheckedChange = { NotificationsState.setEnabled(ctx, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent)
                )
            }

            // Самодиагностика уведомлений. Читаем isForeground ради перерисовки: когда человек
            // возвращается из системных настроек, приложение снова "выходит на передний план".
            val appVisible = com.rosechat.data.local.InAppNotifier.isForeground
            val systemNotificationsOn = remember(appVisible) {
                androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
            }
            if (!systemNotificationsOn) {
                Spacer(Modifier.height(8.dp))
                SettingsCard(
                    icon = Icons.Default.NotificationsOff,
                    title = "Системные уведомления выключены",
                    subtitle = "Из-за этого пуши и звонки не появляются при закрытом приложении"
                ) {
                    TextButton(onClick = {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("Открыть", color = Accent) }
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsCard(
                icon = Icons.Default.Notifications,
                title = "Работа в фоне",
                subtitle = "Если пуши не приходят при закрытом приложении, отключите ограничения батареи (на Xiaomi/Huawei ещё и включите автозапуск)"
            ) {
                TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Открыть", color = Accent) }
            }

            SectionLabel("Хранилище")

            // Показываем, куда реально уходят фото/видео/файлы — раньше это было видно только
            // из local.properties разработчика, пользователь не мог проверить сам. isConfigured
            // здесь означает "ключи Cloudinary прописаны в сборке", не "сервер сейчас отвечает" —
            // сетевой пинг сюда специально не добавлен, чтобы карточка не мигала при слабом интернете.
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (CloudinaryRepository.isConfigured) Online.copy(0.15f) else BgTertiary),
                        Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudDone, null, tint = if (CloudinaryRepository.isConfigured) Online else TextSecondary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Хранилище медиа", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            if (CloudinaryRepository.isConfigured) "Cloudinary · ${CloudinaryRepository.cloudNameForDisplay}"
                            else "Резервный режим (без Cloudinary)",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (CloudinaryRepository.isConfigured) Online.copy(0.18f) else ErrorRed.copy(0.15f)
                    ) {
                        Text(
                            if (CloudinaryRepository.isConfigured) "Подключено" else "Не настроено",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (CloudinaryRepository.isConfigured) Online else ErrorRed,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            SectionLabel("Безопасность")

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(BgTertiary), Alignment.Center) {
                            Icon(Icons.Default.Shield, null, tint = Accent, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Способ входа", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                when {
                                    user?.email?.isNotEmpty() == true -> maskEmail(user!!.email)
                                    user?.phone?.isNotEmpty() == true -> maskPhone(user!!.phone)
                                    else -> "—"
                                },
                                style = MaterialTheme.typography.bodySmall, color = TextSecondary
                            )
                        }
                    }

                    if (user?.email?.isNotEmpty() == true) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = Divider)
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.sendPasswordReset() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Password, null, tint = Accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Изменить пароль", style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
                        }
                        if (pwResetSent) {
                            Spacer(Modifier.height(8.dp))
                            Text("Письмо для смены пароля отправлено на ${user!!.email}", style = MaterialTheme.typography.bodySmall, color = Online)
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text("Вход защищён SMS-кодом на номер телефона, пароль не требуется.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            OutlinedButton({ vm.signOut(); onSignOut() }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed)) {
                Icon(Icons.Default.Logout, null, tint = ErrorRed); Spacer(Modifier.width(8.dp)); Text("Выйти из аккаунта", color = ErrorRed)
            }

            SectionLabel("Инфо")

            Card(Modifier.fillMaxWidth().clickable { showPrivacy = true }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PrivacyTip, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Политика конфиденциальности", style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(28.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PingGram · версия $versionName", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                Spacer(Modifier.height(8.dp))
                CreditsFooter(onOpenDeveloper)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            containerColor = BgSecondary,
            title = { Text("Политика конфиденциальности", color = TextPrimary) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(PRIVACY_POLICY_TEXT, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton({ showPrivacy = false }) { Text("Понятно", color = Accent) } }
        )
    }
}

// Черновик политики конфиденциальности. Как и с пользовательским соглашением —
// стоит вычитать перед публикацией, особенно если ориентируешься на 152-ФЗ (Россия) или GDPR.
private const val PRIVACY_POLICY_TEXT = """1. КАКИЕ ДАННЫЕ МЫ СОБИРАЕМ
Имя, @username, email или номер телефона, фото профиля (если загружено), статус, а также содержимое переписки (текст, стикеры, голосовые, файлы) — всё, что нужно для работы мессенджера.

2. ДЛЯ ЧЕГО МЫ ИХ ИСПОЛЬЗУЕМ
Исключительно для работы функций приложения: доставки сообщений, звонков, поиска пользователей по имени/username, push-уведомлений.

3. ГДЕ ХРАНЯТСЯ ДАННЫЕ
На серверах Firebase (Google Cloud). Голосовые сообщения временно хранятся прямо в базе данных сообщений (Firestore) в зашифрованном канале передачи — как только подключим Firebase Storage, они переедут туда отдельным файлом.

4. С КЕМ МЫ ДЕЛИМСЯ ДАННЫМИ
Мы не продаём и не передаём твои данные рекламным сетям или третьим лицам. Технически данные обрабатываются инфраструктурой Google (Firebase) как оператором хранения.

5. ТВОИ ПРАВА
Ты можешь в любой момент изменить или удалить данные профиля в настройках, а также запросить полное удаление аккаунта через поддержку приложения.

6. БЕЗОПАСНОСТЬ
Доступ к переписке защищён правилами Firestore — прочитать сообщения чата может только его участник. Мы не гарантируем абсолютную защиту от любых видов атак, но применяем разумные технические меры.

7. ИЗМЕНЕНИЯ ПОЛИТИКИ
Мы можем обновлять этот документ. Актуальная версия всегда доступна в Настройках приложения."""

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = TextTertiary,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(BgTertiary), Alignment.Center) {
                Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            trailing()
        }
    }
}

private fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at <= 1) return email
    return email.take(2) + "•••" + email.substring(at)
}

private fun maskPhone(phone: String): String {
    if (phone.length < 4) return phone
    val head = phone.dropLast(4).map { if (it.isDigit()) '•' else it }.joinToString("")
    return head + phone.takeLast(4)
}
