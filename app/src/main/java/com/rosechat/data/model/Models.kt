package com.rosechat.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// Стикеры для профиля (маленький бейдж у аватара)
val PROFILE_STICKERS = listOf("🌸","✨","🔥","💫","🎭","🦋","🌙","⭐","💎","🎪","🦄","🌈","🎨","🎯","💡")

// Стикеры для чата — большой набор на все случаи (эмодзи в укрупнённом виде вместо
// настоящих иллюстрированных стикер-паков, для которых нужна отдельная художественная работа)
val CHAT_STICKERS = listOf(
    // Эмоции
    "😂","🤣","😭","😍","🥰","😘","😎","🤩","🥳","😜","🤪","🙄","😏","😴","🤯","🥴","😱","🤔","😅","😇",
    "🙃","😤","😡","🤬","🥺","😳","🫡","🫠","🤠","🥸",
    // Жесты и руки
    "👍","👎","👏","🙌","🤝","💪","✌️","🤞","🤙","👋","🤌","🙏","👌","🫶","✋","👊",
    // Сердца
    "❤️","🧡","💛","💚","💙","💜","🖤","🤍","💔","💕","💞","💗","💖","💘",
    // Животные
    "🐶","🐱","🐼","🦊","🐨","🐯","🦁","🐸","🐵","🐰","🐔","🦄","🐙","🐳",
    // Еда
    "🍕","🍔","🍟","🌭","🍿","🍩","🍦","🍫","🍓","🍉","🥑","🍺","🍾","☕",
    // Разное / реакции
    "🔥","💯","✨","🎉","🎊","🎁","🏆","💰","🚀","⚡","💀","👻","🤖","👽","💩","🌈","☀️","🌙","⭐","💤"
)

data class User(
    @DocumentId val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val phone: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val photoData: String = "",         // Base64 фото профиля (временно, пока нет Storage/Blaze)
    val status: String = "В сети",
    val profileSticker: String = "",    // Анимационный стикер у имени
    val isPremium: Boolean = false,     // Премиум статус
    val premiumUntil: Timestamp? = null, // Если задан — временный Premium (код "gg"), сгорает по расписанию в Cloud Functions. null = бессрочный (выдан вручную из админки)
    val premiumFrame: String = "gold",  // Выбранный цвет/эффект рамки аватара (см. PREMIUM_FRAMES)
    val showPremiumBadge: Boolean = true, // Показывать ли бейдж у имени (настройка внутри Premium)
    val premiumBadgeIcon: String = "star", // Какой бейдж показывать (см. PREMIUM_BADGE_ICONS)
    val premiumBubbleColor: String = "default", // Цвет своих пузырей в чате (см. PREMIUM_BUBBLE_COLORS)
    val isOnline: Boolean = false,
    val lastSeen: Timestamp? = null,
    val fcmToken: String = "",
    val isDeveloper: Boolean = false,
    val chatBackground: String = "",
    val chatBackgroundData: String = "", // Base64 фона чата (временно, пока нет Storage/Blaze)
    val chatWallpaperPreset: String = "", // Один из встроенных градиентов (см. CHAT_WALLPAPER_PRESETS) — приоритетнее chatBackground/-Data, если задан
    val createdAt: Timestamp? = null,
    val termsAcceptedAt: Timestamp? = null   // когда пользователь принял пользовательское соглашение
)

// Ссылка на фото — либо настоящий URL (Storage), либо Base64 (временный обход без Storage).
data class PhotoRef(val url: String = "", val data: String = "") {
    val isEmpty get() = url.isBlank() && data.isBlank()
}

enum class ChatType { PRIVATE, GROUP, CHANNEL, SAVED }  // SAVED = Избранное

data class Chat(
    @DocumentId val id: String = "",
    val type: ChatType = ChatType.PRIVATE,
    val name: String = "",
    val description: String = "",
    val photoUrl: String = "",
    val memberIds: List<String> = emptyList(),
    val adminIds: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTime: Timestamp? = null,
    val lastSenderId: String = "",
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    // ── Только для CHANNEL ──
    val isPublic: Boolean = false,       // публичный (виден в поиске) или приватный (только по приглашению)
    val channelUsername: String = "",    // @username публичного канала
    val broadcastOnly: Boolean = true,   // true = писать могут только админы (как в Telegram-каналах)
    val archivedBy: List<String> = emptyList(),  // кто из участников убрал чат в архив (у каждого — своё состояние)
    val typingBy: List<String> = emptyList(),    // кто сейчас печатает в этом чате
    val lastReadAt: Map<String, Timestamp> = emptyMap(),  // когда каждый участник последний раз читал чат
    val unreadCount: Map<String, Int> = emptyMap(),  // счётчик непрочитанных на участника — для бейджа в списке чатов
    val pinnedMessageId: String = ""    // id закреплённого сообщения (одно за раз, как в личных чатах Telegram)
)

enum class MessageType { TEXT, IMAGE, VOICE, VIDEO_NOTE, VIDEO, FILE, STICKER, GIF }

data class Message(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: MessageType = MessageType.TEXT,
    val text: String = "",
    val mediaUrl: String = "",
    val mediaData: String = "",        // Base64 фото/файла (временно, пока нет Storage/Blaze)
    val audioData: String = "",        // Base64 аудио голосового сообщения (временно, пока нет Storage/Blaze)
    val fileName: String = "",
    val fileSize: Long = 0,
    val duration: Int = 0,
    val sticker: String = "",          // Эмодзи стикер
    val timestamp: Timestamp? = null,
    val readBy: List<String> = emptyList(),
    val isDeleted: Boolean = false,
    val isEdited: Boolean = false
)

// История (Stories) — фото, живёт 24 часа. Как и с остальными медиа в проекте,
// пока нет Storage/Blaze для крупных файлов — храним сжатое фото как Base64
// (тот же ImageCompress, что и для фото в чате), это укладывается в лимит документа Firestore.
data class Story(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String = "",
    val userPhotoData: String = "",
    val mediaUrl: String = "",         // Cloudinary-ссылка, когда он настроен (см. FirebaseRepo.postStory)
    val mediaData: String = "",        // Base64 фото истории — запасной путь без Cloudinary
    val createdAt: Timestamp? = null,
    val expiresAt: Timestamp? = null, // createdAt + 24 часа — по нему и фильтруем/чистим
    val viewedBy: List<String> = emptyList()
)

// Звонок — сигналинг (обмен SDP offer/answer) идёт через сам этот документ,
// ICE-кандидаты — через подколлекции calls/{id}/callerCandidates и /calleeCandidates
// (см. FirebaseRepo и CallManager). Статус общий на двоих, а не "с чьей-то стороны" —
// это один документ, который читают и обновляют оба участника.
enum class CallType { AUDIO, VIDEO }
enum class CallStatus { RINGING, ACTIVE, ENDED, DECLINED, MISSED }

data class Call(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerPhotoUrl: String = "",
    val callerPhotoData: String = "",
    val receiverId: String = "",
    val type: CallType = CallType.AUDIO,
    val status: CallStatus = CallStatus.RINGING,
    val offerSdp: String = "",
    val answerSdp: String = "",
    val startedAt: Timestamp? = null,
    val endedAt: Timestamp? = null
)

data class IceCandidateData(
    @DocumentId val id: String = "",
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val candidate: String = ""
)
