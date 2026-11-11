@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.rosechat.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
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
import com.rosechat.shared.data.cancelVoiceRecording
import com.rosechat.shared.data.recordVideoNote
import com.rosechat.shared.data.videoNoteSupported
import com.rosechat.shared.data.startVoiceRecording
import com.rosechat.shared.data.stopVoiceRecording
import com.rosechat.shared.data.voiceRecordingSupported
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
import com.rosechat.shared.data.formatLastSeen
import com.rosechat.shared.data.loadStringPref
import com.rosechat.shared.data.saveStringPref
import com.rosechat.shared.data.sendPasswordReset
import com.rosechat.shared.data.GifAnimation
import com.rosechat.shared.data.localSetting
import com.rosechat.shared.data.loadWallpaperResource
import androidx.compose.material.icons.filled.Code
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.rosechat.shared.data.nowEpochSeconds
import com.rosechat.shared.model.SharedStory
import androidx.compose.foundation.lazy.LazyRow
import kotlinx.coroutines.flow.catch
import com.rosechat.shared.data.GifRepository
import com.rosechat.shared.data.decodeGifAnimation
import com.rosechat.shared.data.loadStickerResource
import com.rosechat.shared.model.IMAGE_STICKER_NAMES
import com.rosechat.shared.model.PREMIUM_BUBBLE_COLORS
import androidx.compose.material.icons.filled.Gif
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.rosechat.shared.model.PREMIUM_BADGE_EMOJI
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.rosechat.shared.model.CHAT_STICKERS
import com.rosechat.shared.model.PREMIUM_FRAME_COLORS
import com.rosechat.shared.model.SChat
import com.rosechat.shared.model.SUser
import com.rosechat.shared.model.SCall
import com.rosechat.shared.model.SCallStatus
import com.rosechat.shared.model.SCallType
import com.rosechat.shared.model.SharedCall
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import com.rosechat.shared.data.CallVideoSurface
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.rosechat.shared.data.CallCenter
import com.rosechat.shared.data.CallController
import com.rosechat.shared.data.CallPhase
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
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

private enum class Section { Chats, Archive, Calls, Settings }

// Размер текста в чате (как ChatTextSize на Android: 0.88 / 1.0 / 1.18).
private val TextSizeOptions = listOf("SMALL" to "Мелкий", "MEDIUM" to "Обычный", "LARGE" to "Крупный")
// ── Обои чата (те же id, что CHAT_WALLPAPER_PRESETS на Android) ─────────────────────────────
private val WALLPAPER_PRESETS = listOf(
    "sunset" to "Закат", "midnight" to "Полночь", "ocean" to "Океан", "forest" to "Лес", "violet" to "Аметист",
    "mono" to "Монохром", "pattern" to "Узор", "doodle" to "Каракули", "mountain" to "Горы",
)

private val WALLPAPER_GRADIENTS: Map<String, List<Color>> = mapOf(
    "sunset" to listOf(Color(0xFFFF6B9D), Color(0xFFFF2D78), Color(0xFF6B1A4A)),
    "midnight" to listOf(Color(0xFF1F2A44), Color(0xFF0F0F0F)),
    "ocean" to listOf(Color(0xFF0A4A8B), Color(0xFF0A84FF), Color(0xFF0F0F0F)),
    "forest" to listOf(Color(0xFF1B6B33), Color(0xFF0F0F0F)),
    "violet" to listOf(Color(0xFF6B2C8B), Color(0xFFBF5AF2), Color(0xFF1C1C1E)),
    "mono" to listOf(Color(0xFF3A3A3C), Color(0xFF0F0F0F)),
)

// Рисует обои позади содержимого: градиент, узор из точек, «каракули» или фото (cover-заливка).
// Для "mountain" и своего фото нужен готовый ImageBitmap (его загружает вызывающий код).
private fun Modifier.chatWallpaper(preset: String, photo: ImageBitmap?): Modifier {
    val gradient = WALLPAPER_GRADIENTS[preset]
    if (photo == null && gradient == null && preset != "pattern" && preset != "doodle") return this
    return this.drawBehind {
        when {
            photo != null -> drawCoverImage(photo)
            preset == "pattern" -> {
                drawRect(Color(0xFF0E1A14))
                drawDotPattern()
            }
            preset == "doodle" -> {
                drawRect(Color(0xFF0D080F))
                val tile = 340f
                var ty = 0f
                while (ty < size.height) {
                    var tx = 0f
                    while (tx < size.width) {
                        drawDoodleTile(tx, ty, tile)
                        tx += tile
                    }
                    ty += tile
                }
            }
            gradient != null -> drawRect(Brush.linearGradient(gradient))
            else -> Unit
        }
    }
}

private fun DrawScope.drawCoverImage(img: ImageBitmap) {
    val scale = maxOf(size.width / img.width, size.height / img.height)
    val srcW = (size.width / scale).toInt().coerceIn(1, img.width)
    val srcH = (size.height / scale).toInt().coerceIn(1, img.height)
    val srcX = ((img.width - srcW) / 2).coerceAtLeast(0)
    val srcY = ((img.height - srcH) / 2).coerceAtLeast(0)
    drawImage(
        img, srcOffset = IntOffset(srcX, srcY), srcSize = IntSize(srcW, srcH),
        dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
    )
}

// Сетка точек в шахматном порядке (тайл 72 px, как dotPatternBrush на Android).
private fun DrawScope.drawDotPattern() {
    val tile = 72f
    val step = tile / 2f
    val dot = Color.White.copy(alpha = 0.12f)
    var ty = 0f
    while (ty < size.height) {
        var tx = 0f
        while (tx < size.width) {
            for (row in 0..1) for (col in 0..1) {
                val offX = if (row % 2 == 1) step / 2f else 0f
                drawCircle(dot, 3f, Offset(tx + (col * step + offX) % tile, ty + row * step))
            }
            tx += tile
        }
        ty += tile
    }
}

// Один тайл «каракулей»: звезда, сердце, месяц, нотка, облачко (повторяет doodlePatternBrush с Android).
private fun DrawScope.drawDoodleTile(ox: Float, oy: Float, s: Float) {
    val color = Color(red = 150, green = 100, blue = 180, alpha = 130)
    val sw = 3.4f
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

    translate(ox + s * 0.18f, oy + s * 0.20f) {
        rotate(-12f, pivot = Offset.Zero) {
            val r = s * 0.11f
            val star = Path()
            for (i in 0..9) {
                val ang = (-90 + i * 36) * PI / 180.0
                val rr = if (i % 2 == 0) r else r * 0.42f
                val x = (rr * cos(ang)).toFloat()
                val y = (rr * sin(ang)).toFloat()
                if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            drawPath(star, color, style = stroke)
        }
    }
    translate(ox + s * 0.74f, oy + s * 0.18f) {
        val hs = s * 0.10f
        val heart = Path()
        heart.moveTo(0f, hs * 0.6f)
        heart.cubicTo(-hs * 1.3f, -hs * 0.6f, -hs * 0.3f, -hs * 1.5f, 0f, -hs * 0.4f)
        heart.cubicTo(hs * 0.3f, -hs * 1.5f, hs * 1.3f, -hs * 0.6f, 0f, hs * 0.6f)
        heart.close()
        drawPath(heart, color, style = stroke)
    }
    drawArc(
        color, startAngle = 40f, sweepAngle = 280f, useCenter = false,
        topLeft = Offset(ox + s * 0.10f, oy + s * 0.62f), size = Size(s * 0.22f, s * 0.22f), style = stroke,
    )
    translate(ox + s * 0.76f, oy + s * 0.70f) {
        drawCircle(color, s * 0.045f, Offset(0f, s * 0.07f), style = stroke)
        drawLine(color, Offset(s * 0.04f, s * 0.07f), Offset(s * 0.04f, -s * 0.10f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(s * 0.04f, -s * 0.10f), Offset(s * 0.12f, -s * 0.06f), strokeWidth = sw, cap = StrokeCap.Round)
    }
    val cx = ox + s * 0.46f
    val cy = oy + s * 0.46f
    drawCircle(color, s * 0.045f, Offset(cx - s * 0.05f, cy), style = stroke)
    drawCircle(color, s * 0.055f, Offset(cx + s * 0.02f, cy - s * 0.03f), style = stroke)
    drawCircle(color, s * 0.04f, Offset(cx + s * 0.09f, cy), style = stroke)
    drawLine(color, Offset(cx - s * 0.09f, cy + s * 0.035f), Offset(cx + s * 0.13f, cy + s * 0.035f), strokeWidth = sw, cap = StrokeCap.Round)
}

private fun textScaleFor(key: String): Float = when (key) { "SMALL" -> 0.88f; "LARGE" -> 1.18f; else -> 1f }

// Цвет своих пузырей (Premium); null — обычный цвет темы.
private fun bubbleColorFor(id: String?): Color? =
    PREMIUM_BUBBLE_COLORS.firstOrNull { it.first == id }?.second?.let { Color(it) }

@OptIn(ExperimentalEncodingApi::class)
private fun encodeBase64(bytes: ByteArray): String = Base64.Default.encode(bytes)

// Base64 из mediaData (Android кладёт туда фото/стикеры без Cloudinary). Mime-декодер терпим к
// переводам строк; префикс data:...;base64, на всякий случай отрезаем.
@OptIn(ExperimentalEncodingApi::class)
private fun decodeBase64Safe(s: String): ByteArray? =
    try { Base64.Mime.decode(s.substringAfter("base64,", s)) } catch (e: Exception) { null }

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
                // Узкий экран (телефон) — отдельная раскладка в стиле Android-приложения;
                // широкий (Windows) — прежняя с боковой полосой и двумя панелями.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val compact = maxWidth < COMPACT_WIDTH
                    when (screen) {
                        is Screen.Login -> SafeArea(enabled = compact, topColor = BgPrimary) {
                            LoginScreen(onLoggedIn = { screen = Screen.Main })
                        }
                        is Screen.Main -> MainShell(
                            isDark = isDark,
                            onToggleDark = { checked -> isDark = checked; saveDarkThemePref(checked) },
                            accentKey = accentKey,
                            onSelectAccent = { key -> accentKey = key; saveAccentPref(key) },
                            onSignedOut = { screen = Screen.Login },
                            compact = compact,
                        )
                    }
                    if (screen is Screen.Main) CallHost(compact)
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
    compact: Boolean = false,
) {
    var section by remember { mutableStateOf(Section.Chats) }
    var openChat by remember { mutableStateOf<OpenChat?>(null) }
    var storyViewer by remember { mutableStateOf<Pair<List<List<SharedStory>>, Int>?>(null) }

    // Пока приложение открыто — «в сети»; при выходе из главного экрана — «был(а) в сети …».
    LaunchedEffect(Unit) {
        val uid = SharedRepo.currentUid
        if (uid != null) SharedRepo.setOnlineStatus(uid, true)
        try { awaitCancellation() } finally {
            if (uid != null) withContext(NonCancellable) { SharedRepo.setOnlineStatus(uid, false) }
        }
    }

    Box(Modifier.fillMaxSize()) {
    if (compact) {
        SafeArea(enabled = true, topColor = if (openChat != null) BgSecondary else BgPrimary) {
            CompactMain(
                section = section,
                onSection = { section = it },
                openChat = openChat,
                onOpenChat = { openChat = it },
                onOpenStories = { groups, index -> storyViewer = groups to index },
                isDark = isDark,
                onToggleDark = onToggleDark,
                accentKey = accentKey,
                onSelectAccent = onSelectAccent,
                onSignedOut = onSignedOut,
            )
        }
    } else {
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
                            onOpenStories = { groups, index -> storyViewer = groups to index },
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
            Section.Calls -> Box(Modifier.weight(1f).fillMaxHeight()) {
                CallHistoryPane(compact = false, onOpenChat = { openChat = it; section = Section.Chats })
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
    storyViewer?.let { (groups, startIndex) ->
        StoryViewer(groups, startIndex, onClose = { storyViewer = null })
    }
    }
}

// Ширина, меньше которой включается телефонная раскладка.
private val COMPACT_WIDTH = 700.dp

// Отступы от статус-бара/выреза/«домашней» полосы и клавиатуры (на iPhone; на десктопе = 0).
// Полоса под статус-баром закрашивается цветом верхней панели, как в Android-приложении.
@Composable
private fun SafeArea(enabled: Boolean, topColor: Color, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        Box(Modifier.fillMaxWidth().height(top).background(topColor))
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        ) { content() }
    }
}

// Телефонная раскладка: список чатов / настройки на весь экран с плавающей панелью
// вкладок внизу (как в Android-версии); чат открывается на весь экран со стрелкой «назад».
@Composable
private fun CompactMain(
    section: Section,
    onSection: (Section) -> Unit,
    openChat: OpenChat?,
    onOpenChat: (OpenChat?) -> Unit,
    onOpenStories: (List<List<SharedStory>>, Int) -> Unit,
    isDark: Boolean,
    onToggleDark: (Boolean) -> Unit,
    accentKey: String,
    onSelectAccent: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
    if (openChat != null) {
        key(openChat.id) {
            ChatScreen(openChat.id, openChat.title, openChat.photoUrl, onClose = { onOpenChat(null) }, compact = true)
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(bottom = 92.dp)) {
                if (section == Section.Calls) {
                    CallHistoryPane(compact = true, onOpenChat = { onSection(Section.Chats); onOpenChat(it) })
                } else if (section == Section.Settings) {
                    SettingsScreen(
                        onSignedOut = onSignedOut,
                        isDark = isDark,
                        onToggleDark = onToggleDark,
                        accentKey = accentKey,
                        onSelectAccent = onSelectAccent,
                    )
                } else {
                    key(section) {
                        ChatListPane(
                            archived = section == Section.Archive,
                            selectedId = null,
                            onOpenChat = { onOpenChat(it) },
                            onChatRemoved = { },
                            onOpenStories = onOpenStories,
                            compact = true,
                            onToggleArchive = { onSection(if (section == Section.Archive) Section.Chats else Section.Archive) },
                        )
                    }
                }
            }
            BottomCapsule(section, onSection, Modifier.align(Alignment.BottomCenter))
        }
    }
}

