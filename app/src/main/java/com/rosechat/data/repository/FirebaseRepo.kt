package com.rosechat.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.Timestamp
import java.util.Date
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.rosechat.data.local.ImageCompress
import com.rosechat.data.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.TimeUnit

object FirebaseRepo {
    val auth    get() = FirebaseAuth.getInstance()
    val db      get() = FirebaseFirestore.getInstance()
    val storage get() = FirebaseStorage.getInstance()
    val currentUid get() = auth.currentUser?.uid
    val isLoggedIn get() = auth.currentUser != null

    // ══════════════════════════════════════════
    // AUTH — ТЕЛЕФОН
    // ══════════════════════════════════════════

    fun sendPhoneOtp(
        phone: String,
        activity: android.app.Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phone)
                .setTimeout(120L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
        )
    }

    suspend fun signInWithPhone(credential: PhoneAuthCredential): Result<Pair<String, Boolean>> {
        return try {
            val result = auth.signInWithCredential(credential).await()
            val uid = result.user?.uid ?: return Result.failure(Exception("UID null"))
            val isNew = ensureUser(uid, phone = result.user?.phoneNumber ?: "")
            ensureSavedMessages(uid)
            Result.success(uid to isNew)
        } catch (e: Exception) { Result.failure(e) }
    }

    // ══════════════════════════════════════════
    // AUTH — GOOGLE
    // ══════════════════════════════════════════

    suspend fun signInWithGoogle(idToken: String): Result<Pair<String, Boolean>> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: return Result.failure(Exception("UID null"))
            val isNew = ensureUser(
                uid = user.uid,
                phone = user.phoneNumber ?: "",
                displayName = user.displayName ?: "",
                email = user.email ?: "",
                photoUrl = user.photoUrl?.toString() ?: ""
            )
            ensureSavedMessages(user.uid)
            Result.success(user.uid to isNew)
        } catch (e: Exception) { Result.failure(e) }
    }

    // ══════════════════════════════════════════
    // AUTH — EMAIL
    // ══════════════════════════════════════════

    suspend fun registerEmail(
        email: String,
        password: String,
        name: String,
        username: String
    ): Result<Pair<String, Boolean>> {
        return try {
            // Проверяем username если введён
            val cleanUsername = username.lowercase().trim()
            if (cleanUsername.length >= 3) {
                val check = db.collection("usernames").document(cleanUsername).get().await()
                if (check.exists()) return Result.failure(Exception("@$cleanUsername уже занят"))
            }

            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return Result.failure(Exception("UID null"))

            // Создаём профиль
            val user = User(
                uid = uid,
                displayName = name.trim(),
                username = cleanUsername,
                email = email,
                createdAt = Timestamp.now(),
                termsAcceptedAt = Timestamp.now()
            )
            db.collection("users").document(uid).set(user).await()

            // Сохраняем username → uid
            if (cleanUsername.length >= 3) {
                db.collection("usernames").document(cleanUsername).set(
                    mapOf("uid" to uid, "displayName" to name.trim())
                ).await()
            }

            // Создаём Избранное
            createSavedMessages(uid)

            Result.success(uid to true)  // регистрация — всегда новый пользователь
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun signInEmail(email: String, password: String): Result<Pair<String, Boolean>> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: return Result.failure(Exception("UID null"))
            // Обновляем online статус
            db.collection("users").document(uid)
                .update(mapOf("isOnline" to true, "lastSeen" to Timestamp.now())).await()
            ensureSavedMessages(uid)
            Result.success(uid to false)  // вход — уже существующий пользователь
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return try { auth.sendPasswordResetEmail(email).await(); Result.success(Unit) }
        catch (e: Exception) { Result.failure(e) }
    }

    // Сначала снимаем push-токен с профиля, потом выходим: иначе на общем устройстве
    // пуши прежнего аккаунта продолжали бы приходить уже новому. Запись без сети может
    // "зависнуть" до появления интернета, поэтому выход не ждёт её дольше 2.5 секунд.
    fun signOut() {
        val uid = currentUid
        lastSavedToken = null
        NotificationService.stop(com.rosechat.RoseChatApp.instance)
        if (uid == null) { auth.signOut(); return }
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finish() {
            if (done.compareAndSet(false, true)) {
                runCatching { com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken() }
                auth.signOut()
            }
        }
        db.collection("users").document(uid).update("fcmToken", "").addOnCompleteListener { finish() }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish() }, 2500)
    }

    // ══════════════════════════════════════════
    // PUSH-ТОКЕН
    // Раньше токен сохранялся ТОЛЬКО в FcmService.onNewToken, а он срабатывает лишь при
    // создании/смене токена — обычно до входа в аккаунт, когда uid ещё нет. В итоге
    // у пользователей fcmToken оставался пустым, Cloud Functions пропускали их
    // (`if (!token) return`), и пуши не приходили вообще. Теперь токен запрашивается
    // явно при каждом запуске/входе.
    // ══════════════════════════════════════════

    private var lastSavedToken: Pair<String, String>? = null

    // Счётчик непрочитанных теперь увеличивает клиент отправителя (раньше это делала только
    // Cloud Function, а она требует платного плана Blaze и на Spark не разворачивается —
    // поэтому бейджи не росли вообще). Для каждого участника, кроме отправителя, +1.
    // Функция глотает ошибки: неудачный счётчик не должен ломать отправку сообщения.
    private suspend fun bumpUnread(chatId: String, senderUid: String) {
        try {
            val ref = db.collection("chats").document(chatId)
            val snap = try { ref.get(com.google.firebase.firestore.Source.CACHE).await() } catch (_: Exception) { ref.get().await() }
            val members = (snap.get("memberIds") as? List<*>)?.filterIsInstance<String>() ?: return
            val inc = members.filter { it != senderUid }.associate { "unreadCount.$it" to FieldValue.increment(1) }
            if (inc.isNotEmpty()) ref.update(inc).await()
        } catch (e: Exception) {
            Log.w("FirebaseRepo", "bumpUnread failed: ${e.message}")
        }
    }

    fun saveFcmToken(token: String) {
        val uid = currentUid ?: return
        if (token.isBlank() || lastSavedToken == (uid to token)) return
        db.collection("users").document(uid).update("fcmToken", token)
            .addOnSuccessListener { lastSavedToken = uid to token }
            // Документ профиля может ещё не существовать (сразу после регистрации) —
            // тогда повторим при следующем запуске/открытии главного экрана.
            .addOnFailureListener { Log.w("FirebaseRepo", "saveFcmToken failed: ${it.message}") }
    }

    fun registerPushToken() {
        if (currentUid == null) return
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { saveFcmToken(it) }
            .addOnFailureListener { Log.w("FirebaseRepo", "getToken failed: ${it.message}") }
    }

    // ══════════════════════════════════════════
    // ИЗБРАННОЕ
    // ══════════════════════════════════════════

    private suspend fun createSavedMessages(uid: String) {
        try {
            val existing = db.collection("chats")
                .whereEqualTo("type", "SAVED")
                .whereArrayContains("memberIds", uid)
                .get().await()
            if (existing.isEmpty) {
                val chat = hashMapOf(
                    "type" to "SAVED",
                    "name" to "Избранное",
                    "memberIds" to listOf(uid),
                    "adminIds" to listOf(uid),
                    "createdBy" to uid,
                    "lastMessage" to "Сохраняй сюда всё важное",
                    "lastMessageTime" to Timestamp.now(),
                    "createdAt" to Timestamp.now()
                )
                db.collection("chats").add(chat).await()
            }
        } catch (_: Exception) {}
    }

    private suspend fun ensureSavedMessages(uid: String) {
        createSavedMessages(uid)
    }

    // ══════════════════════════════════════════
    // ПОЛЬЗОВАТЕЛИ
    // ══════════════════════════════════════════

    private suspend fun ensureUser(
        uid: String, phone: String = "", displayName: String = "",
        email: String = "", photoUrl: String = ""
    ): Boolean {
        val ref = db.collection("users").document(uid)
        val isNew = !ref.get().await().exists()
        if (isNew) {
            ref.set(
                User(
                    uid = uid,
                    displayName = displayName.ifBlank { phone.ifBlank { "Пользователь" } },
                    phone = phone, email = email, photoUrl = photoUrl,
                    createdAt = Timestamp.now(),
                    termsAcceptedAt = Timestamp.now()
                )
            ).await()
        }
        return isNew
    }

    suspend fun getUser(uid: String): User? {
        return try {
            db.collection("users").document(uid).get().await().toObject(User::class.java)
        } catch (_: Exception) { null }
    }

    // Живой listener вместо разового getUser(). Раньше экраны читали профиль один раз при
    // старте — из-за этого активация Premium ("gg" в Избранном), смена аватарки и т.п. не
    // отражались в UI без перезапуска приложения. Теперь isPremium/фото/стикер обновляются сами.
    fun observeUser(uid: String): Flow<User?> = callbackFlow {
        val listener = db.collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObject(User::class.java))
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateUser(uid: String, updates: Map<String, Any>) {
        db.collection("users").document(uid).update(updates).await()
    }

    suspend fun updateUsername(uid: String, newUsername: String): Result<Unit> {
        return try {
            val clean = newUsername.lowercase().trim()
            val check = db.collection("usernames").document(clean).get().await()
            if (check.exists() && check.getString("uid") != uid)
                return Result.failure(Exception("@$clean уже занят"))
            val user = getUser(uid)
            // Удаляем старый username
            if (user?.username?.isNotEmpty() == true) {
                db.collection("usernames").document(user.username).delete().await()
            }
            // Сохраняем новый
            val name = user?.displayName ?: ""
            db.collection("usernames").document(clean).set(mapOf("uid" to uid, "displayName" to name)).await()
            db.collection("users").document(uid).update("username", clean).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // Если настроен Cloudinary (см. local.properties → CLOUDINARY_CLOUD_NAME/CLOUDINARY_UPLOAD_PRESET) — грузим фото туда
    // и храним обычную ссылку в photoUrl (поле для этого и заведено, см. Models.kt).
    // Если ключи не заданы — как и раньше, Base64 прямо в документе пользователя
    // (photoData), ничего не ломается без настройки внешнего хранилища.
    // Раньше сбой самой загрузки в Cloudinary (сеть, отключённый/пересозданный unsigned-пресет,
    // HTTP 401 и т.п.) сразу проваливал всю функцию — даже когда ключи настроены правильно, но
    // сервис на этот раз не ответил. Теперь при любом сбое реальной загрузки (не только когда
    // ключи вовсе не заданы) откатываемся на тот же Base64-путь, что раньше был только для
    // случая "Cloudinary не настроен" — фото всё равно долетает, просто без экономии места.
    suspend fun uploadAvatar(context: Context, uid: String, uri: Uri): Result<Unit> {
        if (CloudinaryRepository.isConfigured) {
            val bytes = ImageCompress.compressToBytes(context, uri, maxDim = 720, targetBytes = 1_500_000)
                ?: return Result.failure(Exception("Не удалось обработать изображение"))
            val urlResult = CloudinaryRepository.uploadImage(bytes, "avatar_$uid.jpg")
            val url = urlResult.getOrNull()
            if (url != null) {
                return try {
                    db.collection("users").document(uid).update(mapOf("photoUrl" to url, "photoData" to "")).await()
                    Result.success(Unit)
                } catch (e: Exception) { Result.failure(e) }
            }
            Log.w("FirebaseRepo", "Cloudinary avatar upload failed, falling back to Base64: ${urlResult.exceptionOrNull()?.message}")
        }
        return try {
            val base64 = ImageCompress.compressToBase64(context, uri, maxDim = 480, targetBytes = 350_000)
                ?: return Result.failure(Exception("Не удалось обработать изображение"))
            db.collection("users").document(uid).update(mapOf("photoData" to base64, "photoUrl" to "")).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun uploadChatBackground(context: Context, uid: String, uri: Uri): Result<Unit> {
        if (CloudinaryRepository.isConfigured) {
            val bytes = ImageCompress.compressToBytes(context, uri, maxDim = 1280, targetBytes = 2_500_000)
                ?: return Result.failure(Exception("Не удалось обработать изображение"))
            val urlResult = CloudinaryRepository.uploadImage(bytes, "bg_$uid.jpg")
            val url = urlResult.getOrNull()
            if (url != null) {
                return try {
                    // Сбрасываем встроенный градиент-пресет (chatWallpaperPreset) — свежезагруженное
                    // фото приоритетнее, иначе старый пресет продолжил бы перекрывать его при показе.
                    db.collection("users").document(uid).update(mapOf("chatBackground" to url, "chatBackgroundData" to "", "chatWallpaperPreset" to "")).await()
                    Result.success(Unit)
                } catch (e: Exception) { Result.failure(e) }
            }
            Log.w("FirebaseRepo", "Cloudinary background upload failed, falling back to Base64: ${urlResult.exceptionOrNull()?.message}")
        }
        return try {
            val base64 = ImageCompress.compressToBase64(context, uri, maxDim = 960, targetBytes = 650_000)
                ?: return Result.failure(Exception("Не удалось обработать изображение"))
            db.collection("users").document(uid).update(mapOf("chatBackgroundData" to base64, "chatBackground" to "", "chatWallpaperPreset" to "")).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // Раньше карточка "Фон чата" в настройках умела только ставить новое фото — убрать
    // уже установленный фон было нечем (см. чат: "можно было убрать фон обоев"). Сбрасывает
    // все три поля разом, чат возвращается к обычному сплошному фону.
    suspend fun clearChatBackground(uid: String): Result<Unit> = try {
        db.collection("users").document(uid)
            .update(mapOf("chatBackground" to "", "chatBackgroundData" to "", "chatWallpaperPreset" to "")).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // Встроенные обои (градиенты, см. CHAT_WALLPAPER_PRESETS в SmartImage.kt) — без загрузки
    // своей фотографии, просто выбор готового варианта. Хранится отдельным полем, а не как
    // спецпрефикс в chatBackground — так не нужно трогать формат уже занятого поля.
    suspend fun setChatWallpaperPreset(uid: String, presetId: String): Result<Unit> = try {
        db.collection("users").document(uid)
            .update(mapOf("chatWallpaperPreset" to presetId, "chatBackground" to "", "chatBackgroundData" to "")).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // Поиск по @username (точное совпадение)
    suspend fun findByUsername(username: String): User? {
        return try {
            val clean = username.removePrefix("@").lowercase().trim()
            if (clean.isEmpty()) return null
            val doc = db.collection("usernames").document(clean).get().await()
            val uid = doc.getString("uid") ?: return null
            getUser(uid)
        } catch (_: Exception) { null }
    }

    // Поиск по номеру телефона
    suspend fun findByPhone(phone: String): User? {
        return try {
            db.collection("users").whereEqualTo("phone", phone).limit(1).get().await()
                .toObjects(User::class.java).firstOrNull()
        } catch (_: Exception) { null }
    }

    // Поиск по имени — локально по всем пользователям
    suspend fun findByEmail(email: String): User? {
        return try {
            db.collection("users")
                .whereEqualTo("email", email.trim().lowercase())
                .limit(1).get().await()
                .toObjects(User::class.java)
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    // Приводим номер к "голым" цифрам и меняем ведущую 8 на 7 — иначе поиск по
    // привычному локальному формату (8999...) не находит номер, сохранённый как +7999...
    private fun normalizePhoneDigits(raw: String): String {
        var d = raw.filter { it.isDigit() }
        if (d.length == 11 && d.startsWith("8")) d = "7" + d.substring(1)
        return d
    }

    suspend fun searchByName(query: String, myUid: String): List<User> {
        return try {
            val all = db.collection("users").get().await().toObjects(User::class.java)
            val q = query.lowercase().trim()
            val qDigits = normalizePhoneDigits(q)
            all.filter { u ->
                u.uid != myUid && (
                    u.displayName.lowercase().contains(q) ||
                    u.username.lowercase().contains(q) ||
                    u.phone.contains(q) ||
                    (qDigits.length >= 4 && normalizePhoneDigits(u.phone).contains(qDigits))
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getAllUsers(): List<User> {
        return try { db.collection("users").get().await().toObjects(User::class.java) }
        catch (_: Exception) { emptyList() }
    }

    // Premium через "gg" — пишешь "gg" в Избранное. Временный: 1.5 недели, дальше сгорает сам
    // (см. cleanupExpiredPremium в functions/index.js). Админ-панель по-прежнему может
    // выдать бессрочный Premium вручную (toggle без premiumUntil, см. DeveloperScreen).
    private val GG_PREMIUM_DURATION_MS = (1.5 * 7 * 24 * 60 * 60 * 1000).toLong() // 1.5 недели

    suspend fun activatePremium(uid: String): Result<Unit> {
        return try {
            val until = Timestamp(Date(System.currentTimeMillis() + GG_PREMIUM_DURATION_MS))
            db.collection("users").document(uid)
                .update(mapOf("isPremium" to true, "premiumUntil" to until)).await()
            Result.success(Unit)
        }
        catch (e: Exception) { Result.failure(e) }
    }

    // Premium через настоящую оплату (Google Play Billing, см. BillingRepository.kt) — вызывается
    // после подтверждённой покупки подписки. Точно такая же самозапись клиента, как и "gg" выше
    // (см. предупреждение про доверие в BillingRepository.kt) — until считает вызывающая сторона
    // (обычно "сейчас + ~месяц с запасом").
    suspend fun activatePaidPremium(uid: String, until: Timestamp): Result<Unit> {
        return try {
            db.collection("users").document(uid)
                .update(mapOf("isPremium" to true, "premiumUntil" to until)).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun setPremiumFrame(uid: String, frame: String): Result<Unit> {
        return try { db.collection("users").document(uid).update("premiumFrame", frame).await(); Result.success(Unit) }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun setShowPremiumBadge(uid: String, show: Boolean): Result<Unit> {
        return try { db.collection("users").document(uid).update("showPremiumBadge", show).await(); Result.success(Unit) }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun setPremiumBadgeIcon(uid: String, icon: String): Result<Unit> {
        return try { db.collection("users").document(uid).update("premiumBadgeIcon", icon).await(); Result.success(Unit) }
        catch (e: Exception) { Result.failure(e) }
    }

    suspend fun setPremiumBubbleColor(uid: String, colorId: String): Result<Unit> {
        return try { db.collection("users").document(uid).update("premiumBubbleColor", colorId).await(); Result.success(Unit) }
        catch (e: Exception) { Result.failure(e) }
    }

    fun setOnline(uid: String, online: Boolean) {
        try {
            db.collection("users").document(uid)
                .update(mapOf("isOnline" to online, "lastSeen" to Timestamp.now()))
        } catch (_: Exception) {}
    }

    // Статус "печатает…" — храним прямо на документе чата (простой список uid).
    // Пишем не на каждое нажатие клавиши, а с debounce на стороне вызывающего кода.
    fun setTyping(chatId: String, uid: String, typing: Boolean) {
        try {
            db.collection("chats").document(chatId)
                .update("typingBy", if (typing) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid))
        } catch (_: Exception) {}
    }

    // Отмечает чат прочитанным для uid — момент, до которого все сообщения собеседника
    // считаются прочитанными (используется для галочек ✓/✓✓ у своих сообщений).
    fun markChatRead(chatId: String, uid: String) {
        try {
            db.collection("chats").document(chatId)
                .update("lastReadAt.$uid", Timestamp.now())
        } catch (_: Exception) {}
    }

    // Сброс личного счётчика непрочитанных (бейдж в списке чатов) — намеренно отдельно
    // от markChatRead: это не про то, видит ли собеседник галочку ✓✓ (настройка
    // приватности), а про собственный бейдж пользователя, который должен обнуляться
    // всегда при открытии чата, независимо от sendReadReceipts.
    fun clearUnread(chatId: String, uid: String) {
        try {
            db.collection("chats").document(chatId)
                .update("unreadCount.$uid", 0)
        } catch (_: Exception) {}
    }

    fun pinMessage(chatId: String, messageId: String) {
        try { db.collection("chats").document(chatId).update("pinnedMessageId", messageId) } catch (_: Exception) {}
    }

    fun unpinMessage(chatId: String) {
        try { db.collection("chats").document(chatId).update("pinnedMessageId", "") } catch (_: Exception) {}
    }

    // ══════════════════════════════════════════
    // ЧАТЫ
    // ══════════════════════════════════════════

    fun getChats(uid: String): Flow<List<Chat>> = callbackFlow {
        val listener = db.collection("chats")
            .whereArrayContains("memberIds", uid)
            .orderBy("lastMessageTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                // Раньше ошибка молча игнорировалась (snap становится null, и список
                // просто схлопывался в пустой без единого объяснения — так выглядел
                // баг "чат мелькает и пропадает из вкладки"). Теперь она долетает
                // до экрана и показывается снекбаром.
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.toObjects(Chat::class.java) ?: emptyList()
                val sorted = list.sortedWith(compareByDescending<Chat> { it.type == ChatType.SAVED }
                    .thenByDescending { it.lastMessageTime })
                trySend(sorted)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getOrCreatePrivateChat(myUid: String, otherUid: String): String {
        val existing = db.collection("chats")
            .whereEqualTo("type", ChatType.PRIVATE.name)
            .whereArrayContains("memberIds", myUid)
            .get().await()
        val found = existing.toObjects(Chat::class.java)
            .firstOrNull { it.memberIds.contains(otherUid) }
        if (found != null) return found.id
        val chat = Chat(type = ChatType.PRIVATE, memberIds = listOf(myUid, otherUid))
        return db.collection("chats").add(chat).await().id
    }

    suspend fun createGroup(name: String, memberIds: List<String>, creatorUid: String): String {
        val chat = Chat(
            type = ChatType.GROUP,
            name = name.trim(),
            memberIds = (memberIds + creatorUid).distinct(),
            adminIds = listOf(creatorUid),
            createdBy = creatorUid,
            createdAt = Timestamp.now()
        )
        return db.collection("chats").add(chat).await().id
    }

    suspend fun getChatPartnerName(chat: Chat, myUid: String): String {
        return when (chat.type) {
            ChatType.GROUP, ChatType.CHANNEL -> chat.name
            ChatType.SAVED -> "Избранное"
            ChatType.PRIVATE -> {
                val otherId = chat.memberIds.firstOrNull { it != myUid } ?: return "Чат"
                getUser(otherId)?.displayName ?: "Пользователь"
            }
        }
    }

    suspend fun getChatPartnerPhoto(chat: Chat, myUid: String): PhotoRef {
        if (chat.type != ChatType.PRIVATE) return PhotoRef(chat.photoUrl, "")
        val otherId = chat.memberIds.firstOrNull { it != myUid } ?: return PhotoRef()
        val u = getUser(otherId) ?: return PhotoRef()
        return PhotoRef(u.photoUrl, u.photoData)
    }

    // Живое отслеживание одного чата (нужно для экрана канала — статус подписки, список админов и т.п.)
    fun getChat(chatId: String): Flow<Chat?> = callbackFlow {
        val listener = db.collection("chats").document(chatId)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObject(Chat::class.java))
            }
        awaitClose { listener.remove() }
    }

    // ══════════════════════════════════════════
    // КАНАЛЫ
    // ══════════════════════════════════════════

    suspend fun createChannel(
        name: String, description: String, isPublic: Boolean,
        username: String, broadcastOnly: Boolean, creatorUid: String
    ): Result<String> {
        return try {
            val cleanUsername = username.lowercase().trim()
            if (isPublic) {
                if (cleanUsername.length < 5) return Result.failure(Exception("Юзернейм канала — минимум 5 символов"))
                val check = db.collection("channelnames").document(cleanUsername).get().await()
                if (check.exists()) return Result.failure(Exception("@$cleanUsername уже занят"))
            }
            val chat = Chat(
                type = ChatType.CHANNEL,
                name = name.trim(),
                description = description.trim(),
                memberIds = listOf(creatorUid),
                adminIds = listOf(creatorUid),
                createdBy = creatorUid,
                createdAt = Timestamp.now(),
                isPublic = isPublic,
                channelUsername = if (isPublic) cleanUsername else "",
                broadcastOnly = broadcastOnly,
                lastMessage = "Канал создан",
                lastMessageTime = Timestamp.now()
            )
            val ref = db.collection("chats").add(chat).await()
            if (isPublic) {
                db.collection("channelnames").document(cleanUsername)
                    .set(mapOf("chatId" to ref.id, "name" to name.trim())).await()
            }
            Result.success(ref.id)
        } catch (e: Exception) { Result.failure(e) }
    }

    // Поиск публичных каналов по названию/юзернейму (для глобального поиска).
    // Раньше ошибка тут (например "не хватает индекса") тонула в emptyList() —
    // строка ниже больше не глотает исключение, оно долетает до onQueryChanged.
    suspend fun searchChannels(query: String): List<Chat> {
        val q = query.removePrefix("@").lowercase().trim()
        if (q.length < 2) return emptyList()
        return db.collection("chats")
            .whereEqualTo("type", ChatType.CHANNEL.name)
            .whereEqualTo("isPublic", true)
            .get().await()
            .toObjects(Chat::class.java)
            .filter { it.name.lowercase().contains(q) || it.channelUsername.contains(q) }
    }

    suspend fun joinChannel(chatId: String, uid: String): Result<Unit> {
        return try {
            db.collection("chats").document(chatId)
                .update("memberIds", FieldValue.arrayUnion(uid)).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun leaveChannel(chatId: String, uid: String): Result<Unit> {
        return try {
            db.collection("chats").document(chatId).update(
                mapOf(
                    "memberIds" to FieldValue.arrayRemove(uid),
                    "adminIds" to FieldValue.arrayRemove(uid)
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun setChannelAdmin(chatId: String, uid: String, isAdmin: Boolean) {
        db.collection("chats").document(chatId)
            .update("adminIds", if (isAdmin) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)).await()
    }

    suspend fun addChannelMember(chatId: String, uid: String) {
        db.collection("chats").document(chatId).update("memberIds", FieldValue.arrayUnion(uid)).await()
    }

    suspend fun updateChannelInfo(chatId: String, updates: Map<String, Any>) {
        db.collection("chats").document(chatId).update(updates).await()
    }

    // ══════════════════════════════════════════
    // АРХИВ ЧАТОВ (у каждого участника — своё состояние)
    // ══════════════════════════════════════════

    suspend fun archiveChat(chatId: String, uid: String) {
        db.collection("chats").document(chatId)
            .update("archivedBy", FieldValue.arrayUnion(uid)).await()
    }

    suspend fun unarchiveChat(chatId: String, uid: String) {
        db.collection("chats").document(chatId)
            .update("archivedBy", FieldValue.arrayRemove(uid)).await()
    }

    // ══════════════════════════════════════════
    // СООБЩЕНИЯ
    // ══════════════════════════════════════════

    fun getMessages(chatId: String): Flow<List<Message>> = callbackFlow {
        val listener = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(Message::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // Возвращает Result<String?> — при успехе строка не-null означает "только что включили Premium"
    // (для мгновенного тоста в UI, не дожидаясь live-listener). Раньше вся функция была без
    // единого try/catch — если бы Firestore отклонил запись (например, из-за недоразвёрнутых
    // правил), ошибка тонула молча и выглядело так, будто "просто ничего не произошло".
    suspend fun sendText(chatId: String, text: String, sender: User): Result<String?> {
        return try {
            val now = Timestamp.now()
            db.collection("chats").document(chatId).collection("messages")
                .add(Message(chatId = chatId, senderId = sender.uid, senderName = sender.displayName,
                    type = MessageType.TEXT, text = text, timestamp = now)).await()
            db.collection("chats").document(chatId)
                .update(mapOf("lastMessage" to text, "lastMessageTime" to now, "lastSenderId" to sender.uid)).await()
            bumpUnread(chatId, sender.uid)

            var premiumMsg: String? = null
            if (text.trim().lowercase() == "gg" && !sender.isPremium) {
                val chat = db.collection("chats").document(chatId).get().await().toObject(Chat::class.java)
                if (chat?.type == ChatType.SAVED) {
                    activatePremium(sender.uid)
                        .onSuccess {
                            // Читаем напрямую с сервера, минуя локальный кэш — чтобы точно увидеть,
                            // долетела ли запись, а не гадать по тому, что показывает UI.
                            val verified = try {
                                db.collection("users").document(sender.uid)
                                    .get(com.google.firebase.firestore.Source.SERVER).await()
                                    .getBoolean("isPremium")
                            } catch (e: Exception) { null }
                            premiumMsg = when (verified) {
                                true -> "⭐ Premium активирован (подтверждено с сервера)"
                                else -> "Запись прошла, но сервер вернул isPremium=$verified — сообщи об этом"
                            }
                        }
                        .onFailure { e -> premiumMsg = "Не удалось включить Premium: ${e.message}" }
                }
            }
            Result.success(premiumMsg)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun sendSticker(chatId: String, sticker: String, sender: User) {
        val now = Timestamp.now()
        db.collection("chats").document(chatId).collection("messages")
            .add(Message(chatId = chatId, senderId = sender.uid, senderName = sender.displayName,
                type = MessageType.STICKER, sticker = sticker, timestamp = now)).await()
        db.collection("chats").document(chatId)
            .update(mapOf("lastMessage" to sticker, "lastMessageTime" to now, "lastSenderId" to sender.uid)).await()
            bumpUnread(chatId, sender.uid)
    }

    // GIF — как стикер, только храним внешнюю ссылку (Klipy CDN) в mediaUrl, а не эмодзи.
    // Base64/Storage не нужны — сам файл нигде не хранится, только ссылка.
    suspend fun sendGif(chatId: String, gifUrl: String, sender: User) {
        val now = Timestamp.now()
        db.collection("chats").document(chatId).collection("messages")
            .add(Message(chatId = chatId, senderId = sender.uid, senderName = sender.displayName,
                type = MessageType.GIF, mediaUrl = gifUrl, timestamp = now)).await()
        db.collection("chats").document(chatId)
            .update(mapOf("lastMessage" to "GIF", "lastMessageTime" to now, "lastSenderId" to sender.uid)).await()
            bumpUnread(chatId, sender.uid)
    }

    suspend fun sendMedia(
        chatId: String, sender: User, type: MessageType, uri: Uri,
        duration: Int = 0, fileName: String = "", fileSize: Long = 0
    ): Result<Unit> {
        return try {
            val folder = when (type) {
                MessageType.VOICE -> "voice"; MessageType.VIDEO_NOTE -> "video_notes"
                MessageType.IMAGE -> "images"; MessageType.FILE -> "files"; else -> "media"
            }
            val ext = when (type) {
                MessageType.VOICE -> "m4a"; MessageType.VIDEO_NOTE -> "mp4"
                MessageType.FILE -> fileName.substringAfterLast('.', "bin"); else -> "jpg"
            }
            val ref = storage.reference.child("$folder/$chatId/${UUID.randomUUID()}.$ext")
            ref.putFile(uri).await()
            val url = ref.downloadUrl.await().toString()
            val preview = when (type) {
                MessageType.VOICE -> "🎤 Голосовое ${duration}с"
                MessageType.VIDEO_NOTE -> "⭕ Видео"
                MessageType.IMAGE -> "🖼 Фото"
                MessageType.FILE -> "📎 $fileName"
                else -> "Медиа"
            }
            val now = Timestamp.now()
            db.collection("chats").document(chatId).collection("messages")
                .add(Message(chatId = chatId, senderId = sender.uid, senderName = sender.displayName,
                    type = type, mediaUrl = url, duration = duration,
                    fileName = fileName, fileSize = fileSize, timestamp = now)).await()
            db.collection("chats").document(chatId)
                .update(mapOf("lastMessage" to preview, "lastMessageTime" to now, "lastSenderId" to sender.uid)).await()
            bumpUnread(chatId, sender.uid)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // Временное решение для голосовых, пока не подключён Storage (нужен Blaze-план):
    // аудио кладём прямо в документ сообщения как Base64. Ограничение по длительности
    // (см. MAX_VOICE_SECONDS в ChatViewModel) держит размер далеко от лимита Firestore в 1 МБ на документ.
    // Когда перейдёшь на Blaze — заменим на storage.reference.putFile(...) как в sendMedia(),
    // структура сообщения (mediaUrl вместо audioData) уже для этого готова.
    suspend fun sendVoiceMessage(chatId: String, sender: User, base64Audio: String, duration: Int): Result<Unit> {
        return try {
            val now = Timestamp.now()
            db.collection("chats").document(chatId).collection("messages")
                .add(Message(chatId = chatId, senderId = sender.uid, senderName = sender.displayName,
                    type = MessageType.VOICE, audioData = base64Audio, duration = duration, timestamp = now)).await()
            db.collection("chats").document(chatId)
                .update(mapOf("lastMessage" to "🎤 Голосовое ${duration}с", "lastMessageTime" to now, "lastSenderId" to sender.uid)).await()
            bumpUnread(chatId, sender.uid)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // Видео-кружки — та же Base64-в-документ схема, что и голосовые (см. sendVoiceMessage).
    // Здесь это даже более узкое место: видео весит намного больше голоса при том же лимите
    // документа в 1 МБ — отсюда жёсткое ограничение по длительности и битрейту при записи
    // (см. VideoNoteRecorder.MAX_DURATION_SEC), это сознательный компромисс, а не недосмотр.
    suspend fun sendVideoNote(chatId: String, sender: User, base64Video: String, duration: Int): Result<Unit> {
        return try {
            val now = Timestamp.now()
            db.collection("chats").document(chatId).collection("messages")
                .add(Message(chatId = chatId, senderId = sender.uid, senderName = sender.displayName,
                    type = MessageType.VIDEO_NOTE, mediaData = base64Video, duration = duration, timestamp = now)).await()
            db.collection("chats").document(chatId)
                .update(mapOf("lastMessage" to "⭕ Видео", "lastMessageTime" to now, "lastSenderId" to sender.uid)).await()
            bumpUnread(chatId, sender.uid)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // Та же схема, что и sendVoiceMessage: пока нет Storage, фото/файлы едут как Base64
    // прямо в документе сообщения. Фото сжимается заранее (см. ImageCompress), файлы —
    // просто с ограничением по размеру (см. MAX_FILE_BYTES в ChatViewModel).
    // Обычное видео (не кружок) — перекодированное через VideoCompress под лимит документа,
    // с сохранением пропорций (в отличие от VIDEO_NOTE, который всегда квадратный).
    suspend fun sendVideo(chatId: String, sender: User, base64Video: String, duration: Int): Result<Unit> {
        return try {
            val now = Timestamp.now()
            db.collection("chats").document(chatId).collection("messages")
                .add(Message(chatId = chatId, senderId = sender.uid, senderName = sender.displayName,
                    type = MessageType.VIDEO, mediaData = base64Video, duration = duration, timestamp = now)).await()
            db.collection("chats").document(chatId)
                .update(mapOf("lastMessage" to "🎬 Видео", "lastMessageTime" to now, "lastSenderId" to sender.uid)).await()
            bumpUnread(chatId, sender.uid)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // Отправка медиа-сообщения, когда файл уже загружен во внешнее хранилище (Cloudinary)
    // и есть готовая ссылка — используется для фото/файлов/видео/кружков/голосовых, когда
    // настроен CLOUDINARY_CLOUD_NAME/CLOUDINARY_UPLOAD_PRESET в local.properties (см. ChatViewModel в ChatScreen.kt). Пока
    // ключи не заданы, эти же типы сообщений по-прежнему едут через sendMediaBase64/
    // sendVideo/sendVideoNote/sendVoiceMessage ниже.
    suspend fun sendMediaUrl(chatId: String, sender: User, type: MessageType, url: String, duration: Int = 0, fileName: String = "", fileSize: Long = 0): Result<Unit> {
        return try {
            val preview = when (type) {
                MessageType.IMAGE -> "🖼 Фото"
                MessageType.FILE -> "📎 ${fileName.ifBlank { "Файл" }}"
                MessageType.VIDEO -> "🎬 Видео"
                MessageType.VIDEO_NOTE -> "⭕ Видео"
                MessageType.VOICE -> "🎤 Голосовое ${duration}с"
                else -> "Медиа"
            }
            val now = Timestamp.now()
            db.collection("chats").document(chatId).collection("messages")
                .add(Message(chatId = chatId, senderId = sender.uid, senderName = sender.displayName,
                    type = type, mediaUrl = url, duration = duration, fileName = fileName, fileSize = fileSize, timestamp = now)).await()
            db.collection("chats").document(chatId)
                .update(mapOf("lastMessage" to preview, "lastMessageTime" to now, "lastSenderId" to sender.uid)).await()
            bumpUnread(chatId, sender.uid)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun sendMediaBase64(chatId: String, sender: User, type: MessageType, base64Data: String, fileName: String = "", fileSize: Long = 0): Result<Unit> {
        return try {
            val now = Timestamp.now()
            val preview = when (type) { MessageType.IMAGE -> "🖼 Фото"; MessageType.FILE -> "📎 ${fileName.ifBlank { "Файл" }}"; MessageType.STICKER -> "🌟 Стикер"; else -> "Медиа" }
            db.collection("chats").document(chatId).collection("messages")
                .add(Message(chatId = chatId, senderId = sender.uid, senderName = sender.displayName,
                    type = type, mediaData = base64Data, fileName = fileName, fileSize = fileSize, timestamp = now)).await()
            db.collection("chats").document(chatId)
                .update(mapOf("lastMessage" to preview, "lastMessageTime" to now, "lastSenderId" to sender.uid)).await()
            bumpUnread(chatId, sender.uid)
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteMessage(chatId: String, msgId: String) {
        db.collection("chats").document(chatId)
            .collection("messages").document(msgId).update("isDeleted", true).await()
    }

    // Настоящее редактирование текста (раньше такой функции не было вообще —
    // "редактировать" ничего не делало). Работает только для текстовых сообщений,
    // и только правки автора — это также enforced в firestore.rules.
    suspend fun editMessage(chatId: String, msgId: String, newText: String): Result<Unit> {
        return try {
            db.collection("chats").document(chatId).collection("messages").document(msgId)
                .update(mapOf("text" to newText, "isEdited" to true)).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    // ══════════════════════════════════════════
    // ЗВОНКИ
    // Сигналинг (offer/answer/ICE) идёт через Firestore — тот же Spark-план, что и
    // для остального проекта, никакого Storage/доп.тарифа для этого не нужно.
    // Сама передача голоса/видео — WebRTC напрямую между устройствами (см. CallManager).
    // ══════════════════════════════════════════

    suspend fun startCall(
        callerId: String, callerName: String, callerPhotoUrl: String, callerPhotoData: String,
        receiverId: String, type: CallType
    ): String {
        val call = Call(
            callerId = callerId, callerName = callerName,
            callerPhotoUrl = callerPhotoUrl, callerPhotoData = callerPhotoData,
            receiverId = receiverId, type = type, status = CallStatus.RINGING,
            startedAt = Timestamp.now()
        )
        return db.collection("calls").add(call).await().id
    }

    // Входящие звонки, адресованные мне и ещё не отвеченные. Два equality-фильтра на
    // разных полях Firestore умеет без составного индекса (он нужен только когда к
    // equality добавляется range/orderBy по другому полю).
    fun listenIncomingCalls(myUid: String): Flow<Call?> = callbackFlow {
        val listener = db.collection("calls")
            .whereEqualTo("receiverId", myUid)
            .whereEqualTo("status", CallStatus.RINGING.name)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(Call::class.java)?.firstOrNull())
            }
        awaitClose { listener.remove() }
    }

    fun observeCall(callId: String): Flow<Call?> = callbackFlow {
        val listener = db.collection("calls").document(callId)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObject(Call::class.java))
            }
        awaitClose { listener.remove() }
    }

    suspend fun setOffer(callId: String, sdp: String) {
        db.collection("calls").document(callId).update("offerSdp", sdp).await()
    }

    suspend fun setAnswer(callId: String, sdp: String) {
        db.collection("calls").document(callId)
            .update(mapOf("answerSdp" to sdp, "status" to CallStatus.ACTIVE.name)).await()
    }

    suspend fun declineCall(callId: String) {
        try {
            db.collection("calls").document(callId)
                .update(mapOf("status" to CallStatus.DECLINED.name, "endedAt" to Timestamp.now())).await()
        } catch (_: Exception) {}
    }

    suspend fun endCall(callId: String, finalStatus: CallStatus = CallStatus.ENDED) {
        try {
            db.collection("calls").document(callId)
                .update(mapOf("status" to finalStatus.name, "endedAt" to Timestamp.now())).await()
        } catch (_: Exception) {}
    }

    // Кандидаты — отдельные подколлекции на каждую сторону, чтобы не перезаписывать
    // весь список при каждом новом кандидате и не терять уже отправленные.
    suspend fun addIceCandidate(callId: String, fromCaller: Boolean, candidate: IceCandidateData) {
        val sub = if (fromCaller) "callerCandidates" else "calleeCandidates"
        try { db.collection("calls").document(callId).collection(sub).add(candidate).await() } catch (_: Exception) {}
    }

    // Каждая сторона слушает кандидатов ДРУГОЙ стороны; отдаём только новые
    // (documentChanges ADDED), чтобы не пытаться добавить один и тот же кандидат дважды.
    fun listenIceCandidates(callId: String, fromCaller: Boolean): Flow<List<IceCandidateData>> = callbackFlow {
        val sub = if (fromCaller) "callerCandidates" else "calleeCandidates"
        val listener = db.collection("calls").document(callId).collection(sub)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val added = snap?.documentChanges
                    ?.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                    ?.mapNotNull { it.document.toObject(IceCandidateData::class.java) }
                    ?: emptyList()
                if (added.isNotEmpty()) trySend(added)
            }
        awaitClose { listener.remove() }
    }

    // ══════════════════════════════════════════
    // ИСТОРИИ (Stories) — живут 24 часа
    // ══════════════════════════════════════════

    // Забираем все ещё не истёкшие истории (глобально по коллекции — она маленькая,
    // whereIn по contactUids упёрся бы в лимит Firestore в 10 значений). Группировку по
    // автору и фильтр "только контакты + я" делает вызывающий код (в HomeScreen), т.к.
    // там уже есть список чатов, из которого ясно, кто "контакт".
    fun getActiveStories(): Flow<List<Story>> = callbackFlow {
        val listener = db.collection("stories")
            .whereGreaterThan("expiresAt", Timestamp.now())
            .orderBy("expiresAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toObjects(Story::class.java) ?: emptyList())
            }
        awaitClose { listener.remove() }
    }

    // Раньше историю всегда паковали в Base64 прямо в документ Firestore с бюджетом
    // 600 КБ (см. StoriesBar.launchPostFlow) — этого мало: обычное фото с телефона даже
    // после сжатия до 1080px и минимального JPEG-качества сплошь и рядом не влезает в
    // 600 КБ (в отличие от аватарки/фона чата, где Cloudinary даёт бюджет 1.5-2.5 МБ), и
    // ImageCompress.compressToBytes честно возвращает null, если картинка не влезла даже
    // на минимальном качестве — то есть "истории не работают" было не багом отправки, а
    // тем, что почти любое настоящее фото отклонялось на этапе сжатия ещё до Firestore.
    // Теперь postStory использует тот же приём, что uploadAvatar/uploadChatBackground:
    // если Cloudinary настроен — грузим туда с тем же щедрым бюджетом, что и остальные
    // фото, и в документ истории кладём просто ссылку (mediaUrl); иначе — прежний
    // Base64-в-Firestore путь как надёжный запасной вариант.
    suspend fun postStory(context: Context, sender: User, uri: Uri): Result<Unit> {
        val now = Timestamp.now()
        val expires = Timestamp(now.seconds + TimeUnit.HOURS.toSeconds(24), now.nanoseconds)
        if (CloudinaryRepository.isConfigured) {
            val bytes = ImageCompress.compressToBytes(context, uri, maxDim = 1080, targetBytes = 2_000_000)
                ?: return Result.failure(Exception("Не удалось обработать фото"))
            val urlResult = CloudinaryRepository.uploadImage(bytes, "story_${sender.uid}_${now.seconds}.jpg")
            val url = urlResult.getOrNull()
            if (url != null) {
                return try {
                    db.collection("stories").add(
                        Story(userId = sender.uid, userName = sender.displayName,
                            userPhotoUrl = sender.photoUrl, userPhotoData = sender.photoData,
                            mediaUrl = url, createdAt = now, expiresAt = expires)
                    ).await()
                    Result.success(Unit)
                } catch (e: Exception) { Result.failure(e) }
            }
            Log.w("FirebaseRepo", "Cloudinary story upload failed, falling back to Base64: ${urlResult.exceptionOrNull()?.message}")
        }
        return try {
            val base64 = ImageCompress.compressToBase64(context, uri, maxDim = 1080, targetBytes = 600_000)
                ?: return Result.failure(Exception("Не удалось обработать фото"))
            db.collection("stories").add(
                Story(userId = sender.uid, userName = sender.displayName,
                    userPhotoUrl = sender.photoUrl, userPhotoData = sender.photoData,
                    mediaData = base64, createdAt = now, expiresAt = expires)
            ).await()
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun markStoryViewed(storyId: String, viewerId: String) {
        try { db.collection("stories").document(storyId).update("viewedBy", FieldValue.arrayUnion(viewerId)).await() }
        catch (_: Exception) {}
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        return try { db.collection("stories").document(storyId).delete().await(); Result.success(Unit) }
        catch (e: Exception) { Result.failure(e) }
    }
}
