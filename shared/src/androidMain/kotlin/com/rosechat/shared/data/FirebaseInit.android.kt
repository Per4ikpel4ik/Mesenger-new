package com.rosechat.shared.data

// :shared пока не подключён к :app (это будущий Этап 2 — сейчас Android-приложение
// продолжает работать на своём родном com.rosechat.data.repository.FirebaseRepo).
// Если/когда :app начнёт использовать SharedRepo напрямую, Firebase на Android уже
// будет проинициализирован автоматически плагином google-services — здесь делать
// ничего не нужно.
actual fun ensureFirebaseInitialized() {}
