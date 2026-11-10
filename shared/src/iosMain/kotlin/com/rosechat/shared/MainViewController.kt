package com.rosechat.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.rosechat.shared.data.ensureFirebaseInitialized

// Точка входа на iPhone — аналог main() из Main.kt на десктопе.
// Firebase на iOS настраивается в Swift (FirebaseApp.configure() в iOSApp.swift).
fun MainViewController() = ComposeUIViewController {
    ensureFirebaseInitialized()
    App()
}
