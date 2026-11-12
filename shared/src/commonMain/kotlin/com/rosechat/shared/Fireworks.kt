package com.rosechat.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.rosechat.shared.data.loadStringPref
import com.rosechat.shared.data.localDateParts
import com.rosechat.shared.data.saveStringPref
import kotlinx.coroutines.delay
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

    // Надпись по центру экрана (null — не показываем).
    var greeting by mutableStateOf<String?>(null)
        internal set
    var newYearRequest by mutableStateOf(0)
        private set
    var greetingYear = 0
        private set

    // Новогодний салют с надписью «С Новым годом!» на несколько секунд.
    fun launchNewYear(year: Int) {
        greetingYear = year
        newYearRequest++
    }
}

// Следит за временем: салют начинается сразу после полуночи на 1 января и показывается один раз за год.
// Если приложение откроют уже 1 января, а салют ещё не показывали, он запустится при открытии.
@Composable
fun NewYearWatcher() {
    LaunchedEffect(Unit) {
        while (true) {
            val t = localDateParts()
            val year = t[0]
            if (t[1] == 1 && t[2] == 1) {
                val key = "newyear_shown_$year"
                if (loadStringPref(key, "") != "1") {
                    saveStringPref(key, "1")
                    delay(1200)
                    Fireworks.launchNewYear(year)
                }
            }
            val nearMidnight = t[1] == 12 && t[2] == 31 && t[3] == 23 && t[4] >= 58
            delay(if (nearMidnight) 500L else 20_000L)
        }
    }
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

    LaunchedEffect(Fireworks.newYearRequest) {
        if (Fireworks.newYearRequest == 0) return@LaunchedEffect
        Fireworks.greeting = "С Новым годом!\n" + Fireworks.greetingYear
        repeat(5) {
            Fireworks.launch(big = true)
            delay(2200)
        }
        delay(3000)
        Fireworks.greeting = null
    }

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

    val greeting = Fireworks.greeting
    val greetingAlpha by animateFloatAsState(if (greeting != null) 1f else 0f, tween(700))
    Box(modifier.fillMaxSize()) {
    Canvas(Modifier.fillMaxSize().onSizeChanged { area = it }) {
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
    if (greeting != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                greeting,
                style = TextStyle(
                    fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD66B).copy(alpha = greetingAlpha),
                    textAlign = TextAlign.Center, shadow = Shadow(Color(0xCC000000), Offset(0f, 3f), 12f),
                ),
            )
        }
    }
    }
}