// Плавающая «капсула» с вкладками Чаты / Настройки (выбранная подсвечена акцентом).
@Composable
private fun BottomCapsule(section: Section, onSelect: (Section) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(shape).background(BgSecondary).border(1.dp, HoverBg, shape).padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapsuleItem(Icons.Default.ChatBubble, "Чаты", section == Section.Chats || section == Section.Archive, Modifier.weight(1f)) { onSelect(Section.Chats) }
        CapsuleItem(Icons.Default.Call, "Звонки", section == Section.Calls, Modifier.weight(1f)) { onSelect(Section.Calls) }
        CapsuleItem(Icons.Default.Settings, "Настройки", section == Section.Settings, Modifier.weight(1f)) { onSelect(Section.Settings) }
    }
}

@Composable
private fun CapsuleItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(24.dp))
                .background(if (selected) Accent.copy(alpha = 0.18f) else Color.Transparent)
                .clickable { onClick() }
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, label, tint = if (selected) Accent else TextSecondary)
            Text(label, color = if (selected) Accent else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// Меню сообщения для телефона (вызывается долгим нажатием на пузырь).
@Composable
private fun MessageActionsMenu(
    expanded: Boolean, onDismiss: () -> Unit, isText: Boolean, isDeleted: Boolean, mine: Boolean, isPinned: Boolean,
    onCopy: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onPin: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (isText && !isDeleted) DropdownMenuItem(text = { Text("Копировать") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { onDismiss(); onCopy() })
        if (mine && isText && !isDeleted) DropdownMenuItem(text = { Text("Редактировать") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { onDismiss(); onEdit() })
        if (!isDeleted) DropdownMenuItem(text = { Text(if (isPinned) "Открепить" else "Закрепить") }, leadingIcon = { Icon(Icons.Default.PushPin, null) }, onClick = { onDismiss(); onPin() })
        if (mine && !isDeleted) DropdownMenuItem(text = { Text("Удалить", color = DangerColor) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = DangerColor) }, onClick = { onDismiss(); onDelete() })
    }
}

// Полноэкранный просмотр фото: щипок двумя пальцами — масштаб, перетаскивание — сдвиг,
// двойной тап — приблизить/вернуть. Крестик слева сверху закрывает.
@Composable
private fun ImageViewer(bitmap: androidx.compose.ui.graphics.ImageBitmap, onClose: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        fun clamp(o: Offset, sc: Float): Offset {
            val maxX = widthPx * (sc - 1f) / 2f
            val maxY = heightPx * (sc - 1f) / 2f
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }
        androidx.compose.foundation.Image(
            bitmap, contentDescription = null,
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 6f)
                        scale = newScale
                        offset = if (newScale <= 1f) Offset.Zero else clamp(offset + pan, newScale)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    })
                }
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            contentScale = ContentScale.Fit,
        )
        Box(Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start)).padding(8.dp)) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
            ) { Icon(Icons.Default.Close, "Закрыть", tint = Color.White) }
        }
    }
}

// Ввод промокода — одно и то же в настройках и в окне Premium.
@Composable
private fun PromoCodeBox(myUid: String?) {
    var promoCode by remember { mutableStateOf("") }
    var promoMsg by remember { mutableStateOf<String?>(null) }
    var promoBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column {
        OutlinedTextField(
            value = promoCode, onValueChange = { promoCode = it; promoMsg = null },
            label = { Text("Промокод") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (myUid != null) {
                    promoBusy = true
                    scope.launch {
                        SharedRepo.redeemPromoCode(myUid, promoCode)
                            .onSuccess { promoMsg = it }
                            .onFailure { promoMsg = "Не удалось: " + (it.message ?: "ошибка") }
                        promoBusy = false
                    }
                }
            },
            enabled = promoCode.isNotBlank() && !promoBusy,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
        ) { Text("Активировать") }
        promoMsg?.let {
            Text(it, color = if (it.startsWith("⭐")) OnlineColor else TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// Окно «PingGram Premium»: что входит и (если Premium ещё нет) активация по промокоду.
@Composable
private fun PremiumDialog(isPremium: Boolean, myUid: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPremium) "У тебя есть Premium ⭐" else "PingGram Premium ⭐") },
        text = {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Что входит в Premium", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "🖼" to "Цветная рамка вокруг аватара — на выбор из нескольких цветов",
                    "⭐" to "Значок рядом с именем — выбери эмодзи или скрой его",
                    "🎨" to "Свой цвет пузырей ваших сообщений",
                    "🚫" to "Список заблокированных пользователей в настройках",
                ).forEach { (icon, text) ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Text(icon, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(text, color = TextSecondary, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (isPremium) {
                    Text("Настроить рамку, значок и цвет пузырей можно в разделе PREMIUM выше.", color = TextSecondary, fontSize = 13.sp)
                } else {
                    Text("Получить Premium бесплатно", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Введите промокод — Premium включится на время его действия", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    PromoCodeBox(myUid)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть", color = Accent) } },
    )
}

// Адрес политики конфиденциальности: если задан — открывается в браузере, иначе показывается окно с текстом.
private const val PRIVACY_POLICY_URL = ""
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
private fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Политика конфиденциальности") },
        text = {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(PRIVACY_POLICY_TEXT, color = TextSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно", color = Accent) } },
    )
}

// Строка-ссылка в настройках: значок, заголовок, подпись и стрелка.
@Composable
private fun SettingsLinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, iconTint: Color, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconTint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconTint)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Text("›", color = TextSecondary, fontSize = 22.sp)
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
        RailButton(Icons.Default.Call, "Звонки", section == Section.Calls) { onSelect(Section.Calls) }
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
    var info by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var agreed by remember { mutableStateOf(loadBoolPref("terms_accepted", false)) }
    var showTerms by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
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

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = agreed,
                    onCheckedChange = { agreed = it; saveBoolPref("terms_accepted", it) },
                    colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = TextSecondary),
                )
                Text("Принимаю ", color = TextSecondary, fontSize = 13.sp)
                Text("условия использования", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { showTerms = true })
            }
            if (showTerms) TermsDialog(onDismiss = { showTerms = false })

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = DangerColor)
            }
            info?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = OnlineColor)
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
                enabled = !loading && agreed && email.isNotBlank() && password.isNotBlank() &&
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

            if (!isRegisterMode) {
                TextButton(
                    onClick = {
                        error = null
                        info = null
                        if (email.isBlank()) {
                            error = "Введите email выше, затем нажмите «Забыл пароль?»"
                        } else {
                            loading = true
                            scope.launch {
                                sendPasswordReset(email.trim())
                                    .onSuccess { info = "Письмо для сброса пароля отправлено на ${email.trim()}" }
                                    .onFailure { error = it.message ?: "Не удалось отправить письмо" }
                                loading = false
                            }
                        }
                    },
                    enabled = !loading,
                ) { Text("Забыл пароль?", color = Accent) }
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
                enabled = !loading && agreed,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Войти через Google")
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { isRegisterMode = !isRegisterMode; error = null; info = null }) {
                Text(
                    if (isRegisterMode) "Уже есть аккаунт? Войти" else "Нет аккаунта? Зарегистрироваться",
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ChatListPane(archived: Boolean, selectedId: String?, onOpenChat: (OpenChat) -> Unit, onChatRemoved: (String) -> Unit, onOpenStories: (List<List<SharedStory>>, Int) -> Unit, compact: Boolean = false, onToggleArchive: (() -> Unit)? = null) {
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

    // Авторы историй, которых показываем в ленте: собеседники из личных чатов.
    val contactUids = remember(chats, myUid) {
        chats.filter { it.data.type == SChatType.PRIVATE }.flatMap { it.data.memberIds }.filter { it != myUid }.toSet()
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = if (compact) 12.dp else 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (archived) "Архив" else "PingGram",
                color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            if (compact && onToggleArchive != null) {
                IconButton(onClick = onToggleArchive) {
                    Icon(Icons.Default.Archive, if (archived) "Чаты" else "Архив", tint = if (archived) Accent else TextSecondary)
                }
            }
            if (!archived && !compact) {
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
        if (!archived && query.isBlank()) StoriesBar(contactUids, onOpenStories)
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
                        compact = compact,
                    )
                }
            }
        }
    }
    if (compact && !archived) {
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp).size(58.dp)
                .clip(RoundedCornerShape(18.dp)).background(Accent).clickable { showNew = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Edit, "Новый чат", tint = Color.White) }
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
    var profileUser by remember { mutableStateOf<SharedUser?>(null) }
    var channels by remember { mutableStateOf<List<SharedChat>>(emptyList()) }
    var showNewChannel by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        if (myUid == null) return@LaunchedEffect
        delay(300)
        results = SharedRepo.searchUsers(query, myUid)
        channels = if (group) emptyList() else SharedRepo.searchChannels(query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group) "Новая группа" else "Новый чат") },
        text = {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
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
                    if (!group) items(channels, key = { "ch_" + it.id }) { ch ->
                        val isMember = myUid != null && ch.data.memberIds.contains(myUid)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                if (myUid == null) return@clickable
                                scope.launch {
                                    val joined = if (isMember) null else SharedRepo.joinChannel(ch.id, myUid)
                                    if (joined != null && joined.isFailure) {
                                        error = joined.exceptionOrNull()?.message ?: "Не удалось подписаться"
                                        return@launch
                                    }
                                    onOpen(OpenChat(ch.id, ch.data.name, ch.data.photoUrl))
                                    onDismiss()
                                }
                            }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(ch.data.photoUrl, ch.data.name, 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ch.data.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                val handle = if (ch.data.channelUsername.isNotBlank()) "@" + ch.data.channelUsername + " · " else ""
                                Text("📢 " + handle + ch.data.memberIds.size + " подписчиков", color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                            Text(if (isMember) "Открыть" else "Подписаться", color = Accent, fontSize = 12.sp)
                        }
                    }
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
                            else if (!group) IconButton(onClick = { profileUser = u }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Info, "Профиль", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Row {
                    TextButton(onClick = { group = !group; error = null }) { Text(if (group) "← Обычный чат" else "Создать группу") }
                    if (!group) TextButton(onClick = { showNewChannel = true }) { Text("Создать канал") }
                }
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

    if (showNewChannel) {
        NewChannelDialog(onDismiss = { showNewChannel = false }, onCreated = { chat -> showNewChannel = false; onOpen(chat); onDismiss() })
    }

    profileUser?.let { pu ->
        val writeAction: (SUser) -> Unit = { _ ->
            if (myUid != null) scope.launch {
                try {
                    val id = SharedRepo.getOrCreatePrivateChat(myUid, pu.uid)
                    val nm = pu.data.displayName.ifBlank { pu.data.username.ifBlank { "Пользователь" } }
                    profileUser = null
                    onOpen(OpenChat(id, nm, pu.data.photoUrl))
                    onDismiss()
                } catch (e: Exception) { profileUser = null; error = e.message ?: "Не удалось открыть чат" }
            }
        }
        UserProfileDialog(pu.uid, onDismiss = { profileUser = null }, onWrite = writeAction)
    }
}

// Лента историй над списком чатов (как StoriesBar на Android): первый кружок — «моя история»
// (+ добавить фото, либо открыть свою), дальше — собеседники с активными историями.
// Непросмотренные — с цветным кольцом, просмотренные — с блёклым.
@Composable
private fun StoriesBar(contactUids: Set<String>, onOpen: (List<List<SharedStory>>, Int) -> Unit) {
    val myUid = SharedRepo.currentUid ?: ""
    var all by remember { mutableStateOf<List<SharedStory>>(emptyList()) }
    var me by remember { mutableStateOf<SUser?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        SharedRepo.observeActiveStories()
            .catch { e -> error = "Истории не загрузились: " + (e.message ?: "ошибка") }
            .collect { all = it }
    }
    LaunchedEffect(myUid) { if (myUid.isNotBlank()) SharedRepo.observeUser(myUid).collect { me = it } }

    val now = nowEpochSeconds()
    val groups = remember(all, myUid, contactUids) {
        val byUser = all
            .filter { (tsSeconds(it.data.expiresAt) ?: 0L) > now }
            .filter { it.data.userId == myUid || it.data.userId in contactUids }
            .groupBy { it.data.userId }.values
            .map { list -> list.sortedBy { tsSeconds(it.data.createdAt) ?: 0L } }
        val mine = byUser.filter { it.first().data.userId == myUid }
        val others = byUser.filter { it.first().data.userId != myUid }
            .sortedByDescending { g -> g.maxOf { tsSeconds(it.data.createdAt) ?: 0L } }
        mine + others
    }

    fun addStory() {
        if (uploading || myUid.isBlank()) return
        scope.launch {
            val picked = try { pickFile(MediaKind.IMAGE) } catch (e: Exception) { error = e.message; null } ?: return@launch
            uploading = true
            error = null
            SharedRepo.postStory(myUid, me?.displayName ?: "", me?.photoUrl ?: "", picked.bytes, picked.name, picked.mimeType)
                .onFailure { error = "Не удалось опубликовать историю: " + (it.message ?: "ошибка") }
            uploading = false
        }
    }

    val myGroup = groups.firstOrNull { it.first().data.userId == myUid }
    val others = groups.filter { it.first().data.userId != myUid }
    Column {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "my_story") {
                StoryCircle("Моя история", me?.photoUrl ?: "", ring = if (myGroup != null) Accent else null, showPlus = myGroup == null, busy = uploading) {
                    if (myGroup != null) onOpen(groups, groups.indexOf(myGroup)) else addStory()
                }
            }
            items(others, key = { it.first().data.userId }) { g ->
                val unseen = g.any { myUid !in it.data.viewedBy }
                StoryCircle(
                    g.first().data.userName.ifBlank { "Без имени" }, g.first().data.userPhotoUrl,
                    ring = if (unseen) Accent else TextSecondary.copy(alpha = 0.5f), showPlus = false, busy = false,
                ) { onOpen(groups, groups.indexOf(g)) }
            }
        }
        error?.let {
            Text(it, color = DangerColor, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp).clickable { error = null })
        }
    }
}

