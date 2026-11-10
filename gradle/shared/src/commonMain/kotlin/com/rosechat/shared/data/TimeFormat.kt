package com.rosechat.shared.data

// Время последнего сообщения для списка чатов: сегодня — "14:05", в этом году — "27.09",
// раньше — "27.09.25". На Android не используется (заглушка нужна для компиляции commonMain).
expect fun formatChatTime(epochSeconds: Long): String
