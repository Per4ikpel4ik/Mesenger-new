package com.rosechat.shared.model

import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.serialization.Serializable

// ВАЖНО: имена полей здесь должны совпадать с app/src/main/java/com/rosechat/data/model/Models.kt —
// это те же документы Firestore (коллекции users/chats/chats/{id}/messages), просто читаем
// и пишем в них не полным набором полей, а только тем подмножеством, которое нужно для
// текстового мессенджера на Windows. Остальные поля (фото, стикеры, премиум и т.д.) в
// документе никуда не денутся — сериализатор их просто не трогает при чтении, а set(merge=true)
// не затирает их при записи.

@Serializable
data class SUser(
    val displayName: String = "",
    val username: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val status: String = "В сети",
    val isPremium: Boolean = false,
    val isOnline: Boolean = false,
    // Как в Android User: uid, которых я заблокировал; выбор рамки аватара (Premium).
    val blockedUsers: List<String> = emptyList(),
    val premiumFrame: String = "gold",
    val showPremiumBadge: Boolean = true,
    val premiumBadgeIcon: String = "star",
)

data class SharedUser(val uid: String, val data: SUser)

enum class SChatType { PRIVATE, GROUP, CHANNEL, SAVED }

@Serializable
data class SChat(
    val type: SChatType = SChatType.PRIVATE,
    val name: String = "",
    val photoUrl: String = "",
    val memberIds: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTime: BaseTimestamp? = null,
    val lastSenderId: String = "",
    // ── Поля ниже — те же, что в Android Chat (data/model/Models.kt) ──
    val description: String = "",
    val adminIds: List<String> = emptyList(),
    val createdBy: String = "",
    val isPublic: Boolean = false,
    val channelUsername: String = "",
    val broadcastOnly: Boolean = true,
    val archivedBy: List<String> = emptyList(),
    val typingBy: List<String> = emptyList(),
    val lastReadAt: Map<String, BaseTimestamp> = emptyMap(),
    val unreadCount: Map<String, Int> = emptyMap(),
    val pinnedMessageId: String = "",
    val clearedAt: Map<String, BaseTimestamp> = emptyMap(),
    val hiddenAt: Map<String, BaseTimestamp> = emptyMap(),
)

data class SharedChat(val id: String, val data: SChat)

enum class SMessageType { TEXT, IMAGE, VOICE, VIDEO_NOTE, VIDEO, FILE, STICKER, GIF }

@Serializable
data class SMessage(
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: SMessageType = SMessageType.TEXT,
    val text: String = "",
    // Те же имена полей, что и Message на Android (data/model/Models.kt) — это один и тот же
    // документ Firestore. mediaUrl — ссылка Cloudinary (основной путь и на телефоне, и здесь,
    // раз Cloudinary настроен); mediaData — Base64-запасной вариант для старых/маленьких файлов
    // без Cloudinary. Десктоп при отправке всегда пишет mediaUrl (см. SharedRepo.sendMedia) —
    // мы не дублируем Base64-запасной путь с телефона, чтобы не тащить лимиты Firestore-документа
    // сюда же.
    val mediaUrl: String = "",
    val mediaData: String = "",
    val fileName: String = "",
    val fileSize: Long = 0,
    val sticker: String = "",
    val isDeleted: Boolean = false,
    val isEdited: Boolean = false,
    val timestamp: BaseTimestamp? = null,
)

data class SharedMessage(val id: String, val data: SMessage)


// Секунды из серверной отметки времени. Пока запись ещё "в пути" (ServerTimestamp-заглушка),
// это не настоящий Timestamp — тогда null.
fun tsSeconds(t: BaseTimestamp?): Long? = (t as? Timestamp)?.seconds

// "Очистить историю": сообщения старше отметки clearedAt[uid] этому пользователю не показываются.
fun SChat.clearedSecondsFor(uid: String?): Long? = if (uid == null) null else tsSeconds(clearedAt[uid])

// "Удалить чат": скрыт, пока не пришло новое сообщение позже hiddenAt (как Chat.isHiddenFor на Android).
fun SChat.isHiddenFor(uid: String?): Boolean {
    if (uid == null) return false
    val h = tsSeconds(hiddenAt[uid]) ?: return false
    val last = tsSeconds(lastMessageTime) ?: return true
    return last <= h
}

// Превью последнего сообщения после очистки истории — пусто, а не старый текст.
fun SChat.lastMessagePreviewFor(uid: String?): String {
    val c = clearedSecondsFor(uid) ?: return lastMessage
    val last = tsSeconds(lastMessageTime) ?: return lastMessage
    return if (last <= c) "" else lastMessage
}

fun SChat.isArchivedFor(uid: String?): Boolean = uid != null && archivedBy.contains(uid)

// Рамки аватара Premium (те же ключи, что PREMIUM_FRAMES на Android; анимации neon/rainbow
// на десктопе пока сплошным цветом).
val PREMIUM_FRAME_COLORS: List<Pair<String, Long>> = listOf(
    "gold" to 0xFFFFD700, "sapphire" to 0xFF4A90E2, "emerald" to 0xFF2ECC71, "ruby" to 0xFFE91E63,
    "amethyst" to 0xFF9B59B6, "rose" to 0xFFFF4D8D, "ice" to 0xFF7FDBFF, "platinum" to 0xFFC7C9CC,
    "neon" to 0xFF39FF14, "rainbow" to 0xFFFFD700,
)
val PREMIUM_BADGE_EMOJI: Map<String, String> = mapOf("star" to "⭐", "diamond" to "💎", "crown" to "👑")

// Те же стикеры-эмодзи, что и CHAT_STICKERS на Android.
val CHAT_STICKERS = listOf(
    "😂","🤣","😭","😍","🥰","😘","😎","🤩","🥳","😜","🤪","🙄","😏","😴","🤯","🥴","😱","🤔","😅","😇",
    "🙃","😤","😡","🤬","🥺","😳","🫡","🫠","🤠","🥸",
    "👍","👎","👏","🙌","🤝","💪","✌️","🤞","🤙","👋","🤌","🙏","👌","🫶","✋","👊",
    "❤️","🧡","💛","💚","💙","💜","🖤","🤍","💔","💕","💞","💗","💖","💘",
    "🐶","🐱","🐼","🦊","🐨","🐯","🦁","🐸","🐵","🐰","🐔","🦄","🐙","🐳",
    "🍕","🍔","🍟","🌭","🍿","🍩","🍦","🍫","🍓","🍉","🥑","🍺","🍾","☕",
    "🔥","💯","✨","🎉","🎊","🎁","🏆","💰","🚀","⚡","💀","👻","🤖","👽","💩","🌈","☀️","🌙","⭐","💤",
)