@Composable
private fun StoryCircle(label: String, photoUrl: String, ring: Color?, showPlus: Boolean, busy: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(58.dp).then(if (ring != null) Modifier.border(2.5.dp, ring, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) { Avatar(photoUrl, label, 50.dp) }
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Accent, strokeWidth = 2.dp)
            } else if (showPlus) {
                Box(Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Просмотр историй поверх всего окна: 5 секунд на историю, клик по левой/правой половине —
// назад/вперёд (между авторами тоже), у своих историй есть «Удалить».
@Composable
private fun StoryViewer(groups: List<List<SharedStory>>, startIndex: Int, onClose: () -> Unit) {
    val myUid = SharedRepo.currentUid ?: ""
    var groupIdx by remember { mutableStateOf(startIndex.coerceIn(0, (groups.size - 1).coerceAtLeast(0))) }
    var storyIdx by remember { mutableStateOf(0) }
    var progress by remember(groupIdx, storyIdx) { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    val group = groups.getOrNull(groupIdx)
    val story = group?.getOrNull(storyIdx)
    if (group == null || story == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val isMine = story.data.userId == myUid

    fun next() {
        if (storyIdx < group.lastIndex) storyIdx++
        else if (groupIdx < groups.lastIndex) { groupIdx++; storyIdx = 0 }
        else onClose()
    }
    fun prev() {
        if (storyIdx > 0) storyIdx--
        else if (groupIdx > 0) { groupIdx--; storyIdx = (groups[groupIdx].size - 1).coerceAtLeast(0) }
    }

    LaunchedEffect(story.id) {
        if (!isMine && myUid.isNotBlank()) SharedRepo.markStoryViewed(story.id, myUid)
    }
    LaunchedEffect(groupIdx, storyIdx) {
        while (progress < 1f) {
            delay(16)
            progress += 16f / 5000f
        }
        next()
    }

    val noRipple = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable(interactionSource = noRipple, indication = null) { onClose() }) {
        Box(
            Modifier.align(Alignment.Center).fillMaxHeight(0.94f).aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(16.dp)).background(Color.Black),
        ) {
            StoryImage(story.data.mediaUrl, story.data.mediaData, story.id)

            // Левая половина — назад, правая — вперёд.
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { prev() })
                Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { next() })
            }

            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.25f)).padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    group.forEachIndexed { i, _ ->
                        val p = when { i < storyIdx -> 1f; i == storyIdx -> progress; else -> 0f }
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.weight(1f).height(2.5.dp).clip(RoundedCornerShape(2.dp)),
                            color = Color.White, trackColor = Color.White.copy(alpha = 0.3f),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val who = story.data.userName.ifBlank { "Без имени" }
                    Avatar(story.data.userPhotoUrl, who, 32.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(who, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val secs = tsSeconds(story.data.createdAt)
                        if (secs != null) Text(formatChatTime(secs), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                    if (isMine) {
                        IconButton(onClick = { scope.launch { SharedRepo.deleteStory(story.id); onClose() } }) {
                            Icon(Icons.Default.Delete, "Удалить историю", tint = Color.White)
                        }
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Закрыть", tint = Color.White) }
                }
            }
        }
    }
}

// Фото истории: по Cloudinary-ссылке, а если её нет — из Base64 в mediaData (так публикует Android без Cloudinary).
@Composable
private fun StoryImage(mediaUrl: String, mediaData: String, key: String) {
    var bmp by remember(key) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        val decoded = if (mediaUrl.isNotBlank()) {
            fetchBytes(mediaUrl).getOrNull()?.let { decodeImageBitmap(it) }
        } else {
            decodeBase64Safe(mediaData)?.let { decodeImageBitmap(it) }
        }
        if (decoded != null) bmp = decoded else failed = true
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) androidx.compose.foundation.Image(b, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else if (failed) Text("Не удалось загрузить историю", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        else CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
    }
}

// Панель GIF (Klipy): строка поиска + сетка превью; клик по превью отправляет GIF.
@Composable
private fun GifPanel(onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.rosechat.shared.data.GifResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableStateOf(0) }

    LaunchedEffect(query, retryTick) {
        if (!GifRepository.isConfigured) return@LaunchedEffect
        if (query.isNotBlank()) delay(400)
        loading = true
        error = null
        val r = if (query.isBlank()) GifRepository.trending() else GifRepository.search(query.trim())
        r.onSuccess { results = it }.onFailure { results = emptyList(); error = it.message ?: "Не удалось загрузить GIF" }
        loading = false
    }

    Surface(color = BgSecondary) {
        Column(Modifier.fillMaxWidth().height(300.dp).padding(8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Поиск GIF…", color = TextSecondary) },
                singleLine = true, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (!GifRepository.isConfigured) {
                    Text(
                        "GIF пока не настроены: добавьте GIF_API_KEY=… в local.properties (корень проекта) и перезапустите приложение",
                        color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(16.dp),
                    )
                } else if (loading) {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(28.dp))
                } else if (error != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Не удалось загрузить GIF", color = TextPrimary, fontSize = 13.sp)
                        Text(error ?: "", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        TextButton(onClick = { retryTick++ }) { Text("Повторить", color = Accent) }
                    }
                } else if (results.isEmpty()) {
                    Text("Ничего не найдено", color = TextSecondary, fontSize = 13.sp)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        gridItems(results, key = { it.id }) { g -> GifThumb(g.previewUrl) { onPick(g.fullUrl) } }
                    }
                }
            }
        }
    }
}

