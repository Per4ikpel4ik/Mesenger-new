@file:OptIn(ExperimentalForeignApi::class)

package com.rosechat.shared.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.posix.time

// Сегодня — "14:05", в этом году — "27.09", раньше — "27.09.25" (как на десктопе).
actual fun formatChatTime(epochSeconds: Long): String {
    val date = NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble())
    val calendar = NSCalendar.currentCalendar
    val formatter = NSDateFormatter()
    formatter.dateFormat = when {
        calendar.isDateInToday(date) -> "HH:mm"
        calendar.component(NSCalendarUnitYear, fromDate = date) ==
            calendar.component(NSCalendarUnitYear, fromDate = NSDate()) -> "dd.MM"
        else -> "dd.MM.yy"
    }
    return formatter.stringFromDate(date)
}

// Текущее время в секундах с 1970 года (POSIX time(), без лишних Foundation-импортов).
actual fun nowEpochSeconds(): Long = time(null)
