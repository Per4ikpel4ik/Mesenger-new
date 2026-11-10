package com.rosechat.shared.model

import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.Timestamp
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
    val premiumBubbleColor: String = "default",
    // Обои чата (пресет приоритетнее своего фото) и признак разработчика — те же поля, что в Android User.
    val chatBackground: String = "",
    val chatBackgroundData: String = "",
    val chatWallpaperPreset: String = "",
    val isDeveloper: Boolean = false,
    // Как в Android User: когда был в сети и «стикер у имени» (для экрана профиля).
    val lastSeen: BaseTimestamp? = null,
    val profileSticker: String = "",
)

data class SharedUser(val uid: String, val data: SUser)

// История (Stories) — фото, живёт 24 часа. Те же поля, что data class Story на Android
// (коллекция stories). userPhotoData/mediaData — Base64-запасной путь, который иногда
// кладёт Android; Windows читает mediaData для показа, но сам публикует только через Cloudinary-ссылку.
@Serializable
data class SStory(
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String = "",
    val userPhotoData: String = "",
    val mediaUrl: String = "",
    val mediaData: String = "",
    val createdAt: BaseTimestamp? = null,
    val expiresAt: BaseTimestamp? = null,
    val viewedBy: List<String> = emptyList(),
)

data class SharedStory(val id: String, val data: SStory)

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
    // Карты по uid (lastReadAt/unreadCount/clearedAt/hiddenAt) помечены @Transient: библиотека не умеет
    // декодировать Map<String, Timestamp>, поэтому SharedRepo.decodeChat читает их по путям "поле.uid".
    // ── Поля ниже — те же, что в Android Chat (data/model/Models.kt) ──
    val description: String = "",
    val adminIds: List<String> = emptyList(),
    val createdBy: String = "",
    val isPublic: Boolean = false,
    val channelUsername: String = "",
    val broadcastOnly: Boolean = true,
    val archivedBy: List<String> = emptyList(),
    val typingBy: List<String> = emptyList(),
    @Transient val lastReadAt: Map<String, BaseTimestamp> = emptyMap(),
    @Transient val unreadCount: Map<String, Int> = emptyMap(),
    val pinnedMessageId: String = "",
    @Transient val clearedAt: Map<String, BaseTimestamp> = emptyMap(),
    @Transient val hiddenAt: Map<String, BaseTimestamp> = emptyMap(),
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

// ── Звонки (только история): те же имена полей и значения enum, что у Android Call/CallStatus/CallType
// (коллекция calls). offerSdp/answerSdp сюда не входят — пока читаем только список звонков. ──
enum class SCallStatus { RINGING, ACTIVE, ENDED, DECLINED, MISSED }
enum class SCallType { AUDIO, VIDEO }

@Serializable
data class SCall(
    val callerId: String = "",
    val callerName: String = "",
    val callerPhotoUrl: String = "",
    val callerPhotoData: String = "",
    val receiverId: String = "",
    val type: SCallType = SCallType.AUDIO,
    val status: SCallStatus = SCallStatus.ENDED,
    val offerSdp: String = "",
    val answerSdp: String = "",
    val startedAt: BaseTimestamp? = null,
    val endedAt: BaseTimestamp? = null,
)

data class SharedCall(val id: String, val data: SCall)

// ICE-кандидат в подколлекциях calls/{id}/callerCandidates и calleeCandidates (как Android IceCandidateData).
@Serializable
data class SIce(
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val candidate: String = "",
)


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

// Цвет своих исходящих пузырей (Premium, виден только самому пользователю) — те же ключи, что
// PREMIUM_BUBBLE_COLORS на Android; null = обычный цвет темы.
val PREMIUM_BUBBLE_COLORS: List<Pair<String, Long?>> = listOf(
    "default" to null,
    "sapphire" to 0xFF3D6FB4,
    "emerald" to 0xFF1E9E5A,
    "ruby" to 0xFFC2185B,
    "amethyst" to 0xFF7B4397,
    "rose" to 0xFFD6336C,
    "midnight" to 0xFF1F2A44,
)

// Картиночные стикеры (как IMAGE_STICKERS на Android): PNG лежат в desktopMain/resources/stickers/<имя>.png.
val IMAGE_STICKER_NAMES: List<String> = listOf("sticker_seal", "sticker_hippo1", "sticker_hippo2")

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
