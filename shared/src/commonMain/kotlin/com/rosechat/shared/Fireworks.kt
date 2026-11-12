package com.rosechat.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Фейерверк на весь экран. Запуск из любого места: Fireworks.launch() или Fireworks.launch(big = true).
// Сам оверлей (FireworksOverlay) один раз стоит в корне App и не перехватывает нажатия.
object Fireworks {
    var request by mutableStateOf(0)
        private set
    var big by mutableStateOf(false)
        private set

    fun launch(big: Boolean = false) {
        this.big = big
        request++
    }

    private val triggers = listOf("🎆", "🎇", "🎉", "🎊", "🥳")

    // Сообщение «праздничное» — тогда при отправке и получении запускается большой салют.
    fun isCelebration(text: String): Boolean = triggers.any { text.contains(it) }
}

private class Rocket(var x: Float, var y: Float, val tx: Float, val ty: Float, var vy: Float, val color: Color, var wait: Float)

private class Spark(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val decay: Float, val color: Color, val size: Float,
) {
    var px = x
    var py = y
}

private val fireworkColors = listOf(
    Color(0xFFFF5C5C), Color(0xFFFFC440), Color(0xFF78DC8C), Color(0xFF60AAFF),
    Color(0xFFC878FF), Color(0xFFFF82C8), Color(0xFFFFFFFF),
)

@Composable
fun FireworksOverlay(modifier: Modifier = Modifier) {
    val d = LocalDensity.current.density
    val rockets = remember { mutableListOf<Rocket>() }
    val sparks = remember { mutableListOf<Spark>() }
    var frame by remember { mutableStateOf(0L) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    val request = Fireworks.request

    LaunchedEffect(request) {
        if (request == 0 || area.width == 0 || area.height == 0) return@LaunchedEffect
        val w = area.width.toFloat()
        val h = area.height.toFloat()
        val count = if (Fireworks.big) 9 else 3
        for (i in 0 until count) {
            val tx = w * (0.12f + Random.nextFloat() * 0.76f)
            rockets.add(
                Rocket(
                    x = tx + (Random.nextFloat() - 0.5f) * 60f * d, y = h, tx = tx,
                    ty = h * (0.12f + Random.nextFloat() * 0.38f),
                    vy = -(560f + Random.nextFloat() * 160f) * d,
                    color = fireworkColors.random(), wait = i * 0.17f,
                ),
            )
        }
        var last = withFrameNanos { it }
        while (rockets.isNotEmpty() || sparks.isNotEmpty()) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now

            val rit = rockets.iterator()
            val fresh = mutableListOf<Spark>()
            while (rit.hasNext()) {
                val r = rit.next()
                if (r.wait > 0f) { r.wait -= dt; continue }
                r.x += (r.tx - r.x) * 1.4f * dt
                r.y += r.vy * dt
                r.vy *= (1f - 0.5f * dt)
                if (r.y <= r.ty || r.vy > -140f * d) {
                    val n = 56 + Random.nextInt(28)
                    val second = fireworkColors.random()
                    for (i in 0 until n) {
                        val a = (2.0 * PI * i / n).toFloat() + Random.nextFloat() * 0.1f
                        val sp = (140f + Random.nextFloat() * 130f) * d
                        fresh.add(
                            Spark(
                                r.x, r.y, cos(a) * sp, sin(a) * sp, 1f, 0.7f + Random.nextFloat() * 0.5f,
                                if (i % 3 == 0) second else r.color, (1.6f + Random.nextFloat() * 1.4f) * d,
                            ),
                        )
                    }
                    rit.remove()
                }
            }
            sparks.addAll(fresh)

            val sit = sparks.iterator()
            while (sit.hasNext()) {
                val s = sit.next()
                s.px = s.x
                s.py = s.y
                s.x += s.vx * dt
                s.y += s.vy * dt
                s.vy += 170f * d * dt
                val damp = (1f - 1.1f * dt).coerceAtLeast(0f)
                s.vx *= damp
                s.vy *= damp
                s.life -= s.decay * dt
                if (s.life <= 0f) sit.remove()
            }
            frame = now
        }
    }

    Canvas(modifier.fillMaxSize().onSizeChanged { area = it }) {
        @Suppress("UNUSED_VARIABLE")
        val tick = frame
        for (r in rockets) {
            if (r.wait > 0f) continue
            drawLine(r.color.copy(alpha = 0.8f), Offset(r.x, r.y), Offset(r.x, r.y + 14f * d), strokeWidth = 2.5f * d, cap = StrokeCap.Round)
        }
        for (s in sparks) {
            val a = s.life.coerceIn(0f, 1f)
            drawLine(s.color.copy(alpha = a), Offset(s.px, s.py), Offset(s.x, s.y), strokeWidth = s.size * (0.6f + 0.6f * a), cap = StrokeCap.Round)
        }
    }
}
