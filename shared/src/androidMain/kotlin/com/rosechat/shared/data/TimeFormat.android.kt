package com.rosechat.shared.data

actual fun formatChatTime(epochSeconds: Long): String = ""

actual fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000
