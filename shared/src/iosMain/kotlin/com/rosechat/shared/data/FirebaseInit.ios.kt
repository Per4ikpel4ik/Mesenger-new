package com.rosechat.shared.data

// На iPhone Firebase настраивается нативно: FirebaseApp.configure() вызывается из Swift-кода
// (iosApp) при запуске приложения, до любого кода :shared. Здесь делать нечего.
actual fun ensureFirebaseInitialized() {
}
