package com.rosechat.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Цвета рамки аватара для Premium — фиксированные (не зависят от светлой/тёмной темы),
// потому что это декоративный выбор пользователя, а не часть системной палитры.
// "neon" и "rainbow" тоже числятся тут с обычным сплошным цветом — он идёт как база под
// анимированным эффектом (см. PremiumFrameGlow), а не как основной вид рамки.
val PREMIUM_FRAMES = linkedMapOf(
    "gold" to Color(0xFFFFD700),
    "sapphire" to Color(0xFF4A90E2),
    "emerald" to Color(0xFF2ECC71),
    "ruby" to Color(0xFFE91E63),
    "amethyst" to Color(0xFF9B59B6),
    "rose" to Color(0xFFFF4D8D),
    "ice" to Color(0xFF7FDBFF),
    "platinum" to Color(0xFFC7C9CC),
    "neon" to Color(0xFF39FF14),
    "rainbow" to Color(0xFFFFD700),
)

val NEON_GLOW_COLOR = Color(0xFF39FF14)
private val RAINBOW_COLORS = listOf(
    Color(0xFFFF0000), Color(0xFFFF9900), Color(0xFFFFEE00), Color(0xFF33FF00),
    Color(0xFF00CFFF), Color(0xFF7A00FF), Color(0xFFFF00C8), Color(0xFFFF0000),
)

fun premiumFrameColor(id: String): Color = PREMIUM_FRAMES[id] ?: PREMIUM_FRAMES.getValue("gold")

// Значки Premium-бейджа у имени — раньше был только один захардкоженный ⭐ в пяти разных
// местах экрана; теперь это выбор пользователя, а эмодзи достаётся отсюда.
val PREMIUM_BADGE_ICONS = linkedMapOf(
    "star" to "⭐",
    "diamond" to "💎",
    "crown" to "👑",
)
fun premiumBadgeEmoji(id: String): String = PREMIUM_BADGE_ICONS[id] ?: PREMIUM_BADGE_ICONS.getValue("star")

// Цвет собственных исходящих пузырей в чате — виден только самому пользователю (это его
// личная настройка вида, а не то, что видят собеседники, — так же устроены похожие
// функции в других мессенджерах). "default" — обычный цвет темы (BubbleOut), без наложения.
val PREMIUM_BUBBLE_COLORS = linkedMapOf(
    "default" to null,
    "sapphire" to Color(0xFF3D6FB4),
    "emerald" to Color(0xFF1E9E5A),
    "ruby" to Color(0xFFC2185B),
    "amethyst" to Color(0xFF7B4397),
    "rose" to Color(0xFFD6336C),
    "midnight" to Color(0xFF1F2A44),
)
fun premiumBubbleColorOrNull(id: String): Color? = PREMIUM_BUBBLE_COLORS[id]

// Неоновая и радужная рамки — не просто ещё один сплошной цвет из PREMIUM_FRAMES, а
// эффект вокруг обычной рамки. Настоящий blur (RenderEffect) тут не то — эффект должен
// быть виден и на API до 31, где RenderEffect ещё нет, — поэтому свечение имитируется
// несколькими полупрозрачными кольцами растущего радиуса и падающей альфой (тот же приём,
// что и "жидкое стекло" в MainScreen), а вращение радуги — обычным поворотом DrawScope,
// без реальных шейдеров. Рисуется ПОД аватаркой, как отдельный слой в Box до неё, а не
// поверх — иначе он обрежется по CircleShape аватарки и эффекта видно не будет.
@Composable
fun PremiumFrameGlow(frame: String, avatarSize: Dp) {
    when (frame) {
        "neon" -> NeonGlow(avatarSize)
        "rainbow" -> RainbowRing(avatarSize)
        else -> {}
    }
}

@Composable
private fun NeonGlow(avatarSize: Dp) {
    val glowSize = avatarSize + 24.dp
    Canvas(Modifier.size(glowSize)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxRadius = this.size.minDimension / 2f
        val rings = 5
        for (i in rings downTo 1) {
            val t = i / rings.toFloat()
            drawCircle(
                color = NEON_GLOW_COLOR.copy(alpha = 0.10f * (1.15f - t)),
                radius = maxRadius * (0.55f + 0.45f * t),
                center = center
            )
        }
    }
}

@Composable
private fun RainbowRing(avatarSize: Dp) {
    val infinite = rememberInfiniteTransition(label = "rainbow_frame")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(3000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "rainbow_angle"
    )
    val ringSize = avatarSize + 12.dp
    val strokeWidth = 4.dp
    Canvas(Modifier.size(ringSize)) {
        val strokePx = strokeWidth.toPx()
        rotate(angle) {
            drawCircle(
                brush = Brush.sweepGradient(RAINBOW_COLORS),
                radius = this.size.minDimension / 2f - strokePx / 2f,
                style = Stroke(width = strokePx)
            )
        }
    }
}
