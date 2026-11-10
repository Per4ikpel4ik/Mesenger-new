package com.rosechat.shared.data

import com.rosechat.shared.model.*
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Тот же Firestore-проект (pingchat-c68f2), те же коллекции ("users", "chats",
// "chats/{id}/messages"), что и app/.../FirebaseRepo.kt на Android — поэтому чат,
// открытый на телефоне, и чат, открытый в Windows-клиенте, это буквально один и
// тот же документ. Набор функций тут — только то, что нужно текстовому MVP:
// вход/регистрация, список чатов, переписка. Всё остальное (медиа, звонки,
// стикеры, премиум) остаётся зоной ответственности Android-приложения — на
// десктопе эти сообщения просто отобразятся как текстовая заглушка (см. ChatScreen
// в App.kt), ничего не сломают.
object SharedRepo {

    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore

    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun signIn(email: String, password: String): Result<String> = try {
        val result = auth.signInWithEmailAndPassword(email, password)
        val uid = result.user?.uid
        if (uid == null) Result.failure(Exception("Не удалось войти")) else Result.success(uid)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // Регистрация нового аккаунта прямо с десктопа. Пишем тот же набор полей, что и
    // Android при обычной email-регистрации (см. FirebaseRepo.registerEmail) — только
    // не проверяем занятость @username здесь отдельным запросом, чтобы не дублировать
    // эту логику в двух местах: если username уже занят, коллекция "usernames" просто
    // не получит новую запись, а поиск по нику останется зоной ответственности Android-клиента,
    // где эта проверка уже есть. Можно перенести сюда позже, если понадобится регистрация
    // именно с Windows как основной сценарий.
    suspend fun register(email: String, password: String, name: String, username: String): Result<String> = try {
        val result = auth.createUserWithEmailAndPassword(email, password)
        val uid = result.user?.uid
        if (uid == null) {
            Result.failure(Exception("Не удалось создать аккаунт"))
        } else {
            val profile = SUser(
                displayName = name.trim(),
                username = username.trim().lowercase(),
                email = email,
            )
            db.collection("users").document(uid).set(profile, merge = true)
            val cleanUsername = username.trim().lowercase()
            if (cleanUsername.length >= 3) {
                db.collection("usernames").document(cleanUsername)
                    .set(mapOf("uid" to uid, "displayName" to name.trim()), merge = true)
            }
            Result.success(uid)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun signOut() {
        auth.signOut()
    }

    suspend fun getUser(uid: String): SharedUser? = try {
        val doc = db.collection("users").document(uid).get()
        if (doc.exists) SharedUser(uid, doc.data<SUser>()) else null
    } catch (e: Exception) {
        null
    }

    fun observeChats(uid: String): Flow<List<SharedChat>> =
        db.collection("chats")
            .where { "memberIds" contains uid }
            .orderBy("lastMessageTime", Direction.DESCENDING)
            .snapshots
            .map { snapshot -> snapshot.documents.map { doc -> SharedChat(doc.id, doc.data<SChat>()) } }

    fun observeMessages(chatId: String): Flow<List<SharedMessage>> =
        db.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", Direction.ASCENDING)
            .snapshots
            .map { snapshot -> snapshot.documents.map { doc -> SharedMessage(doc.id, doc.data<SMessage>()) } }

    suspend fun sendText(chatId: String, text: String, senderUid: String, senderName: String) {
        val message = SMessage(
            chatId = chatId,
            senderId = senderUid,
            senderName = senderName,
            type = SMessageType.TEXT,
            text = text,
            timestamp = Timestamp.ServerTimestamp,
        )
        db.collection("chats").document(chatId).collection("messages").add(message)
        db.collection("chats").document(chatId).update(
            "lastMessage" to text,
            "lastMessageTime" to Timestamp.ServerTimestamp,
            "lastSenderId" to senderUid,
        )
    }

    // Фото/видео/файл — присланы с уже готовой Cloudinary-ссылкой (см. cloudinaryUpload в
    // MediaPlatform.desktop.kt, вызывается из App.kt ДО этой функции). Пишем то же mediaUrl,
    // что понимает и Android-приложение (Message.mediaUrl в data/model/Models.kt) — открытое
    // на телефоне, это фото/видео покажется как обычное, без всякой заглушки.
    suspend fun sendMedia(chatId: String, type: SMessageType, senderUid: String, senderName: String, mediaUrl: String, fileName: String = "", fileSize: Long = 0) {
        val lastLabel = when (type) {
            SMessageType.IMAGE -> "📷 Фото"
            SMessageType.VIDEO -> "🎬 Видео"
            SMessageType.FILE -> "📎 ${fileName.ifBlank { "Файл" }}"
            else -> "Медиа"
        }
        val message = SMessage(
            chatId = chatId,
            senderId = senderUid,
            senderName = senderName,
            type = type,
            mediaUrl = mediaUrl,
            fileName = fileName,
            fileSize = fileSize,
            timestamp = Timestamp.ServerTimestamp,
        )
        db.collection("chats").document(chatId).collection("messages").add(message)
        db.collection("chats").document(chatId).update(
            "lastMessage" to lastLabel,
            "lastMessageTime" to Timestamp.ServerTimestamp,
            "lastSenderId" to senderUid,
        )
    }

    // Помогает подписать чужие личные чаты человекочитаемым именем И аватаркой сразу
    // одним запросом (см. ChatDisplay ниже) — для приватного чата ни то ни другое не
    // хранится в самом документе чата (только uid собеседника в memberIds).
    suspend fun chatDisplayName(chat: SharedChat, myUid: String): String {
        if (chat.data.type != SChatType.PRIVATE) return chat.data.name
        val otherUid = chat.data.memberIds.firstOrNull { it != myUid } ?: return chat.data.name
        return getUser(otherUid)?.data?.displayName?.takeIf { it.isNotBlank() } ?: "Пользователь"
    }

    data class ChatDisplay(val name: String, val photoUrl: String, val frame: String? = null)

    suspend fun chatDisplayInfo(chat: SharedChat, myUid: String): ChatDisplay {
        if (chat.data.type != SChatType.PRIVATE) return ChatDisplay(chat.data.name, chat.data.photoUrl)
        val otherUid = chat.data.memberIds.firstOrNull { it != myUid } ?: return ChatDisplay(chat.data.name, chat.data.photoUrl)
        val user = getUser(otherUid)
        return ChatDisplay(
            user?.data?.displayName?.takeIf { it.isNotBlank() } ?: "Пользователь",
            user?.data?.photoUrl ?: "",
            if (user?.data?.isPremium == true) user.data.premiumFrame else null,
        )
    }

    // Смена своей аватарки из Настроек (см. SettingsScreen) — тот же самый Cloudinary-аплоад,
    // что и для фото/видео в чате, только результат пишем в users/{uid}.photoUrl, а не в
    // сообщение. Android читает то же поле (SUser.photoUrl / User.photoUrl), так что фото,
    // выбранное на Windows, увидят и там.
    suspend fun updateProfilePhoto(uid: String, url: String) {
        db.collection("users").document(uid).update("photoUrl" to url)
    }

    // ═══════════════ Этап 1: функции, как в Android FirebaseRepo ═══════════════

    private fun chatRef(id: String) = db.collection("chats").document(id)

    fun observeChat(chatId: String): Flow<SChat?> =
        chatRef(chatId).snapshots.map { if (it.exists) it.data<SChat>() else null }

    fun observeUser(uid: String): Flow<SUser?> =
        db.collection("users").document(uid).snapshots.map { if (it.exists) it.data<SUser>() else null }

    suspend fun setOnlineStatus(uid: String, online: Boolean) {
        try {
            db.collection("users").document(uid).update("isOnline" to online, "lastSeen" to Timestamp.ServerTimestamp)
        } catch (_: Exception) {}
    }

    // Поиск: @username (через коллекцию usernames), email целиком, либо часть имени/ника — как searchByName на Android.
    suspend fun searchUsers(query: String, myUid: String): List<SharedUser> = try {
        val q = query.removePrefix("@").trim().lowercase()
        if (q.length < 2) emptyList()
        else db.collection("users").get().documents
            .map { SharedUser(it.id, it.data<SUser>()) }
            .filter { u ->
                u.uid != myUid && (
                    u.data.displayName.lowercase().contains(q) ||
                    u.data.username.lowercase().contains(q) ||
                    u.data.email.lowercase() == q
                )
            }
    } catch (e: Exception) { emptyList() }

    suspend fun getOrCreatePrivateChat(myUid: String, otherUid: String): String {
        val existing = db.collection("chats").where { "memberIds" contains myUid }.get().documents
            .firstOrNull { d ->
                val c = d.data<SChat>()
                c.type == SChatType.PRIVATE && c.memberIds.contains(otherUid)
            }
        if (existing != null) return existing.id
        return db.collection("chats").add(SChat(type = SChatType.PRIVATE, memberIds = listOf(myUid, otherUid))).id
    }

    suspend fun createGroup(name: String, memberIds: List<String>, creatorUid: String): String {
        val chat = SChat(
            type = SChatType.GROUP,
            name = name.trim(),
            memberIds = (memberIds + creatorUid).distinct(),
            adminIds = listOf(creatorUid),
            createdBy = creatorUid,
            lastMessage = "Группа создана",
            lastMessageTime = Timestamp.ServerTimestamp,
        )
        return db.collection("chats").add(chat).id
    }

    // Для списков (memberIds/archivedBy/blockedUsers/typingBy) используем set(mapOf(...), merge = true) —
    // тот же приём, что уже работает в register(); скалярные поля — update с путём "a.b".
    suspend fun archiveChat(chatId: String, uid: String) {
        val cur = chatRef(chatId).get().data<SChat>().archivedBy
        chatRef(chatId).set(mapOf("archivedBy" to (cur + uid).distinct()), merge = true)
    }

    suspend fun unarchiveChat(chatId: String, uid: String) {
        val cur = chatRef(chatId).get().data<SChat>().archivedBy
        chatRef(chatId).set(mapOf("archivedBy" to cur.filter { it != uid }), merge = true)
    }

    suspend fun clearChatHistory(chatId: String, uid: String): Result<Unit> = try {
        chatRef(chatId).update("clearedAt.$uid" to Timestamp.ServerTimestamp, "unreadCount.$uid" to 0)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun deleteChatForMe(chatId: String, uid: String): Result<Unit> = try {
        chatRef(chatId).update(
            "clearedAt.$uid" to Timestamp.ServerTimestamp,
            "hiddenAt.$uid" to Timestamp.ServerTimestamp,
            "unreadCount.$uid" to 0,
        )
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun blockUser(myUid: String, otherUid: String): Result<Unit> = try {
        val cur = getUser(myUid)?.data?.blockedUsers ?: emptyList()
        db.collection("users").document(myUid).set(mapOf("blockedUsers" to (cur + otherUid).distinct()), merge = true)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun unblockUser(myUid: String, otherUid: String): Result<Unit> = try {
        val cur = getUser(myUid)?.data?.blockedUsers ?: emptyList()
        db.collection("users").document(myUid).set(mapOf("blockedUsers" to cur.filter { it != otherUid }), merge = true)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun setTyping(chatId: String, uid: String, typing: Boolean) {
        try {
            val cur = chatRef(chatId).get().data<SChat>().typingBy
            val next = if (typing) (cur + uid).distinct() else cur.filter { it != uid }
            if (next != cur) chatRef(chatId).set(mapOf("typingBy" to next), merge = true)
        } catch (_: Exception) {}
    }

    suspend fun markChatRead(chatId: String, uid: String) {
        try { chatRef(chatId).update("lastReadAt.$uid" to Timestamp.ServerTimestamp) } catch (_: Exception) {}
    }

    suspend fun clearUnread(chatId: String, uid: String) {
        try { chatRef(chatId).update("unreadCount.$uid" to 0) } catch (_: Exception) {}
    }

    suspend fun pinMessage(chatId: String, messageId: String) {
        try { chatRef(chatId).update("pinnedMessageId" to messageId) } catch (_: Exception) {}
    }

    suspend fun deleteMessage(chatId: String, msgId: String) {
        chatRef(chatId).collection("messages").document(msgId).update("isDeleted" to true)
    }

    suspend fun editMessage(chatId: String, msgId: String, newText: String): Result<Unit> = try {
        chatRef(chatId).collection("messages").document(msgId).update("text" to newText, "isEdited" to true)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun sendSticker(chatId: String, sticker: String, senderUid: String, senderName: String) {
        chatRef(chatId).collection("messages").add(
            SMessage(chatId = chatId, senderId = senderUid, senderName = senderName,
                type = SMessageType.STICKER, sticker = sticker, timestamp = Timestamp.ServerTimestamp)
        )
        chatRef(chatId).update("lastMessage" to sticker, "lastMessageTime" to Timestamp.ServerTimestamp, "lastSenderId" to senderUid)
    }

    suspend fun updateProfile(uid: String, displayName: String, status: String) {
        db.collection("users").document(uid).update("displayName" to displayName.trim(), "status" to status.trim())
    }

    suspend fun updateUsername(uid: String, newUsername: String): Result<Unit> = try {
        val clean = newUsername.removePrefix("@").lowercase().trim()
        if (clean.length < 3) return Result.failure(Exception("Минимум 3 символа"))
        val check = db.collection("usernames").document(clean).get()
        if (check.exists && check.get<String>("uid") != uid) return Result.failure(Exception("@$clean уже занят"))
        val user = getUser(uid)
        val old = user?.data?.username ?: ""
        if (old.isNotEmpty() && old != clean) db.collection("usernames").document(old).delete()
        db.collection("usernames").document(clean).set(mapOf("uid" to uid, "displayName" to (user?.data?.displayName ?: "")))
        db.collection("users").document(uid).update("username" to clean)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun setPremiumFrame(uid: String, frame: String) {
        db.collection("users").document(uid).update("premiumFrame" to frame)
    }
}
