const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { setGlobalOptions } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();
const messaging = getMessaging();

// Регион можно поменять на ближайший к пользователям, например "europe-west1".
setGlobalOptions({ region: "us-central1", maxInstances: 10 });

exports.sendMessageNotification = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    if (!message) return;

    const chatId = event.params.chatId;
    const chatRef = db.collection("chats").doc(chatId);
    const chatSnap = await chatRef.get();
    const chat = chatSnap.data();
    if (!chat) return;

    // Получатели — все участники чата, кроме отправителя. Для канала с
    // broadcastOnly это те, кто подписан (memberIds уже содержит только их).
    const recipients = (chat.memberIds || []).filter((uid) => uid !== message.senderId);
    if (recipients.length === 0) return;

    // Счётчик непрочитанных unreadCount теперь увеличивает клиент отправителя
    // (FirebaseRepo.bumpUnread), поэтому здесь его НЕ трогаем — иначе будет двойной счёт.

    const userRefs = recipients.map((uid) => db.collection("users").doc(uid));
    const userSnaps = await db.getAll(...userRefs);

    const preview = previewFor(message);
    const chatName = chat.name && chat.name.length > 0 ? chat.name : (message.senderName || "PingGram");
    const text = message.senderName ? `${message.senderName}: ${preview}` : preview;

    // Счётчик на иконке приложения — у каждого получателя своё общее число
    // непрочитанных по всем чатам, поэтому шлём персонально, а не одним multicast
    // с одинаковым data (так, как раньше, разный unreadCount не передать).
    const sends = [];
    for (const snap of userSnaps) {
      const token = snap.data()?.fcmToken;
      if (typeof token !== "string" || token.length === 0) continue;
      sends.push(
        totalUnreadFor(snap.id).then((totalUnread) =>
          messaging.send({
            token,
            data: { chatId, chatName, text: text.slice(0, 200), unreadCount: String(totalUnread) },
            android: { priority: "high" },
          })
        ).then(() => ({ token, success: true })).catch((error) => ({ token, success: false, error }))
      );
    }
    if (sends.length === 0) return;

    const results = await Promise.all(sends);
    await cleanupInvalidTokens(userSnaps, results);
  }
);

// Сумма unreadCount[uid] по всем чатам, где uid состоит — общее число для бейджа
// на иконке приложения (setNumber в FcmService.kt), а не только по этому чату.
async function totalUnreadFor(uid) {
  const snap = await db.collection("chats").where("memberIds", "array-contains", uid).get();
  let total = 0;
  snap.forEach((d) => { total += (d.data().unreadCount?.[uid] || 0); });
  return total;
}

function previewFor(message) {
  switch (message.type) {
    case "IMAGE": return "🖼 Фото";
    case "VOICE": return `🎤 Голосовое ${message.duration ? message.duration + "с" : ""}`.trim();
    case "VIDEO_NOTE": return "⭕ Видео";
    case "FILE": return `📎 ${message.fileName || "Файл"}`;
    case "STICKER": return message.sticker || "Стикер";
    default: return message.text || "Новое сообщение";
  }
}

// FCM-токен может протухнуть (переустановка приложения, смена устройства и т.п.).
// Чистим такие токены из профиля, чтобы они не копились и не тормозили рассылку.
async function cleanupInvalidTokens(userSnaps, results) {
  const invalidTokens = new Set();
  results.forEach((r) => {
    if (!r.success) {
      const code = r.error?.code || "";
      if (code.includes("registration-token-not-registered") || code.includes("invalid-argument")) {
        invalidTokens.add(r.token);
      }
    }
  });
  if (invalidTokens.size === 0) return;

  const batch = db.batch();
  userSnaps.forEach((snap) => {
    if (invalidTokens.has(snap.data()?.fcmToken)) {
      batch.update(snap.ref, { fcmToken: "" });
    }
  });
  await batch.commit();
}

// Пуш о новом звонке — расширение той же схемы, что и sendMessageNotification.
// Никакого нового тарифа это не требует: Functions уже на Blaze (иначе не работали
// бы существующие пуши сообщений), это просто ещё один триггер на том же проекте.
exports.sendCallNotification = onDocumentCreated(
  "calls/{callId}",
  async (event) => {
    const call = event.data?.data();
    if (!call || !call.receiverId) return;

    const receiverSnap = await db.collection("users").doc(call.receiverId).get();
    const token = receiverSnap.data()?.fcmToken;
    if (!token) return;

    await messaging.send({
      token,
      data: {
        type: "call",
        callId: event.params.callId,
        callerName: call.callerName || "PingGram",
        isVideo: String(call.type === "VIDEO"),
      },
      // TTL 30 с: если телефон был без сети, "входящий звонок" не должен прийти
      // через несколько минут, когда звонок давно закончен.
      android: { priority: "high", ttl: 30000 },
    }).catch(() => {}); // протухший токен здесь не критично чистить — звонок и так пропущен
  }
);

// Клиент и так не показывает истёкшие истории (фильтр expiresAt > now в запросе),
// но сами документы без этого копились бы в Firestore вечно. Раз в час подчищаем —
// требует Blaze-плана (Cloud Scheduler), но Functions уже на нём, раз пуши работают.
exports.cleanupExpiredStories = onSchedule("every 60 minutes", async () => {
  const expired = await db.collection("stories")
    .where("expiresAt", "<=", new Date())
    .limit(400)  // с запасом; батч Firestore ограничен 500 операциями
    .get();
  if (expired.empty) return;

  const batch = db.batch();
  expired.docs.forEach((doc) => batch.delete(doc.ref));
  await batch.commit();
});

// Раз в час гасим Premium, выданный временно через код "gg" (см. FirebaseRepo.activatePremium
// в приложении — там же ставится premiumUntil). Бессрочный Premium, выданный вручную из
// Dev Panel, premiumUntil не имеет — его этот таймер не трогает.
exports.cleanupExpiredPremium = onSchedule("every 60 minutes", async () => {
  const expired = await db.collection("users")
    .where("premiumUntil", "<=", new Date())
    .limit(400)
    .get();
  if (expired.empty) return;

  const batch = db.batch();
  expired.docs.forEach((doc) => {
    batch.update(doc.ref, { isPremium: false, premiumUntil: FieldValue.delete() });
  });
  await batch.commit();
});
