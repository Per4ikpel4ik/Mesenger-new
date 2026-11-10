package com.rosechat.shared.data

// Время последнего сообщения для списка чатов: сегодня — "14:05", в этом году — "27.09",
// раньше — "27.09.25". На Android не используется (заглушка нужна для компиляции commonMain).
expect fun formatChatTime(epochSeconds: Long): String

// Текущее время в секундах (Unix) — нужно, чтобы посчитать «был(а) в сети N мин. назад».
expect fun nowEpochSeconds(): Long

// Текст под именем собеседника, когда он не в сети (те же формулировки, что formatLastSeen на Android).
fun formatLastSeen(lastSeenSeconds: Long?): String {
    if (lastSeenSeconds == null) return "был(а) в сети давно"
    val minutes = (nowEpochSeconds() - lastSeenSeconds) / 60
    return when {
        minutes < 1 -> "был(а) в сети только что"
        minutes < 5 -> "был(а) в сети недавно"
        minutes < 60 -> "был(а) в сети $minutes мин. назад"
        minutes < 60 * 24 -> "был(а) в сети ${minutes / 60} ч. назад"
        minutes < 60 * 24 * 7 -> "был(а) в сети ${minutes / (60 * 24)} дн. назад"
        else -> "был(а) в сети давно"
    }
}
