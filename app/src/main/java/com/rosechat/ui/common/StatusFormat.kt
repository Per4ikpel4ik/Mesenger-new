package com.rosechat.ui.common

import com.google.firebase.Timestamp

// Текст под именем собеседника в чате, когда он не в сети и не печатает.
fun formatLastSeen(ts: Timestamp?): String {
    if (ts == null) return "был(а) в сети давно"
    val minutes = (System.currentTimeMillis() - ts.toDate().time) / 60_000
    return when {
        minutes < 1 -> "был(а) в сети только что"
        minutes < 5 -> "был(а) в сети недавно"
        minutes < 60 -> "был(а) в сети $minutes мин. назад"
        minutes < 60 * 24 -> "был(а) в сети ${minutes / 60} ч. назад"
        minutes < 60 * 24 * 7 -> "был(а) в сети ${minutes / (60 * 24)} дн. назад"
        else -> "был(а) в сети давно"
    }
}
