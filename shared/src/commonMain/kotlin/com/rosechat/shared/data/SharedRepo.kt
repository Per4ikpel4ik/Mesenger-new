package com.rosechat.shared.data

import com.rosechat.shared.model.*
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
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
            .map { snapshot -> snapshot.documents.map { doc -> SharedChat(doc.id, decodeChat(doc)) } }

    // История звонков: документы коллекции "calls", где я звонящий или принимающий (как на Android).
    // Сортируем на клиенте — так не нужен составной индекс Firestore.
    fun observeCallHistory(uid: String): Flow<List<SharedCall>> {
        val outgoing = db.collection("calls").where { "callerId" equalTo uid }.snapshots
            .map { s -> s.documents.map { SharedCall(it.id, it.data<SCall>()) } }
        val incoming = db.collection("calls").where { "receiverId" equalTo uid }.snapshots
            .map { s -> s.documents.map { SharedCall(it.id, it.data<SCall>()) } }
        return combine(outgoing, incoming) { a, b ->
            (a + b).distinctBy { it.id }
                .sortedByDescending { tsSeconds(it.data.startedAt) ?: 0L }
                .take(200)
        }
    }

    // ── Звонки: сигналинг как у Android (offerSdp/answerSdp в документе, кандидаты — подколлекции) ──
    suspend fun startCall(
        callerId: String, callerName: String, callerPhotoUrl: String, callerPhotoData: String,
        receiverId: String, type: String,
    ): String {
        val call = SCall(
            callerId = callerId, callerName = callerName,
            callerPhotoUrl = callerPhotoUrl, callerPhotoData = callerPhotoData,
            receiverId = receiverId, type = SCallType.valueOf(type), status = SCallStatus.RINGING,
            startedAt = Timestamp.ServerTimestamp,
        )
        return db.collection("calls").add(call).id
    }

    fun observeCall(callId: String): Flow<SharedCall?> =
        db.collection("calls").document(callId).snapshots.map {
            if (it.exists) SharedCall(it.id, it.data<SCall>()) else null
        }

    // Входящие мне и ещё не отвеченные (RINGING). Фильтр по статусу — на клиенте, так не нужен составной индекс.
    fun observeIncomingCall(uid: String): Flow<SharedCall?> =
        db.collection("calls").where { "receiverId" equalTo uid }.snapshots.map { s ->
            s.documents.map { SharedCall(it.id, it.data<SCall>()) }
                .filter { it.data.status == SCallStatus.RINGING }
                .maxByOrNull { tsSeconds(it.data.startedAt) ?: Long.MAX_VALUE }
        }

    suspend fun setOffer(callId: String, sdp: String) {
        db.collection("calls").document(callId).update("offerSdp" to sdp)
    }

    suspend fun setAnswer(callId: String, sdp: String) {
        db.collection("calls").document(callId).update("answerSdp" to sdp, "status" to "ACTIVE")
    }

    suspend fun declineCall(callId: String) {
        try {
            db.collection("calls").document(callId).update("status" to "DECLINED", "endedAt" to Timestamp.ServerTimestamp)
        } catch (_: Exception) {}
    }

    suspend fun endCall(callId: String, status: String) {
        try {
            db.collection("calls").document(callId).update("status" to status, "endedAt" to Timestamp.ServerTimestamp)
        } catch (_: Exception) {}
    }

    suspend fun addIceCandidate(callId: String, fromCaller: Boolean, sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val sub = if (fromCaller) "callerCandidates" else "calleeCandidates"
        db.collection("calls").document(callId).collection(sub).add(SIce(sdpMid, sdpMLineIndex, candidate))
    }

    // Кандидаты ДРУГОЙ стороны: fromCaller = true — слушаем callerCandidates (так делает принимающий).
    // Отдаём только новые документы.
    fun listenIceCandidates(callId: String, fromCaller: Boolean): Flow<List<SIce>> = flow {
        val sub = if (fromCaller) "callerCandidates" else "calleeCandidates"
        val seen = HashSet<String>()
        db.collection("calls").document(callId).collection(sub).snapshots.collect { s ->
            val fresh = s.documents.filter { seen.add(it.id) }.map { it.data<SIce>() }
            if (fresh.isNotEmpty()) emit(fresh)
        }
    }

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
            SMessageType.VOICE -> "🎤 Голосовое сообщение"
            SMessageType.VIDEO_NOTE -> "⭕ Видео-кружок"
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

    private fun tsAt(doc: DocumentSnapshot, path: String): BaseTimestamp? =
        try { doc.get<Timestamp?>(path) } catch (e: Exception) { null }

    // Основные поля — через data<SChat>(); карты по uid читаем по путям "поле.uid" (см. Models.kt).
    private fun decodeChat(doc: DocumentSnapshot): SChat {
        val c = doc.data<SChat>()
        fun tsMap(field: String): Map<String, BaseTimestamp> =
            c.memberIds.mapNotNull { uid -> tsAt(doc, "$field.$uid")?.let { uid to it } }.toMap()
        val unread: Map<String, Int> = c.memberIds.mapNotNull { uid ->
            val n = try { doc.get<Long?>("unreadCount.$uid") } catch (e: Exception) { null }
            n?.let { uid to it.toInt() }
        }.toMap()
        return c.copy(
            lastReadAt = tsMap("lastReadAt"), clearedAt = tsMap("clearedAt"),
            hiddenAt = tsMap("hiddenAt"), unreadCount = unread,
        )
    }

    private fun chatRef(id: String) = db.collection("chats").document(id)

    fun observeChat(chatId: String): Flow<SChat?> =
        chatRef(chatId).snapshots.map { if (it.exists) decodeChat(it) else null }

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

    suspend fun updateUsername(uid: String, newUsername: String): Result<Unit> {
        return try {
            val clean = newUsername.removePrefix("@").lowercase().trim()
            if (clean.length < 3) {
                Result.failure(Exception("Минимум 3 символа"))
            } else {
                val check = db.collection("usernames").document(clean).get()
                if (check.exists && check.get<String>("uid") != uid) {
                    Result.failure(Exception("@$clean уже занят"))
                } else {
                    val user = getUser(uid)
                    val old = user?.data?.username ?: ""
                    if (old.isNotEmpty() && old != clean) db.collection("usernames").document(old).delete()
                    db.collection("usernames").document(clean).set(mapOf("uid" to uid, "displayName" to (user?.data?.displayName ?: "")))
                    db.collection("users").document(uid).update("username" to clean)
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun setPremiumFrame(uid: String, frame: String) {
        db.collection("users").document(uid).update("premiumFrame" to frame)
    }

    // ═══════════════ Каналы и управление группой (как в Android FirebaseRepo) ═══════════════

    suspend fun getUsers(ids: List<String>): List<SharedUser> = ids.mapNotNull { getUser(it) }

    suspend fun createChannel(
        name: String, description: String, isPublic: Boolean, username: String,
        broadcastOnly: Boolean, creatorUid: String,
    ): Result<String> {
        return try {
            val clean = username.removePrefix("@").lowercase().trim()
            if (isPublic) {
                if (clean.length < 5) return Result.failure(Exception("Юзернейм канала — минимум 5 символов"))
                if (db.collection("channelnames").document(clean).get().exists) {
                    return Result.failure(Exception("@$clean уже занят"))
                }
            }
            val chat = SChat(
                type = SChatType.CHANNEL,
                name = name.trim(),
                description = description.trim(),
                memberIds = listOf(creatorUid),
                adminIds = listOf(creatorUid),
                createdBy = creatorUid,
                isPublic = isPublic,
                channelUsername = if (isPublic) clean else "",
                broadcastOnly = broadcastOnly,
                lastMessage = "Канал создан",
                lastMessageTime = Timestamp.ServerTimestamp,
            )
            val ref = db.collection("chats").add(chat)
            if (isPublic) {
                db.collection("channelnames").document(clean).set(mapOf("chatId" to ref.id, "name" to name.trim()))
            }
            Result.success(ref.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    // Поиск публичных каналов по названию/@username. Оба условия (type и isPublic) нужны, чтобы
    // правила Firestore разрешили чтение чужих каналов (то же, что whereEqualTo ×2 на Android).
    suspend fun searchChannels(query: String): List<SharedChat> = try {
        val q = query.removePrefix("@").lowercase().trim()
        if (q.length < 2) emptyList()
        else db.collection("chats")
            .where { "type" equalTo "CHANNEL" }
            .where { "isPublic" equalTo true }
            .get().documents
            .map { SharedChat(it.id, decodeChat(it)) }
            .filter { it.data.name.lowercase().contains(q) || it.data.channelUsername.contains(q) }
    } catch (e: Exception) { emptyList() }

    // Вступить в канал / добавить участника в группу или канал — одна и та же операция над memberIds
    // (правила Firestore разрешают чужому вступать в публичный канал только через это поле).
    suspend fun joinChannel(chatId: String, uid: String): Result<Unit> = try {
        val cur = chatRef(chatId).get().data<SChat>().memberIds
        chatRef(chatId).set(mapOf("memberIds" to (cur + uid).distinct()), merge = true)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun leaveChat(chatId: String, uid: String): Result<Unit> = try {
        val c = chatRef(chatId).get().data<SChat>()
        chatRef(chatId).set(
            mapOf(
                "memberIds" to c.memberIds.filter { it != uid },
                "adminIds" to c.adminIds.filter { it != uid },
            ),
            merge = true,
        )
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun setChatAdmin(chatId: String, uid: String, isAdmin: Boolean): Result<Unit> = try {
        val cur = chatRef(chatId).get().data<SChat>().adminIds
        val next = if (isAdmin) (cur + uid).distinct() else cur.filter { it != uid }
        chatRef(chatId).set(mapOf("adminIds" to next), merge = true)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun setChannelPublic(chatId: String, isPublic: Boolean) {
        chatRef(chatId).update("isPublic" to isPublic)
    }

    suspend fun setChannelBroadcastOnly(chatId: String, broadcastOnly: Boolean) {
        chatRef(chatId).update("broadcastOnly" to broadcastOnly)
    }

    // ═══════════════ GIF, картиночные стикеры, Premium-оформление (как в Android FirebaseRepo) ═══════════════

    // GIF — как стикер, только в mediaUrl лежит внешняя ссылка (Klipy CDN), сам файл нигде не хранится.
    suspend fun sendGif(chatId: String, gifUrl: String, senderUid: String, senderName: String) {
        chatRef(chatId).collection("messages").add(
            SMessage(chatId = chatId, senderId = senderUid, senderName = senderName,
                type = SMessageType.GIF, mediaUrl = gifUrl, timestamp = Timestamp.ServerTimestamp)
        )
        chatRef(chatId).update("lastMessage" to "GIF", "lastMessageTime" to Timestamp.ServerTimestamp, "lastSenderId" to senderUid)
    }

    // Картиночный стикер: PNG в Base64 в mediaData — ровно так их отправляет и читает Android.
    suspend fun sendImageSticker(chatId: String, base64: String, senderUid: String, senderName: String) {
        chatRef(chatId).collection("messages").add(
            SMessage(chatId = chatId, senderId = senderUid, senderName = senderName,
                type = SMessageType.STICKER, mediaData = base64, timestamp = Timestamp.ServerTimestamp)
        )
        chatRef(chatId).update("lastMessage" to "🌟 Стикер", "lastMessageTime" to Timestamp.ServerTimestamp, "lastSenderId" to senderUid)
    }

    suspend fun setPremiumBadgeIcon(uid: String, icon: String) {
        db.collection("users").document(uid).update("premiumBadgeIcon" to icon)
    }

    suspend fun setShowPremiumBadge(uid: String, show: Boolean) {
        db.collection("users").document(uid).update("showPremiumBadge" to show)
    }

    suspend fun setPremiumBubbleColor(uid: String, colorId: String) {
        db.collection("users").document(uid).update("premiumBubbleColor" to colorId)
    }

    // Промокод "gg" — бесплатный Premium на 1.5 недели (как redeemPromoCode на Android);
    // дальше его снимает расписание в Cloud Functions по premiumUntil.
    suspend fun redeemPromoCode(uid: String, code: String): Result<String> {
        if (code.trim().lowercase() != "gg") return Result.success("Такого промокода нет")
        return try {
            val until = Timestamp(nowEpochSeconds() + 907_200L, 0)
            db.collection("users").document(uid).update("isPremium" to true, "premiumUntil" to until)
            Result.success("⭐ Premium активирован на 1.5 недели")
        } catch (e: Exception) { Result.failure(e) }
    }

    // ═══════════════ Истории (Stories) — живут 24 часа, как в Android FirebaseRepo ═══════════════

    // Все ещё не истёкшие истории (коллекция маленькая; группировку по авторам и фильтр
    // «только контакты и я» делает вызывающий код — как на Android).
    fun observeActiveStories(): Flow<List<SharedStory>> =
        db.collection("stories")
            .where { "expiresAt" greaterThan Timestamp(nowEpochSeconds(), 0) }
            .orderBy("expiresAt", Direction.ASCENDING)
            .snapshots
            .map { snap -> snap.documents.map { SharedStory(it.id, it.data<SStory>()) } }

    // Фото грузим в Cloudinary (тот же аккаунт и пресет, что и для фото в чате), в документ
    // истории кладём ссылку в mediaUrl.
    suspend fun postStory(
        uid: String, name: String, photoUrl: String,
        bytes: ByteArray, fileName: String, mimeType: String,
    ): Result<Unit> {
        return try {
            val url = cloudinaryUpload(bytes, fileName, "image", mimeType).getOrElse { return Result.failure(it) }
            val now = nowEpochSeconds()
            db.collection("stories").add(
                SStory(
                    userId = uid, userName = name, userPhotoUrl = photoUrl, mediaUrl = url,
                    createdAt = Timestamp(now, 0), expiresAt = Timestamp(now + 86_400L, 0),
                )
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // «Посмотрел» — единственное поле чужой истории, которое правила Firestore разрешают менять.
    suspend fun markStoryViewed(storyId: String, viewerUid: String) {
        try {
            val ref = db.collection("stories").document(storyId)
            val cur = ref.get().data<SStory>().viewedBy
            if (!cur.contains(viewerUid)) ref.set(mapOf("viewedBy" to (cur + viewerUid)), merge = true)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> = try {
        db.collection("stories").document(storyId).delete()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // ═══════════════ Обои чата и админ-панель (как в Android FirebaseRepo) ═══════════════

    // Своё фото как обои: в Cloudinary, ссылка — в chatBackground; встроенный пресет сбрасываем,
    // иначе он продолжил бы перекрывать свежее фото (как на Android).
    suspend fun uploadChatBackground(uid: String, bytes: ByteArray, fileName: String, mimeType: String): Result<Unit> {
        return try {
            val url = cloudinaryUpload(bytes, fileName, "image", mimeType).getOrElse { return Result.failure(it) }
            db.collection("users").document(uid).update(
                "chatBackground" to url, "chatBackgroundData" to "", "chatWallpaperPreset" to "",
            )
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun clearChatBackground(uid: String): Result<Unit> = try {
        db.collection("users").document(uid).update(
            "chatBackground" to "", "chatBackgroundData" to "", "chatWallpaperPreset" to "",
        )
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun setChatWallpaperPreset(uid: String, presetId: String): Result<Unit> = try {
        db.collection("users").document(uid).update(
            "chatWallpaperPreset" to presetId, "chatBackground" to "", "chatBackgroundData" to "",
        )
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // Админ-панель: все пользователи; писать/удалять чужие документы правила Firestore разрешают
    // только тем, у кого в СВОЁМ документе isDeveloper == true.
    suspend fun getAllUsers(): List<SharedUser> = try {
        db.collection("users").get().documents.mapNotNull { d ->
            try { SharedUser(d.id, d.data<SUser>()) } catch (e: Exception) { null }
        }
    } catch (e: Exception) { emptyList() }

    // Premium вручную — бессрочный, поэтому premiumUntil от временного «gg» обнуляем.
    suspend fun setUserPremium(uid: String, premium: Boolean): Result<Unit> = try {
        db.collection("users").document(uid).update("isPremium" to premium, "premiumUntil" to null)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun setUserDeveloper(uid: String, developer: Boolean): Result<Unit> = try {
        db.collection("users").document(uid).update("isDeveloper" to developer)
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun deleteUserDocument(uid: String): Result<Unit> = try {
        db.collection("users").document(uid).delete()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
