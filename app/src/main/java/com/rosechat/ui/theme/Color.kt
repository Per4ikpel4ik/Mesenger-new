package com.rosechat.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class PingGramPalette(
    val bgPrimary: Color,
    val bgSecondary: Color,
    val bgTertiary: Color,
    val accent: Color,
    val accentLight: Color,
    val accentMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val bubbleOut: Color,
    val bubbleIn: Color,
    val divider: Color,
    val inputBg: Color,
    val online: Color,
    val errorRed: Color,
    val gold: Color
)

// Telegram-style dark + pink accent — оригинальная (и по умолчанию) тема
val DarkPalette = PingGramPalette(
    bgPrimary     = Color(0xFF0F0F0F),
    bgSecondary   = Color(0xFF1C1C1E),
    bgTertiary    = Color(0xFF2C2C2E),
    accent        = Color(0xFFFF2D78),
    accentLight   = Color(0xFFFF6B9D),
    accentMuted   = Color(0xFF8B1A4A),
    textPrimary   = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E8E93),
    textTertiary  = Color(0xFF636366),
    bubbleOut     = Color(0xFFFF2D78),
    bubbleIn      = Color(0xFF1C1C1E),
    divider       = Color(0xFF2C2C2E),
    inputBg       = Color(0xFF1C1C1E),
    online        = Color(0xFF34C759),
    errorRed      = Color(0xFFFF3B30),
    gold          = Color(0xFFFFD700)
)

// Светлая тема — тот же розовый акцент, светлая подложка
val LightPalette = PingGramPalette(
    bgPrimary     = Color(0xFFFFFFFF),
    bgSecondary   = Color(0xFFF2F2F7),
    bgTertiary    = Color(0xFFE5E5EA),
    accent        = Color(0xFFFF2D78),
    accentLight   = Color(0xFFFF6B9D),
    accentMuted   = Color(0xFF8B1A4A),
    textPrimary   = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF6E6E73),
    textTertiary  = Color(0xFFA0A0A5),
    bubbleOut     = Color(0xFFFF2D78),
    bubbleIn      = Color(0xFFE9E9EB),
    divider       = Color(0xFFD1D1D6),
    inputBg       = Color(0xFFF2F2F7),
    online        = Color(0xFF34C759),
    errorRed      = Color(0xFFFF3B30),
    gold          = Color(0xFFB8860B)   // на белом фоне ярко-жёлтый золотой плохо читается — берём темнее
)

val LocalPingGramPalette = staticCompositionLocalOf { DarkPalette }

// Раньше акцентный цвет был жёстко зашит в DarkPalette/LightPalette (только розовый) — просили
// "изменение цвета не только чёрно-розовый, но и бело-чёрный и так далее". Вместо переделки всей
// палитры под каждую тему держим один набор акцентных пресетов и накладываем его поверх обычной
// тёмной/светлой палитры (.copy(...)) — фон/текст остаются как были, меняется только акцент.
enum class AccentTheme(val label: String, val accent: Color, val accentLight: Color, val accentMuted: Color) {
    PINK("Розовый", Color(0xFFFF2D78), Color(0xFFFF6B9D), Color(0xFF8B1A4A)),
    MONO("Чёрно-белый", Color(0xFF8E8E93), Color(0xFFC7C7CC), Color(0xFF48484A)),
    BLUE("Синий", Color(0xFF0A84FF), Color(0xFF64B5FF), Color(0xFF0A4A8B)),
    GREEN("Зелёный", Color(0xFF30D158), Color(0xFF7EE8A0), Color(0xFF1B6B33)),
    PURPLE("Фиолетовый", Color(0xFFBF5AF2), Color(0xFFDA9CFF), Color(0xFF6B2C8B));

    companion object {
        fun fromId(id: String): AccentTheme = entries.find { it.name == id } ?: PINK
    }
}

fun PingGramPalette.withAccent(theme: AccentTheme): PingGramPalette = copy(
    accent = theme.accent, accentLight = theme.accentLight, accentMuted = theme.accentMuted, bubbleOut = theme.accent
)

// Ниже — те же самые имена, что были раньше как val Color(...). Теперь это
// Composable-свойства, читающие текущую палитру из CompositionLocal, поэтому
// ни один вызов вроде `color = TextPrimary` по всему проекту менять не нужно —
// он просто становится реактивным на смену темы.
val BgPrimary: Color @Composable get() = LocalPingGramPalette.current.bgPrimary
val BgSecondary: Color @Composable get() = LocalPingGramPalette.current.bgSecondary
val BgTertiary: Color @Composable get() = LocalPingGramPalette.current.bgTertiary
val Accent: Color @Composable get() = LocalPingGramPalette.current.accent
val AccentLight: Color @Composable get() = LocalPingGramPalette.current.accentLight
val AccentMuted: Color @Composable get() = LocalPingGramPalette.current.accentMuted
val TextPrimary: Color @Composable get() = LocalPingGramPalette.current.textPrimary
val TextSecondary: Color @Composable get() = LocalPingGramPalette.current.textSecondary
val TextTertiary: Color @Composable get() = LocalPingGramPalette.current.textTertiary
val BubbleOut: Color @Composable get() = LocalPingGramPalette.current.bubbleOut
val BubbleIn: Color @Composable get() = LocalPingGramPalette.current.bubbleIn
val Divider: Color @Composable get() = LocalPingGramPalette.current.divider
val InputBg: Color @Composable get() = LocalPingGramPalette.current.inputBg
val Online: Color @Composable get() = LocalPingGramPalette.current.online
val ErrorRed: Color @Composable get() = LocalPingGramPalette.current.errorRed
val Gold: Color @Composable get() = LocalPingGramPalette.current.gold
