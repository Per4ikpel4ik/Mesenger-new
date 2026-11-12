package com.rosechat.shared.data

import java.util.Calendar

actual fun localDateParts(): IntArray {
    val c = Calendar.getInstance()
    return intArrayOf(
        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
    )
}