// Превью GIF в сетке — статичный первый кадр (быстро и не грузит процессор десятками анимаций).
@Composable
private fun GifThumb(url: String, onClick: () -> Unit) {
    var bmp by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(url) { fetchBytes(url).onSuccess { b -> bmp = decodeImageBitmap(b) } }
    Box(
        Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(8.dp)).background(BubbleIn.copy(alpha = 0.6f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) androidx.compose.foundation.Image(b, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
    }
}

// GIF в сообщении: скачиваем, разбираем на кадры и крутим по таймингам самого файла.
// Если файл не анимированный (или разобрать не вышло) — показываем обычную картинку.
@Composable
private fun ChatGif(url: String, key: String) {
    var anim by remember(key) { mutableStateOf<GifAnimation?>(null) }
    var current by remember(key) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(key) { mutableStateOf(false) }

    LaunchedEffect(url) {
        fetchBytes(url)
            .onSuccess { bytes ->
                val a = withContext(kotlinx.coroutines.Dispatchers.Default) { decodeGifAnimation(bytes) }
                if (a != null) anim = a
                else {
                    val still = decodeImageBitmap(bytes)
                    if (still != null) current = still else failed = true
                }
            }
            .onFailure { failed = true }
    }
    LaunchedEffect(anim) {
        val a = anim ?: return@LaunchedEffect
        var i = 0
        while (true) {
            current = withContext(kotlinx.coroutines.Dispatchers.Default) { a.frame(i) }
            delay(a.delayMs(i).toLong())
            i = (i + 1) % a.frameCount
        }
    }

    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(BubbleIn.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = current
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bmp, null, contentScale = ContentScale.Fit,
                modifier = Modifier.widthIn(max = 260.dp).heightIn(max = 260.dp),
            )
        } else if (failed) {
            Column(Modifier.size(140.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Image, null, tint = TextSecondary)
                Text("GIF не загрузился", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Accent, strokeWidth = 2.dp)
            }
        }
    }
}

// Картиночный стикер в панели стикеров (PNG из ресурсов приложения).
@Composable
private fun ImageStickerThumb(name: String, onClick: () -> Unit) {
    val bmp = remember(name) { loadStickerResource(name)?.let { decodeImageBitmap(it) } }
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) androidx.compose.foundation.Image(bmp, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text("🖼", fontSize = 22.sp)
    }
}

