package com.rosechat.ui.common

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.ContentScale

// Простой узор-текстура вместо сплошного градиента — как ненавязчивый паттерн фона чата
// в WhatsApp/Telegram (там тоже не фотография, а повторяющийся мелкий рисунок). Тут это
// сетка точек в шахматном порядке, отрисованная один раз в маленький тайл и замощённая
// BitmapShader — дешевле и проще, чем тащить готовую картинку-обои.
private fun dotPatternBrush(bgColor: Int, dotColor: Int, tileSizePx: Int = 72, dotRadiusPx: Float = 3f): Brush {
    val bitmap = android.graphics.Bitmap.createBitmap(tileSizePx, tileSizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(bgColor)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
    val step = tileSizePx / 2f
    for (row in 0..1) {
        for (col in 0..1) {
            val offsetX = if (row % 2 == 1) step / 2f else 0f
            canvas.drawCircle((col * step + offsetX) % tileSizePx, row * step, dotRadiusPx, paint)
        }
    }
    val shader = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
    return ShaderBrush(shader)
}

// "Каракули" — тайл с несколькими простыми линейными значками (звезда, сердце, месяц,
// нотка, облако), нарисованными Canvas/Path и замощёнными тем же приёмом, что и
// dotPatternBrush выше. Ничего не грузим из сети и не храним лишнюю картинку в APK —
// только тонкие штрихи фиолетового цвета на тёмном фоне.
private fun doodlePatternBrush(
    bgColor: Int,
    strokeColor: Int,
    tileSizePx: Int = 340,
    strokeWidthPx: Float = 3.4f
): Brush {
    val s = tileSizePx.toFloat()
    val bitmap = android.graphics.Bitmap.createBitmap(tileSizePx, tileSizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(bgColor)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        color = strokeColor
    }

    // Звезда
    canvas.save()
    canvas.translate(s * 0.18f, s * 0.20f)
    canvas.rotate(-12f)
    val starPath = android.graphics.Path()
    val starR = s * 0.11f
    for (i in 0..9) {
        val angle = Math.toRadians((-90 + i * 36).toDouble())
        val r = if (i % 2 == 0) starR else starR * 0.42f
        val x = (r * Math.cos(angle)).toFloat(); val y = (r * Math.sin(angle)).toFloat()
        if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
    }
    starPath.close()
    canvas.drawPath(starPath, paint)
    canvas.restore()

    // Сердце
    canvas.save()
    canvas.translate(s * 0.74f, s * 0.18f)
    val heartPath = android.graphics.Path()
    val hs = s * 0.10f
    heartPath.moveTo(0f, hs * 0.6f)
    heartPath.cubicTo(-hs * 1.3f, -hs * 0.6f, -hs * 0.3f, -hs * 1.5f, 0f, -hs * 0.4f)
    heartPath.cubicTo(hs * 0.3f, -hs * 1.5f, hs * 1.3f, -hs * 0.6f, 0f, hs * 0.6f)
    heartPath.close()
    canvas.drawPath(heartPath, paint)
    canvas.restore()

    // Полумесяц
    val moonRect = android.graphics.RectF(s * 0.10f, s * 0.62f, s * 0.32f, s * 0.84f)
    canvas.drawArc(moonRect, 40f, 280f, false, paint)

    // Нотка
    canvas.save()
    canvas.translate(s * 0.76f, s * 0.70f)
    canvas.drawCircle(0f, s * 0.07f, s * 0.045f, paint)
    canvas.drawLine(s * 0.04f, s * 0.07f, s * 0.04f, -s * 0.10f, paint)
    canvas.drawLine(s * 0.04f, -s * 0.10f, s * 0.12f, -s * 0.06f, paint)
    canvas.restore()

    // Облачко
    val cx = s * 0.46f; val cy = s * 0.46f
    canvas.drawCircle(cx - s * 0.05f, cy, s * 0.045f, paint)
    canvas.drawCircle(cx + s * 0.02f, cy - s * 0.03f, s * 0.055f, paint)
    canvas.drawCircle(cx + s * 0.09f, cy, s * 0.04f, paint)
    canvas.drawLine(cx - s * 0.09f, cy + s * 0.035f, cx + s * 0.13f, cy + s * 0.035f, paint)

    val shader = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
    return ShaderBrush(shader)
}

// Фото-пресет обоев (например, гора) — декодируем встроенный в APK drawable-ресурс один
// раз и растягиваем "на всю ширину" (cover-заливка) через матрицу BitmapShader. Точный
// целевой размер экрана заранее неизвестен (у пресетов нет доступа к размеру Compose-бокса
// в момент создания Brush) — берём с запасом под типичный портретный экран телефона
// (1080×2400), поэтому на редких сильно других пропорциях кадрирование будет чуть менее
// точным, но без видимых швов (TileMode.CLAMP тянет крайний пиксель, а не повторяет тайл).
private fun photoBrush(resId: Int, refWidthPx: Float = 1080f, refHeightPx: Float = 2400f): Brush {
    return try {
        val resources = com.rosechat.RoseChatApp.instance.resources
        val bitmap = android.graphics.BitmapFactory.decodeResource(resources, resId)
        val shader = android.graphics.BitmapShader(
            bitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP
        )
        val scale = maxOf(refWidthPx / bitmap.width, refHeightPx / bitmap.height)
        val scaledW = bitmap.width * scale
        val scaledH = bitmap.height * scale
        val matrix = android.graphics.Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate((refWidthPx - scaledW) / 2f, (refHeightPx - scaledH) / 2f)
        shader.setLocalMatrix(matrix)
        ShaderBrush(shader)
    } catch (e: Exception) {
        // Если ресурс почему-то не декодировался — не роняем экран настроек, а просто
        // показываем нейтральный градиент вместо фото.
        Brush.linearGradient(listOf(Color(0xFF2B2B3D), Color(0xFF0F0F0F)))
    }
}

// Встроенные фоны чата — раньше единственным способом сменить фон была загрузка своей
// фотографии, убрать её тоже было нечем (см. чат: "убрать фон обоев и поставить" + "свои
// обои чатов ... чтобы их можно было выбрать"). Это готовый набор градиентов, ничего не
// нужно загружать — выбор мгновенный. Хранится в отдельном поле User.chatWallpaperPreset
// (id из этой карты), а не как ещё один спецформат в chatBackground/-Data.
val CHAT_WALLPAPER_PRESETS: LinkedHashMap<String, Brush> = linkedMapOf(
    "sunset" to Brush.linearGradient(listOf(Color(0xFFFF6B9D), Color(0xFFFF2D78), Color(0xFF6B1A4A))),
    "midnight" to Brush.linearGradient(listOf(Color(0xFF1F2A44), Color(0xFF0F0F0F))),
    "ocean" to Brush.linearGradient(listOf(Color(0xFF0A4A8B), Color(0xFF0A84FF), Color(0xFF0F0F0F))),
    "forest" to Brush.linearGradient(listOf(Color(0xFF1B6B33), Color(0xFF0F0F0F))),
    "violet" to Brush.linearGradient(listOf(Color(0xFF6B2C8B), Color(0xFFBF5AF2), Color(0xFF1C1C1E))),
    "mono" to Brush.linearGradient(listOf(Color(0xFF3A3A3C), Color(0xFF0F0F0F))),
    "pattern" to dotPatternBrush(
        bgColor = android.graphics.Color.argb(255, 14, 26, 20),
        dotColor = android.graphics.Color.argb(31, 255, 255, 255)
    ),
    "doodle" to doodlePatternBrush(
        bgColor = android.graphics.Color.argb(255, 13, 8, 15),
        strokeColor = android.graphics.Color.argb(130, 150, 100, 180)
    ),
    "mountain" to photoBrush(com.rosechat.R.drawable.wallpaper_mountain),
)

// Название для UI-подписи под превью в гриде выбора.
fun chatWallpaperPresetLabel(id: String): String = when (id) {
    "sunset" -> "Закат"; "midnight" -> "Полночь"; "ocean" -> "Океан"
    "forest" -> "Лес"; "violet" -> "Аметист"; "mono" -> "Монохром"; "pattern" -> "Узор"
    "doodle" -> "Каракули"; "mountain" -> "Горы"
    else -> id
}

// Единая точка рендера фона чата (встроенный градиент ИЛИ своя фотка) — используется и в
// самом ChatScreen, и в превью настроек (SettingsScreen/ChatSettingsScreen), чтобы оба места
// всегда были согласованы между собой. Если ни пресета, ни фото нет, ничего не рисует —
// вызывающий код сам кладёт обычный сплошной фон под этим composable.
@Composable
fun ChatWallpaperView(preset: String, url: String, data: String, modifier: Modifier = Modifier) {
    val brush = CHAT_WALLPAPER_PRESETS[preset]
    when {
        brush != null -> androidx.compose.foundation.layout.Box(modifier.background(brush))
        hasImage(url, data) -> SmartImage(url, data, modifier, ContentScale.Crop)
    }
}

fun hasWallpaper(preset: String, url: String, data: String) = preset.isNotBlank() || hasImage(url, data)
