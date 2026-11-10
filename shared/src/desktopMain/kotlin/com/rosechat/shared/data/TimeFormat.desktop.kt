package com.rosechat.shared.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("dd.MM")
private val fullFmt = DateTimeFormatter.ofPattern("dd.MM.yy")

actual fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

actual fun formatChatTime(epochSeconds: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochSecond(epochSeconds).atZone(zone)
    val today = LocalDate.now(zone)
    val date = dt.toLocalDate()
    return when {
        date == today -> dt.format(timeFmt)
        date.year == today.year -> dt.format(dayFmt)
        else -> dt.format(fullFmt)
    }
}