// Создание канала (как диалог «Новый канал» на Android): название, описание, публичный/приватный,
// @username для публичного, «только админы пишут».
@Composable
private fun NewChannelDialog(onDismiss: () -> Unit, onCreated: (OpenChat) -> Unit) {
    val myUid = SharedRepo.currentUid
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var broadcastOnly by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый канал") },
        text = {
            Column(Modifier.widthIn(max = 400.dp).fillMaxWidth()) {
                OutlinedTextField(name, { name = it }, label = { Text("Название канала") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(description, { description = it }, label = { Text("Описание (необязательно)") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Публичный канал", fontWeight = FontWeight.SemiBold)
                        Text("Виден в поиске по @username", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
                if (isPublic) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(username, { username = it }, label = { Text("@username канала (от 5 символов)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Только админы пишут", fontWeight = FontWeight.SemiBold)
                        Text("Как классический канал-рассылка", color = TextSecondary, fontSize = 12.sp)
                    }
                    Switch(checked = broadcastOnly, onCheckedChange = { broadcastOnly = it })
                }
                error?.let { Text(it, color = DangerColor, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !busy && myUid != null,
                onClick = {
                    if (myUid == null) return@TextButton
                    busy = true
                    error = null
                    scope.launch {
                        SharedRepo.createChannel(name, description, isPublic, username, broadcastOnly, myUid)
                            .onSuccess { id -> onCreated(OpenChat(id, name.trim(), "")) }
                            .onFailure { error = it.message ?: "Не удалось создать канал" }
                        busy = false
                    }
                },
            ) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// «О группе» / «О канале» (как ChannelInfoScreen на Android): название, описание, настройки канала
// (для админа), список участников с отметкой «админ», добавление, назначение админов, выход.
@Composable
private fun ChatInfoDialog(chatId: String, onDismiss: () -> Unit, onLeft: () -> Unit) {
    val myUid = SharedRepo.currentUid
    var chat by remember { mutableStateOf<SChat?>(null) }
    var members by remember { mutableStateOf<List<SharedUser>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var openProfile by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(chatId) { SharedRepo.observeChat(chatId).collect { chat = it } }
    val memberKey = chat?.memberIds ?: emptyList()
    LaunchedEffect(memberKey) { members = SharedRepo.getUsers(memberKey) }

    val c = chat
    val isChannel = c?.type == SChatType.CHANNEL
    val isAdmin = myUid != null && c?.adminIds?.contains(myUid) == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isChannel) "О канале" else "О группе") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        text = {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                if (c == null) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(72.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                            Icon(if (isChannel) Icons.Default.Campaign else Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(c.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (isChannel && c.isPublic && c.channelUsername.isNotBlank()) Text("@" + c.channelUsername, color = Accent, fontSize = 13.sp)
                        if (c.description.isNotBlank()) Text(c.description, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        Text("${c.memberIds.size} ${if (isChannel) "подписчиков" else "участников"}", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    if (isChannel && isAdmin) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Публичный канал", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Виден в глобальном поиске", color = TextSecondary, fontSize = 11.sp)
                            }
                            Switch(checked = c.isPublic, onCheckedChange = { v -> scope.launch { SharedRepo.setChannelPublic(chatId, v) } })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Только админы пишут", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Как классический канал-рассылка", color = TextSecondary, fontSize = 11.sp)
                            }
                            Switch(checked = c.broadcastOnly, onCheckedChange = { v -> scope.launch { SharedRepo.setChannelBroadcastOnly(chatId, v) } })
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isChannel) "Подписчики" else "Участники", color = TextSecondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (isAdmin) TextButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.PersonAdd, null, tint = Accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Добавить", color = Accent)
                        }
                    }
                    LazyColumn(Modifier.fillMaxWidth().height(200.dp)) {
                        items(members, key = { it.uid }) { m ->
                            val mName = m.data.displayName.ifBlank { m.data.username.ifBlank { "Пользователь" } }
                            val mAdmin = c.adminIds.contains(m.uid)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { openProfile = m.uid }.padding(vertical = 5.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(m.data.photoUrl, mName, 38.dp, frame = if (m.data.isPremium) frameColorFor(m.data.premiumFrame) else null)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(mName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        if (mAdmin) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(shape = RoundedCornerShape(6.dp), color = Accent) {
                                                Text("админ", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                                            }
                                        }
                                    }
                                    if (m.data.username.isNotBlank()) Text("@" + m.data.username, color = TextSecondary, fontSize = 12.sp)
                                }
                                if (isAdmin && m.uid != myUid) {
                                    IconButton(onClick = { scope.launch { SharedRepo.setChatAdmin(chatId, m.uid, !mAdmin).onFailure { error = it.message } } }, modifier = Modifier.size(32.dp)) {
                                        Icon(if (mAdmin) Icons.Default.Star else Icons.Default.StarBorder, "Админ", tint = if (mAdmin) Accent else TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                    error?.let { Text(it, color = DangerColor, fontSize = 12.sp) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmLeave = true },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerColor),
                    ) {
                        Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isChannel) "Отписаться" else "Покинуть группу")
                    }
                }
            }
        },
    )

    if (showAdd && c != null) AddMemberDialog(c.memberIds, chatId, onDismiss = { showAdd = false })

    openProfile?.let { uid -> UserProfileDialog(uid, onDismiss = { openProfile = null }, onWrite = null) }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(if (isChannel) "Отписаться от канала?" else "Покинуть группу?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    if (myUid != null) scope.launch {
                        SharedRepo.leaveChat(chatId, myUid)
                            .onSuccess { onLeft() }
                            .onFailure { error = it.message ?: "Не удалось выйти" }
                    }
                }) { Text("Да", color = DangerColor) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Отмена") } },
        )
    }
}

// Добавить участника: поиск по имени/@username/email, кто уже в чате — не показываем.
@Composable
private fun AddMemberDialog(existing: List<String>, chatId: String, onDismiss: () -> Unit) {
    val myUid = SharedRepo.currentUid
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SharedUser>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        if (myUid == null) return@LaunchedEffect
        delay(300)
        results = SharedRepo.searchUsers(query, myUid).filter { !existing.contains(it.uid) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить участника") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        text = {
            Column(Modifier.widthIn(max = 380.dp).fillMaxWidth()) {
                OutlinedTextField(query, { query = it }, label = { Text("Имя, @username или email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = DangerColor, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().height(240.dp)) {
                    items(results, key = { it.uid }) { u ->
                        val name = u.data.displayName.ifBlank { u.data.username.ifBlank { "Пользователь" } }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                scope.launch {
                                    SharedRepo.joinChannel(chatId, u.uid)
                                        .onSuccess { onDismiss() }
                                        .onFailure { error = it.message ?: "Не удалось добавить" }
                                }
                            }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(u.data.photoUrl, name, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(name, fontWeight = FontWeight.Medium, maxLines = 1)
                                if (u.data.username.isNotBlank()) Text("@" + u.data.username, color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
    )
}

// Профиль пользователя (как UserProfileScreen на Android): аватар с рамкой Premium, имя, значок,
// @username, «в сети» / «был(а) в сети …», статус, кнопки «Написать» и «Заблокировать».
@Composable
private fun UserProfileDialog(uid: String, onDismiss: () -> Unit, onWrite: ((SUser) -> Unit)?) {
    val myUid = SharedRepo.currentUid
    var user by remember { mutableStateOf<SUser?>(null) }
    var me by remember { mutableStateOf<SUser?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(uid) { SharedRepo.observeUser(uid).collect { user = it; loading = false } }
    LaunchedEffect(myUid) { if (myUid != null) SharedRepo.observeUser(myUid).collect { me = it } }
    val iBlocked = me?.blockedUsers?.contains(uid) == true
    val isSelf = myUid == uid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Профиль") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        text = {
            Box(Modifier.widthIn(max = 340.dp).fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                val u = user
                if (loading) {
                    CircularProgressIndicator(color = Accent)
                } else if (u == null) {
                    Text("Пользователь не найден", color = TextSecondary)
                } else {
                    val name = u.displayName.ifBlank { "Без имени" }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(u.photoUrl, name, 104.dp, frame = if (u.isPremium) frameColorFor(u.premiumFrame) else null)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            if (u.isPremium && u.showPremiumBadge) Text("  " + (PREMIUM_BADGE_EMOJI[u.premiumBadgeIcon] ?: "⭐"), fontSize = 18.sp)
                            if (u.profileSticker.isNotBlank()) Text("  " + u.profileSticker, fontSize = 18.sp)
                        }
                        if (u.username.isNotBlank()) Text("@" + u.username, color = Accent, fontSize = 14.sp)
                        if (u.isPremium) {
                            Spacer(Modifier.height(8.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFD700).copy(alpha = 0.2f)) {
                                Text(
                                    (PREMIUM_BADGE_EMOJI[u.premiumBadgeIcon] ?: "⭐") + " Premium",
                                    color = Color(0xFFFFB300), fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (u.isOnline) "в сети" else formatLastSeen(tsSeconds(u.lastSeen)),
                            color = if (u.isOnline) OnlineColor else TextSecondary, fontSize = 13.sp,
                        )
                        if (u.status.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(u.status, color = TextSecondary, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(20.dp))
                        if (onWrite != null && !isSelf) {
                            Button(
                                onClick = { onWrite(u) },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            ) { Text("Написать сообщение") }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (!isSelf) {
                            OutlinedButton(
                                onClick = {
                                    if (myUid != null) scope.launch {
                                        if (iBlocked) SharedRepo.unblockUser(myUid, uid) else SharedRepo.blockUser(myUid, uid)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (iBlocked) TextPrimary else DangerColor),
                            ) {
                                Icon(if (iBlocked) Icons.Default.LockOpen else Icons.Default.Block, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (iBlocked) "Разблокировать" else "Заблокировать")
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ChatRow(
    title: String, photoUrl: String, frame: String?, lastMessage: String, timeText: String,
    unread: Int, selected: Boolean, actions: List<Pair<String, () -> Unit>>, onClick: () -> Unit,
    compact: Boolean = false,
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
            .then(
                if (compact) Modifier.combinedClickable(onClick = onClick, onLongClick = { menu = true })
                else Modifier.clickable(interactionSource = interaction, indication = null) { onClick() },
            )
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
    var enterSendsPref by remember { mutableStateOf(loadBoolPref("enter_to_send", true)) }
    var typingPref by remember { mutableStateOf(loadBoolPref("show_typing", true)) }
    var textSizeKey by remember { mutableStateOf(loadStringPref("chat_text_size", "MEDIUM")) }
    var showAdmin by remember { mutableStateOf(false) }
    var showPremium by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

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
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                SettingsLinkRow(
                    Icons.Default.Star, Color(0xFFFFC107), "PingGram Premium",
                    if (userData?.isPremium == true) "У тебя есть Premium" else "Ввести промокод и получить Premium",
                ) { showPremium = true }
            }
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
                        Text("Значок у имени", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PREMIUM_BADGE_EMOJI.forEach { (key, emoji) ->
                                val selected = (userData?.premiumBadgeIcon ?: "star") == key
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape)
                                        .background(if (selected) Accent.copy(alpha = 0.25f) else HoverBg)
                                        .then(if (selected) Modifier.border(2.dp, Accent, CircleShape) else Modifier)
                                        .clickable { if (myUid != null) scope.launch { SharedRepo.setPremiumBadgeIcon(myUid, key) } },
                                    contentAlignment = Alignment.Center,
                                ) { Text(emoji, fontSize = 18.sp) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Показывать значок другим", color = TextPrimary, modifier = Modifier.weight(1f))
                            Switch(
                                checked = userData?.showPremiumBadge ?: true,
                                onCheckedChange = { v -> if (myUid != null) scope.launch { SharedRepo.setShowPremiumBadge(myUid, v) } },
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Цвет моих сообщений", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Виден только вам", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PREMIUM_BUBBLE_COLORS.forEach { (key, argb) ->
                                val selected = (userData?.premiumBubbleColor ?: "default") == key
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                        .background(argb?.let { Color(it) } ?: Accent)
                                        .then(if (selected) Modifier.border(2.dp, TextPrimary, CircleShape) else Modifier)
                                        .clickable { if (myUid != null) scope.launch { SharedRepo.setPremiumBubbleColor(myUid, key) } },
                                )
                            }
                        }
                        // Скрытая функция: переключатель виден только аккаунту с isDeveloper = true.
                        // Обычным пользователям и Premium-пользователям его в интерфейсе нет совсем.
                        if (userData?.isDeveloper == true) {
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Показывать удалённые сообщения", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Действует после открытия чата заново", color = TextSecondary, fontSize = 12.sp)
                                }
                                Switch(checked = showDeleted, onCheckedChange = { showDeleted = it; saveBoolPref("show_deleted", it) })
                            }
                        }
                        // Список заблокированных — часть Premium (блокировать и разблокировать
                        // можно всем: из профиля и из меню чата).
                        if (blockedNames.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text("Заблокированные пользователи", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            blockedNames.forEach { (uid, name) ->
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, color = TextPrimary, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { if (myUid != null) scope.launch { SharedRepo.unblockUser(myUid, uid) } }) { Text("Разблокировать", color = Accent) }
                                }
                            }
                        } else {
                            Spacer(Modifier.height(14.dp))
                            Text("Заблокированных пользователей нет", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            } else if (userData != null) {
                SectionTitle("PREMIUM")
                SettingsCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Получить Premium бесплатно", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Введите промокод — Premium включится на время его действия", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        PromoCodeBox(myUid)
                    }
                }
            }

            SectionTitle("ФОН ЧАТА")
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    var bgBusy by remember { mutableStateOf(false) }
                    var bgError by remember { mutableStateOf<String?>(null) }
                    val mountain = remember { loadWallpaperResource("wallpaper_mountain")?.let { decodeImageBitmap(it) } }
                    val activePreset = userData?.chatWallpaperPreset ?: ""
                    val hasCustom = (userData?.chatBackground ?: "").isNotBlank() || (userData?.chatBackgroundData ?: "").isNotBlank()
                    Text("Обои переписки", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Виден только вам", color = TextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    // Сетка обоев: на телефоне 3 в ряд, на широком экране 5; все плитки одной ширины и пропорций.
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cols = if (maxWidth < 440.dp) 3 else 5
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            WALLPAPER_PRESETS.chunked(cols).forEach { rowItems ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    rowItems.forEach { (id, label) ->
                                        val selected = activePreset == id && !hasCustom
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(
                                                Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp))
                                                    .chatWallpaper(id, if (id == "mountain") mountain else null)
                                                    .then(if (selected) Modifier.border(2.dp, Accent, RoundedCornerShape(12.dp)) else Modifier)
                                                    .clickable { if (myUid != null) scope.launch { SharedRepo.setChatWallpaperPreset(myUid, id) } },
                                            )
                                            Text(
                                                label, color = if (selected) Accent else TextSecondary, fontSize = 11.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            )
                                        }
                                    }
                                    repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            enabled = !bgBusy,
                            onClick = {
                                if (myUid != null) scope.launch {
                                    val picked = try { pickFile(MediaKind.IMAGE) } catch (e: Exception) { bgError = e.message; null } ?: return@launch
                                    bgBusy = true
                                    bgError = null
                                    SharedRepo.uploadChatBackground(myUid, picked.bytes, picked.name, picked.mimeType)
                                        .onFailure { bgError = it.message ?: "Не удалось загрузить фон" }
                                    bgBusy = false
                                }
                            },
                        ) { Text(if (bgBusy) "Загрузка…" else "Своё фото") }
                        if (activePreset.isNotBlank() || hasCustom) {
                            TextButton(onClick = { if (myUid != null) scope.launch { SharedRepo.clearChatBackground(myUid) } }) {
                                Text("Убрать фон", color = DangerColor)
                            }
                        }
                    }
                    bgError?.let { Text(it, color = DangerColor, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
            }

            SectionTitle("НАСТРОЙКИ ЧАТОВ")
            SettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Enter отправляет сообщение", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (enterSendsPref) "Enter — отправить сообщение" else "Enter — новая строка, Ctrl+Enter — отправить",
                                color = TextSecondary, fontSize = 12.sp,
                            )
                        }
                        Switch(checked = enterSendsPref, onCheckedChange = { enterSendsPref = it; saveBoolPref("enter_to_send", it) })
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Статус «печатает…»", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (typingPref) "Собеседник видит, когда вы печатаете" else "Выключено — никто не увидит, что вы печатаете",
                                color = TextSecondary, fontSize = 12.sp,
                            )
                        }
                        Switch(checked = typingPref, onCheckedChange = { typingPref = it; saveBoolPref("show_typing", it) })
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Размер текста в чате", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Привет! Как дела?", color = TextPrimary, fontSize = (16f * textScaleFor(textSizeKey)).sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextSizeOptions.forEach { (key, label) ->
                            val sel = key == textSizeKey
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) Accent else HoverBg)
                                    .clickable { textSizeKey = key; saveStringPref("chat_text_size", key) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(label, color = if (sel) Color.White else TextSecondary, fontWeight = FontWeight.Medium) }
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

            SectionTitle("PINGGRAM PREMIUM")
            SettingsCard {
                SettingsLinkRow(
                    Icons.Default.Star, Color(0xFFFFC107), "PingGram Premium",
                    if (userData?.isPremium == true) "У тебя есть Premium" else "Активировать и посмотреть возможности",
                ) { showPremium = true }
            }

            SectionTitle("ИНФО")
            SettingsCard {
                SettingsLinkRow(Icons.Default.Shield, TextSecondary, "Политика конфиденциальности", null) {
                    if (PRIVACY_POLICY_URL.isNotBlank()) uriHandler.openUri(PRIVACY_POLICY_URL) else showPrivacy = true
                }
            }

            Spacer(Modifier.height(28.dp))
            TextButton(
                onClick = { scope.launch { SharedRepo.signOut(); onSignedOut() } },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Выйти из аккаунта", color = DangerColor, fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(16.dp))
            CreditsFooter { showAdmin = true }
        }
    }
    if (showAdmin) AdminPanelDialog(onDismiss = { showAdmin = false })
    if (showPremium) PremiumDialog(userData?.isPremium == true, myUid, onDismiss = { showPremium = false })
    if (showPrivacy) PrivacyDialog(onDismiss = { showPrivacy = false })
}

// Подпись авторства. Пять нажатий подряд (не реже раза в 2 секунды) открывают панель разработчика —
// как CreditsFooter на Android; сама панель дополнительно закрыта PIN-кодом.
@Composable
private fun CreditsFooter(onSecretUnlock: () -> Unit) {
    var tapCount by remember { mutableStateOf(0) }
    var lastTap by remember { mutableStateOf(0L) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    val now = nowEpochSeconds()
                    tapCount = if (now - lastTap > 2) 1 else tapCount + 1
                    lastTap = now
                    if (tapCount >= 5) { tapCount = 0; onSecretUnlock() }
                }
                .padding(8.dp),
        ) {
            Text("by Ivashka", color = TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
            Text("by Claude", color = TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

// Панель разработчика (как DeveloperScreen на Android): PIN → статистика и список пользователей с
// переключателями Premium / Dev и удалением. PIN берётся из настройки ADMIN_PIN (local.properties или
// переменная окружения), а не зашит в код приложения. Чужие документы правила Firestore
// разрешают менять только аккаунтам с isDeveloper = true.
@Composable
private fun AdminPanelDialog(onDismiss: () -> Unit) {
    val expectedPin = remember { localSetting("ADMIN_PIN") }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }
    var users by remember { mutableStateOf<List<SharedUser>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<SharedUser?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            users = SharedRepo.getAllUsers()
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Панель разработчика") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        text = {
            Column(Modifier.widthIn(max = 540.dp).fillMaxWidth()) {
                if (!unlocked) {
                    if (expectedPin.isBlank()) {
                        Text(
                            "PIN не настроен: добавьте ADMIN_PIN=… в local.properties (корень проекта) и перезапустите приложение",
                            color = TextSecondary, fontSize = 13.sp,
                        )
                    } else {
                        OutlinedTextField(
                            value = pin, onValueChange = { pin = it; pinError = false },
                            label = { Text("PIN-код") }, singleLine = true, isError = pinError,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                        )
                        if (pinError) Text("Неверный PIN", color = DangerColor, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { if (pin == expectedPin) { unlocked = true; reload() } else { pinError = true; pin = "" } },
                            enabled = pin.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        ) { Text("Войти") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AdminStat("👥 Пользователи", users.size.toString(), Modifier.weight(1f))
                        AdminStat("🟢 Онлайн", users.count { it.data.isOnline }.toString(), Modifier.weight(1f))
                    }
                    msg?.let { Text(it, color = DangerColor, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.height(8.dp))
                    if (loading) {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().height(340.dp)) {
                            items(users, key = { it.uid }) { u ->
                                val uName = u.data.displayName.ifBlank { u.data.username.ifBlank { "Без имени" } }
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(u.data.photoUrl, uName, 38.dp, frame = if (u.data.isPremium) frameColorFor(u.data.premiumFrame) else null)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(uName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                            if (u.data.isPremium) Text("  " + (PREMIUM_BADGE_EMOJI[u.data.premiumBadgeIcon] ?: "⭐"), fontSize = 12.sp)
                                            if (u.data.isOnline) Text("  ● онлайн", color = OnlineColor, fontSize = 11.sp)
                                        }
                                        Text(
                                            listOf(if (u.data.username.isNotBlank()) "@" + u.data.username else "", u.data.email)
                                                .filter { it.isNotBlank() }.joinToString(" · "),
                                            color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            SharedRepo.setUserPremium(u.uid, !u.data.isPremium).onFailure { msg = it.message ?: "Не удалось изменить Premium" }
                                            reload()
                                        }
                                    }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.Star, "Premium", tint = if (u.data.isPremium) Accent else TextSecondary)
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            SharedRepo.setUserDeveloper(u.uid, !u.data.isDeveloper).onFailure { msg = it.message ?: "Не удалось изменить Dev" }
                                            reload()
                                        }
                                    }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.Code, "Dev", tint = if (u.data.isDeveloper) Accent else TextSecondary)
                                    }
                                    IconButton(onClick = { confirmDelete = u }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.Delete, "Удалить", tint = DangerColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )

    confirmDelete?.let { u ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить пользователя?") },
            text = { Text("Удалится документ профиля «" + u.data.displayName.ifBlank { u.data.username } + "». Нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        SharedRepo.deleteUserDocument(u.uid).onFailure { msg = it.message ?: "Не удалось удалить" }
                        reload()
                    }
                }) { Text("Удалить", color = DangerColor) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun AdminStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = HoverBg) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = TextSecondary, fontSize = 12.sp)
            Text(value, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// Пользовательское соглашение — тот же текст, что в Android-приложении.
@Composable
private fun TermsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Условия использования") },
        text = {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(TERMS_TEXT, color = TextSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно", color = Accent) } },
    )
}

// На Android это один и тот же документ: условия использования = политика конфиденциальности.
private const val TERMS_TEXT = PRIVACY_POLICY_TEXT


@Composable
private fun ChatScreen(chatId: String, title: String, photoUrl: String, onClose: () -> Unit, compact: Boolean = false) {
    val myUid = SharedRepo.currentUid
    var chat by remember { mutableStateOf<SChat?>(null) }
    var me by remember { mutableStateOf<SUser?>(null) }
    var partner by remember { mutableStateOf<SUser?>(null) }
    var messages by remember { mutableStateOf(emptyList<SharedMessage>()) }
    var draft by remember { mutableStateOf("") }
    var showAttach by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var showGifs by remember { mutableStateOf(false) }
    var wallpaperPhoto by remember { mutableStateOf<ImageBitmap?>(null) }
    val bgPreset = me?.chatWallpaperPreset ?: ""
    val bgUrl = me?.chatBackground ?: ""
    val bgData = me?.chatBackgroundData ?: ""
    LaunchedEffect(bgPreset, bgUrl, bgData) {
        wallpaperPhoto = when {
            bgPreset == "mountain" -> loadWallpaperResource("wallpaper_mountain")?.let { decodeImageBitmap(it) }
            bgPreset.isNotBlank() -> null
            bgUrl.isNotBlank() -> fetchBytes(bgUrl).getOrNull()?.let { decodeImageBitmap(it) }
            bgData.isNotBlank() -> decodeBase64Safe(bgData)?.let { decodeImageBitmap(it) }
            else -> null
        }
    }
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
    val enterSends = remember { loadBoolPref("enter_to_send", true) }
    val broadcastTyping = remember { loadBoolPref("show_typing", true) }
    val textScale = remember { textScaleFor(loadStringPref("chat_text_size", "MEDIUM")) }
    var showProfile by remember { mutableStateOf(false) }
    var viewerImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

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
    val canSeeDeleted = showDeletedPref && me?.isDeveloper == true
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
        if (myUid == null || !broadcastTyping) return@LaunchedEffect
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

    // Видео-кружок: открывается камера (фронтальная), после съёмки — загрузка и отправка.
    fun sendVideoNote() {
        showAttach = false
        if (myUid == null) return
        scope.launch {
            val f = try { recordVideoNote() } catch (e: Exception) { error = e.message; null } ?: return@launch
            uploading = true
            error = null
            cloudinaryUpload(f.bytes, f.name, "video", f.mimeType)
                .onSuccess { url ->
                    val name = SharedRepo.getUser(myUid)?.data?.displayName ?: ""
                    try { SharedRepo.sendMedia(chatId, SMessageType.VIDEO_NOTE, myUid, name, url, f.name, f.size) }
                    catch (e: Exception) { error = e.message ?: "Не удалось отправить" }
                }
                .onFailure { e -> error = e.message ?: "Не удалось загрузить кружок" }
            uploading = false
        }
    }

    // Голосовое: нажали микрофон — идёт запись, нажали ещё раз (■) — загрузка и отправка.
    fun toggleVoice() {
        if (myUid == null) return
        scope.launch {
            if (!recording) {
                error = null
                startVoiceRecording()
                    .onSuccess { recording = true }
                    .onFailure { error = it.message ?: "Не удалось начать запись" }
            } else {
                recording = false
                val f = stopVoiceRecording()
                if (f == null) { error = "Запись слишком короткая"; return@launch }
                uploading = true
                // Cloudinary хранит аудио под resource_type "video".
                cloudinaryUpload(f.bytes, f.name, "video", f.mimeType)
                    .onSuccess { url ->
                        val name = SharedRepo.getUser(myUid)?.data?.displayName ?: ""
                        try { SharedRepo.sendMedia(chatId, SMessageType.VOICE, myUid, name, url, f.name, f.size) }
                        catch (e: Exception) { error = e.message ?: "Не удалось отправить" }
                    }
                    .onFailure { e -> error = e.message ?: "Не удалось загрузить голосовое" }
                uploading = false
            }
        }
    }

    DisposableEffect(chatId) { onDispose { cancelVoiceRecording() } }

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

    fun sendGif(url: String) {
        showGifs = false
        if (myUid == null) return
        scope.launch {
            try {
                val name = SharedRepo.getUser(myUid)?.data?.displayName ?: ""
                SharedRepo.sendGif(chatId, url, myUid, name)
            } catch (e: Exception) { error = e.message ?: "Не удалось отправить GIF" }
        }
    }

    fun sendImageSticker(stickerName: String) {
        showStickers = false
        if (myUid == null) return
        val bytes = loadStickerResource(stickerName)
        if (bytes == null) { error = "Не удалось прочитать стикер"; return }
        scope.launch {
            try {
                val name = SharedRepo.getUser(myUid)?.data?.displayName ?: ""
                SharedRepo.sendImageSticker(chatId, encodeBase64(bytes), myUid, name)
            } catch (e: Exception) { error = e.message ?: "Не удалось отправить стикер" }
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
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = if (compact) 4.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (compact) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Назад", tint = TextPrimary) }
                }
                // Клик по шапке личного чата открывает профиль собеседника.
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(enabled = partnerUid != null || (c != null && (c.type == SChatType.GROUP || c.type == SChatType.CHANNEL))) { showProfile = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                Avatar(photoUrl = photoUrl, label = title, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = when {
                        someoneTyping -> "печатает…"
                        c != null && c.type != SChatType.PRIVATE && c.type != SChatType.SAVED -> "${c.memberIds.size} ${if (c.type == SChatType.CHANNEL) "подписчиков" else "участн."}"
                        partner?.isOnline == true -> "в сети"
                        partner != null -> formatLastSeen(tsSeconds(partner?.lastSeen))
                        else -> ""
                    }
                    if (sub.isNotEmpty()) Text(sub, color = if (someoneTyping || partner?.isOnline == true) Accent else TextSecondary, fontSize = 12.sp)
                }
                }
                if (partnerUid != null && CallCenter.controller != null && !iBlocked && !blockedByPartner) {
                    IconButton(onClick = {
                        val ctrl = CallCenter.controller
                        val uid = myUid
                        if (ctrl != null && uid != null) {
                            ctrl.startOutgoing(uid, me?.displayName ?: "", me?.photoUrl ?: "", "", partnerUid, title, photoUrl, video = true)
                        }
                    }) { Icon(Icons.Default.Videocam, "Видеозвонок", tint = Accent) }
                    IconButton(onClick = {
                        val ctrl = CallCenter.controller
                        val uid = myUid
                        if (ctrl != null && uid != null) {
                            ctrl.startOutgoing(uid, me?.displayName ?: "", me?.photoUrl ?: "", "", partnerUid, title, photoUrl)
                        }
                    }) { Icon(Icons.Default.Call, "Позвонить", tint = Accent) }
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

        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().chatWallpaper(bgPreset, wallpaperPhoto).padding(horizontal = if (compact) 10.dp else 16.dp)) {
            items(visible, key = { it.id }) { msg ->
                val mine = msg.data.senderId == myUid
                val ticks = if (mine && partnerUid != null) {
                    val ms = tsSeconds(msg.data.timestamp)
                    if (ms != null && partnerReadSec != null && ms <= partnerReadSec) "✓✓" else "✓"
                } else ""
                MessageRow(
                    msg = msg, mine = mine, ticks = ticks, isPinned = msg.id == c?.pinnedMessageId, scope = scope, textScale = textScale,
                    senderLabel = if (!mine && c != null && c.type == SChatType.GROUP) msg.data.senderName.ifBlank { null } else null,
                    bubbleColor = if (mine) bubbleColorFor(me?.premiumBubbleColor) else null,
                    onEdit = { editing = msg; draft = msg.data.text },
                    onDelete = { scope.launch { SharedRepo.deleteMessage(chatId, msg.id) } },
                    onPin = { scope.launch { SharedRepo.pinMessage(chatId, if (msg.id == c?.pinnedMessageId) "" else msg.id) } },
                    onCopy = { clipboard.setText(AnnotatedString(msg.data.text)) },
                    compact = compact,
                    onOpenImage = { viewerImage = it },
                )
            }
        }

        if (showStickers) {
            Surface(color = BgSecondary) {
                LazyVerticalGrid(columns = GridCells.Adaptive(46.dp), modifier = Modifier.fillMaxWidth().height(190.dp).padding(8.dp)) {
                    gridItems(IMAGE_STICKER_NAMES) { n -> ImageStickerThumb(n) { sendImageSticker(n) } }
                    gridItems(CHAT_STICKERS) { s ->
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).clickable { sendSticker(s) }, contentAlignment = Alignment.Center) {
                            Text(s, fontSize = 26.sp)
                        }
                    }
                }
            }
        }

        if (showGifs) GifPanel(onPick = { sendGif(it) })

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
                    if (recording) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(DangerColor))
                            Spacer(Modifier.width(8.dp))
                            Text("Идёт запись… нажмите ■, чтобы отправить", color = DangerColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { cancelVoiceRecording(); recording = false }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, "Отмена", tint = TextSecondary, modifier = Modifier.size(16.dp)) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(if (compact) 6.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { showAttach = !showAttach }, enabled = !uploading, modifier = Modifier.size(if (compact) 40.dp else 48.dp)) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Прикрепить", tint = if (showAttach) Accent else TextSecondary)
                            }
                            DropdownMenu(expanded = showAttach, onDismissRequest = { showAttach = false }) {
                                DropdownMenuItem(text = { Text("Фото") }, leadingIcon = { Icon(Icons.Default.Image, null) }, onClick = { uploadAndSend(MediaKind.IMAGE) })
                                DropdownMenuItem(text = { Text("Видео") }, leadingIcon = { Icon(Icons.Default.Movie, null) }, onClick = { uploadAndSend(MediaKind.VIDEO) })
                                if (videoNoteSupported()) {
                                    DropdownMenuItem(text = { Text("Видео-кружок") }, leadingIcon = { Icon(Icons.Default.Videocam, null) }, onClick = { sendVideoNote() })
                                }
                                DropdownMenuItem(text = { Text("Файл") }, leadingIcon = { Icon(Icons.Default.Description, null) }, onClick = { uploadAndSend(MediaKind.FILE) })
                            }
                        }
                        IconButton(onClick = { showStickers = !showStickers; if (showStickers) showGifs = false }, modifier = Modifier.size(if (compact) 40.dp else 48.dp)) {
                            Icon(Icons.Default.EmojiEmotions, "Стикеры", tint = if (showStickers) Accent else TextSecondary)
                        }
                        IconButton(onClick = { showGifs = !showGifs; if (showGifs) showStickers = false }, modifier = Modifier.size(if (compact) 40.dp else 48.dp)) {
                            Icon(Icons.Default.Gif, "GIF", tint = if (showGifs) Accent else TextSecondary)
                        }
                        if (uploading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f).onPreviewKeyEvent { ev ->
                                // Если Enter выключен как «отправить» — отправляем по Ctrl+Enter.
                                if (!enterSends && ev.type == KeyEventType.KeyDown && ev.key == Key.Enter && ev.isCtrlPressed) { sendDraft(); true } else false
                            },
                            placeholder = { Text(if (enterSends) "Сообщение" else "Сообщение (Ctrl+Enter — отправить)", color = TextSecondary) },
                            singleLine = enterSends,
                            maxLines = if (enterSends) 1 else 6,
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
                        val showMic = (recording || draft.isBlank()) && editing == null && voiceRecordingSupported()
                        if (showMic) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(if (recording) DangerColor else Accent).clickable(enabled = !uploading) { toggleVoice() },
                                contentAlignment = Alignment.Center,
                            ) { Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, if (recording) "Отправить голосовое" else "Записать голосовое", tint = Color.White) }
                        } else if (compact) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(Accent).clickable { sendDraft() },
                                contentAlignment = Alignment.Center,
                            ) { Icon(if (editing != null) Icons.Default.Check else Icons.Default.Send, if (editing != null) "Сохранить" else "Отправить", tint = Color.White) }
                        } else {
                            Button(onClick = { sendDraft() }, shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                                Text(if (editing != null) "Сохранить" else "Отправить")
                            }
                        }
                    }
                }
            }
        }
    }

    viewerImage?.let { img -> ImageViewer(img, onClose = { viewerImage = null }) }
    if (showProfile && partnerUid != null) UserProfileDialog(partnerUid, onDismiss = { showProfile = false }, onWrite = null)
    if (showProfile && c != null && (c.type == SChatType.GROUP || c.type == SChatType.CHANNEL)) {
        ChatInfoDialog(chatId, onDismiss = { showProfile = false }, onLeft = { showProfile = false; onClose() })
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
    msg: SharedMessage, mine: Boolean, ticks: String, isPinned: Boolean, scope: kotlinx.coroutines.CoroutineScope, textScale: Float, senderLabel: String? = null, bubbleColor: Color? = null,
    onEdit: () -> Unit, onDelete: () -> Unit, onPin: () -> Unit, onCopy: () -> Unit,
    compact: Boolean = false,
    onOpenImage: (androidx.compose.ui.graphics.ImageBitmap) -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(false) }
    val longPress = if (compact) Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true }) else Modifier
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
        if (mine && !compact) menuButton()
        if (d.type == SMessageType.STICKER && !d.isDeleted && (d.sticker.isNotBlank() || d.mediaUrl.isNotBlank() || d.mediaData.isNotBlank())) {
            Column(longPress, horizontalAlignment = Alignment.End) {
                if (d.sticker.isNotBlank()) Text(d.sticker, fontSize = 40.sp)
                else ChatImage(d.mediaUrl, msg.id, size = 130.dp, mediaData = d.mediaData)
                Text(meta, color = TextSecondary, fontSize = 10.sp)
            }
        } else if (d.type == SMessageType.IMAGE && !d.isDeleted && (d.mediaUrl.isNotBlank() || d.mediaData.isNotBlank())) {
            // Фото — без цветной рамки пузыря: только скруглённая картинка, время поверх неё.
            Box(longPress) {
                MessageContent(msg, scope, fg = fg, textScale = textScale, onOpenImage = onOpenImage)
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) { Text(meta, color = Color.White, fontSize = 10.sp) }
            }
        } else {
            Box(
                modifier = Modifier
                    .then(longPress)
                    .background(
                        if (mine) (bubbleColor ?: Accent) else BubbleIn,
                        shape = RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (mine) 16.dp else 4.dp,
                            bottomEnd = if (mine) 4.dp else 16.dp,
                        ),
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .widthIn(max = if (compact) 300.dp else 460.dp),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    if (senderLabel != null && !d.isDeleted) {
                        Text(senderLabel, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
                    }
                    if (d.isDeleted) {
                        Text("удалено: " + d.text.ifBlank { "[вложение]" }, color = fg.copy(alpha = 0.6f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    } else {
                        MessageContent(msg, scope, fg = fg, textScale = textScale)
                    }
                    Text(meta, color = fg.copy(alpha = 0.7f), fontSize = 10.sp)
                }
            }
        }
        if (!mine && !compact) menuButton()
        if (compact) Box(Modifier.size(0.dp)) {
            MessageActionsMenu(menu, { menu = false }, d.type == SMessageType.TEXT, d.isDeleted, mine, isPinned, onCopy, onEdit, onDelete, onPin)
        }
    }
}

// Содержимое одного пузыря — вынесено из ChatScreen, чтобы код отправки/приёма и код
// разбора типа сообщения не перепутывались в одной гигантской функции.
@Composable
private fun MessageContent(msg: SharedMessage, scope: kotlinx.coroutines.CoroutineScope, fg: Color, textScale: Float = 1f, onOpenImage: ((androidx.compose.ui.graphics.ImageBitmap) -> Unit)? = null) {
    when (msg.data.type) {
        SMessageType.TEXT -> Text(msg.data.text, color = fg, fontSize = (16f * textScale).sp)
        SMessageType.IMAGE -> ChatImage(msg.data.mediaUrl, msg.id, mediaData = msg.data.mediaData, onOpen = onOpenImage)
        SMessageType.GIF -> if (msg.data.mediaUrl.isNotBlank()) ChatGif(msg.data.mediaUrl, msg.id) else ChatImage("", msg.id)
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
        SMessageType.VIDEO_NOTE -> ChatVideoNote(
            url = msg.data.mediaUrl,
            key = msg.id,
            fileName = "video_note_${msg.id}.mp4",
            scope = scope,
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
        SMessageType.STICKER -> if (msg.data.mediaUrl.isNotBlank() || msg.data.mediaData.isNotBlank()) ChatImage(msg.data.mediaUrl, msg.id, size = 130.dp, mediaData = msg.data.mediaData)
            else Text("[Стикер] — доступно в мобильном приложении", color = fg)
    }
}

// Фото/GIF/картиночный стикер — скачивает байты по Cloudinary-ссылке и рисует как
// Image(...); без mediaUrl показать нечего — это либо совсем старое тестовое сообщение
// из прошлых версий приложения (до перехода на Cloudinary), либо десктоп-клиент, в
// отличие от Android, не хранит Base64-запасной путь. Тапом по неудавшейся картинке
// можно попробовать перезагрузить — на случай, если проблема была в сети, а не в ссылке.
@Composable
private fun ChatImage(mediaUrl: String, key: String, size: androidx.compose.ui.unit.Dp = 220.dp, mediaData: String = "", onOpen: ((androidx.compose.ui.graphics.ImageBitmap) -> Unit)? = null) {
    var bitmap by remember(key) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var state by remember(key) { mutableStateOf(if (mediaUrl.isBlank() && mediaData.isBlank()) "no_url" else "loading") }
    var reloadTick by remember(key) { mutableStateOf(0) }
    LaunchedEffect(mediaUrl, mediaData, reloadTick) {
        if (mediaUrl.isBlank()) {
            // Нет ссылки — пробуем Base64 из mediaData (так Android хранит картиночные стикеры и старые фото).
            if (mediaData.isBlank()) { state = "no_url"; return@LaunchedEffect }
            val decoded = decodeBase64Safe(mediaData)?.let { decodeImageBitmap(it) }
            if (decoded != null) { bitmap = decoded; state = "ok" } else state = "decode_error"
            return@LaunchedEffect
        }
        state = "loading"
        fetchBytes(mediaUrl)
            .onSuccess { bytes ->
                val decoded = decodeImageBitmap(bytes)
                if (decoded != null) { bitmap = decoded; state = "ok" } else state = "decode_error"
            }
            .onFailure { state = "network_error" }
    }
    // Фото (onOpen != null): без квадратной рамки — берём пропорции самой картинки, тап открывает просмотр с зумом.
    val loaded = bitmap
    val photoMode = onOpen != null && state == "ok" && loaded != null
    Box(
        modifier = (if (photoMode) Modifier.widthIn(max = 260.dp).heightIn(max = 340.dp).aspectRatio(loaded!!.width.toFloat() / loaded.height.toFloat()) else Modifier.size(size))
            .clip(RoundedCornerShape(12.dp))
            .let { if (photoMode) it else it.background(BubbleIn.copy(alpha = 0.6f)) }
            .let {
                if (state != "ok" && state != "loading") it.clickable { reloadTick++ }
                else if (photoMode) it.clickable { onOpen?.invoke(loaded!!) }
                else it
            },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            "ok" -> androidx.compose.foundation.Image(bitmap!!, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = if (photoMode) ContentScale.Crop else ContentScale.Fit)
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
// Видео-кружок: круглая превьюшка (первый кадр из Cloudinary) и кнопка ▶. По нажатию файл
// скачивается и открывается системным плеером — проигрывателя прямо в чате пока нет.
@Composable
private fun ChatVideoNote(url: String, key: String, fileName: String, scope: kotlinx.coroutines.CoroutineScope) {
    var bitmap by remember(key) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var busy by remember(key) { mutableStateOf(false) }
    var err by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(url) {
        if (url.isBlank() || !url.contains("/video/upload/")) return@LaunchedEffect
        val thumb = url.replace("/video/upload/", "/video/upload/so_0,w_400,h_400,c_fill/").substringBeforeLast('.') + ".jpg"
        fetchBytes(thumb).onSuccess { b -> bitmap = decodeImageBitmap(b) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(200.dp).clip(CircleShape).background(Color(0x55000000))
                .clickable(enabled = url.isNotBlank() && !busy) {
                    busy = true
                    err = null
                    scope.launch {
                        downloadAndOpen(url, fileName).onFailure { e -> err = e.message ?: "Не удалось открыть" }
                        busy = false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { androidx.compose.foundation.Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp), color = Color.White, strokeWidth = 3.dp)
            } else {
                Box(Modifier.size(54.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, "Открыть", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
        if (url.isBlank()) Text("старое сообщение", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        err?.let { Text(it, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall) }
    }
}

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


// ── История звонков (вкладка «Звонки»): те же виды записей и фильтры, что CallHistoryScreen на Android.
// Нажатие на строку открывает личный чат с этим человеком (сами звонки на iPhone — следующий этап). ──
private enum class CallKind { INCOMING_OK, MISSED, INCOMING_DECLINED, OUTGOING_OK, OUTGOING_NO_ANSWER, OUTGOING_DECLINED, IN_PROGRESS }

private enum class CallFilter(val title: String) { ALL("Все"), MISSED("Пропущенные"), INCOMING("Входящие"), OUTGOING("Исходящие") }

private const val CALL_RINGING_STALE_SEC = 90L          // RINGING дольше — звонок уже никто не ждёт
private const val CALL_ACTIVE_STALE_SEC = 3L * 3600L    // ACTIVE дольше — скорее всего приложение закрыли, не завершив звонок

private fun callKindOf(c: SCall, myUid: String?): CallKind {
    val outgoing = c.callerId == myUid
    val age = nowEpochSeconds() - (tsSeconds(c.startedAt) ?: 0L)
    return when (c.status) {
        SCallStatus.ENDED -> if (outgoing) CallKind.OUTGOING_OK else CallKind.INCOMING_OK
        SCallStatus.DECLINED -> if (outgoing) CallKind.OUTGOING_DECLINED else CallKind.INCOMING_DECLINED
        SCallStatus.MISSED -> if (outgoing) CallKind.OUTGOING_NO_ANSWER else CallKind.MISSED
        SCallStatus.RINGING ->
            if (age > CALL_RINGING_STALE_SEC) (if (outgoing) CallKind.OUTGOING_NO_ANSWER else CallKind.MISSED) else CallKind.IN_PROGRESS
        SCallStatus.ACTIVE ->
            if (age > CALL_ACTIVE_STALE_SEC) (if (outgoing) CallKind.OUTGOING_OK else CallKind.INCOMING_OK) else CallKind.IN_PROGRESS
    }
}

private fun callKindLabel(k: CallKind): String = when (k) {
    CallKind.INCOMING_OK -> "Входящий · принят"
    CallKind.MISSED -> "Пропущенный"
    CallKind.INCOMING_DECLINED -> "Входящий · отклонён"
    CallKind.OUTGOING_OK -> "Исходящий · состоялся"
    CallKind.OUTGOING_NO_ANSWER -> "Исходящий · без ответа"
    CallKind.OUTGOING_DECLINED -> "Исходящий · отклонён"
    CallKind.IN_PROGRESS -> "Идёт сейчас"
}

@Composable
private fun CallHistoryPane(compact: Boolean, onOpenChat: (OpenChat) -> Unit) {
    val myUid = SharedRepo.currentUid
    var calls by remember { mutableStateOf(emptyList<SharedCall>()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(CallFilter.ALL) }
    val peers = remember { mutableStateMapOf<String, SUser?>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(myUid) {
        val uid = myUid ?: return@LaunchedEffect
        try {
            SharedRepo.observeCallHistory(uid).collect { calls = it; loaded = true }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "неизвестная ошибка"
            loaded = true
        }
    }

    fun peerUidOf(c: SharedCall): String = if (c.data.callerId == myUid) c.data.receiverId else c.data.callerId
    LaunchedEffect(calls) {
        calls.map { peerUidOf(it) }.distinct().forEach { id ->
            if (id.isNotEmpty() && !peers.containsKey(id)) peers[id] = SharedRepo.getUser(id)?.data
        }
    }

    val shown = calls.filter { c ->
        when (filter) {
            CallFilter.ALL -> true
            CallFilter.MISSED -> callKindOf(c.data, myUid) == CallKind.MISSED
            CallFilter.INCOMING -> c.data.receiverId == myUid
            CallFilter.OUTGOING -> c.data.callerId == myUid
        }
    }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        Text(
            "Звонки", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (compact) 12.dp else 20.dp, bottom = 4.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CallFilter.values().forEach { f ->
                val selected = filter == f
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Accent.copy(alpha = 0.2f) else BgSecondary)
                        .clickable { filter = f }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(f.title, color = if (selected) Accent else TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (compact && CallCenter.controller == null) {
            Text(
                "Звонки в этой сборке выключены: нативный движок WebRTC не подключён (CallPlatform.engine = null). " +
                    "Значит, на телефон попала сборка без WebRTCCallEngine.swift / без строки CallPlatform.shared.engine в iOSApp.swift.",
                color = DangerColor, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        val err = error
        when {
            !loaded -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Accent) }
            err != null && calls.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Text("Не удалось загрузить историю звонков: $err", color = DangerColor, textAlign = TextAlign.Center)
            }
            shown.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📞", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(if (filter == CallFilter.ALL) "Звонков пока нет" else "Здесь пусто", color = TextSecondary, fontSize = 16.sp)
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { c ->
                    val outgoing = c.data.callerId == myUid
                    val kind = callKindOf(c.data, myUid)
                    val missed = kind == CallKind.MISSED
                    val isVideo = c.data.type == SCallType.VIDEO
                    val peer = peers[peerUidOf(c)]
                    val name = peer?.displayName?.takeIf { it.isNotBlank() }
                        ?: (if (outgoing) "" else c.data.callerName).ifBlank { peer?.username?.takeIf { it.isNotBlank() } ?: "Пользователь" }
                    val photo = peer?.photoUrl?.takeIf { it.isNotEmpty() } ?: (if (outgoing) "" else c.data.callerPhotoUrl)
                    val seconds = tsSeconds(c.data.startedAt)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                val uid = myUid ?: return@clickable
                                scope.launch {
                                    try {
                                        val chatId = SharedRepo.getOrCreatePrivateChat(uid, peerUidOf(c))
                                        onOpenChat(OpenChat(chatId, name, photo))
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (_: Exception) { }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(photo, name, 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                name, color = if (missed) DangerColor else TextPrimary, fontSize = 16.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    when {
                                        missed -> Icons.Default.CallMissed
                                        outgoing -> Icons.Default.CallMade
                                        else -> Icons.Default.CallReceived
                                    },
                                    null, modifier = Modifier.size(14.dp), tint = if (missed) DangerColor else TextSecondary,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    (if (isVideo) "Видео · " else "") + callKindLabel(kind),
                                    color = if (missed) DangerColor else TextSecondary, fontSize = 13.sp,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (seconds != null) formatChatTime(seconds) else "", color = TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(2.dp))
                            Icon(if (isVideo) Icons.Default.Videocam else Icons.Default.Call, null, tint = Accent, modifier = Modifier.size(20.dp))
                        }
                    }
                    Box(Modifier.padding(start = 78.dp).fillMaxWidth().height(0.5.dp).background(HoverBg))
                }
            }
        }
    }
}


// ── Звонки: наблюдатель входящих и полноэкранный экран звонка поверх всего приложения.
// Входящий звонок виден, только пока приложение открыто (пуши на закрытое приложение
// на iPhone без платного аккаунта Apple невозможны). ──
@Composable
private fun CallHost(compact: Boolean) {
    val controller = CallCenter.controller ?: return
    val myUid = SharedRepo.currentUid

    LaunchedEffect(myUid) {
        val uid = myUid ?: return@LaunchedEffect
        try {
            SharedRepo.observeIncomingCall(uid).collect { call ->
                if (call != null && controller.phase == CallPhase.IDLE) {
                    val now = nowEpochSeconds()
                    val age = now - (tsSeconds(call.data.startedAt) ?: now)
                    if (age < 60) {
                        val caller = try { SharedRepo.getUser(call.data.callerId)?.data } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
                        val name = caller?.displayName?.takeIf { it.isNotBlank() }
                            ?: call.data.callerName.ifBlank { "Пользователь" }
                        val photo = caller?.photoUrl?.takeIf { it.isNotEmpty() } ?: call.data.callerPhotoUrl
                        controller.onIncoming(call, name, photo)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) { }
    }

    if (controller.phase != CallPhase.IDLE) CallOverlay(controller, compact)
}

@Composable
private fun CallOverlay(controller: CallController, compact: Boolean) {
    val phase = controller.phase
    val showVideo = controller.isVideo && (phase == CallPhase.ACTIVE || phase == CallPhase.CONNECTING)
    Box(Modifier.fillMaxSize().background(BgPrimary).pointerInput(Unit) { detectTapGestures { } }) {
        if (showVideo) CallVideoSurface(remote = true, modifier = Modifier.fillMaxSize())
        SafeArea(enabled = compact, topColor = if (showVideo) Color.Transparent else BgPrimary) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(if (showVideo) 8.dp else 48.dp))
                if (!showVideo) {
                    Avatar(photoUrl = controller.peerPhoto, label = controller.peerName.ifBlank { "?" }, size = 120.dp)
                    Spacer(Modifier.height(20.dp))
                }
                Text(
                    controller.peerName.ifBlank { "Пользователь" }, color = if (showVideo) Color.White else TextPrimary, fontSize = if (showVideo) 20.sp else 26.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                val status = when (phase) {
                    CallPhase.DIALING -> "Вызываем…"
                    CallPhase.INCOMING -> if (controller.isVideo) "Входящий видеозвонок" else "Входящий звонок"
                    CallPhase.CONNECTING -> "Соединение…"
                    CallPhase.ACTIVE -> {
                        val m = controller.seconds / 60
                        val sec = controller.seconds % 60
                        "${if (m < 10) "0$m" else "$m"}:${if (sec < 10) "0$sec" else "$sec"}"
                    }
                    CallPhase.ENDED -> controller.message ?: "Звонок завершён"
                    CallPhase.IDLE -> ""
                }
                Text(status, color = if (phase == CallPhase.ENDED && controller.message != null) DangerColor else if (showVideo) Color.White else TextSecondary, fontSize = 16.sp, textAlign = TextAlign.Center)

                Spacer(Modifier.weight(1f))

                when (phase) {
                    CallPhase.INCOMING -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CallRoundButton(Icons.Default.CallEnd, "Отклонить", DangerColor) { controller.decline() }
                        CallRoundButton(Icons.Default.Call, "Ответить", Color(0xFF34C759)) { controller.accept() }
                    }
                    CallPhase.ENDED -> {}
                    else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CallRoundButton(
                            if (controller.muted) Icons.Default.MicOff else Icons.Default.Mic,
                            if (controller.muted) "Включить микрофон" else "Микрофон",
                            if (controller.muted) Accent else BgSecondary,
                        ) { controller.toggleMute() }
                        if (controller.isVideo) {
                            CallRoundButton(
                                if (controller.cameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                if (controller.cameraOff) "Включить камеру" else "Камера",
                                if (controller.cameraOff) Accent else BgSecondary,
                            ) { controller.toggleCamera() }
                            CallRoundButton(Icons.Default.Cameraswitch, "Перевернуть", BgSecondary) { controller.switchCamera() }
                        }
                        CallRoundButton(Icons.Default.CallEnd, "Завершить", DangerColor) { controller.hangUp() }
                        if (!controller.isVideo) {
                            CallRoundButton(
                                if (controller.speakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                "Динамик",
                                if (controller.speakerOn) Accent else BgSecondary,
                            ) { controller.toggleSpeaker() }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        if (showVideo && !controller.cameraOff) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 16.dp)
                    .size(width = 104.dp, height = 144.dp).clip(RoundedCornerShape(14.dp)),
            ) { CallVideoSurface(remote = false, modifier = Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun CallRoundButton(icon: ImageVector, label: String, bg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
