package com.rosechat.shared.data

import android.app.Application
import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import java.io.File
import java.util.Properties

// Конфиг того же проекта pingchat-c68f2, что и у Android-клиента (значения — из
// app/google-services.json). apiKey/projectId одни на весь проект, отдельного
// "Web"-приложения в консоли Firebase для десктопа пока не заводили — если позже
// заведёшь (Project settings → Add app → Web, это 30 секунд и бесплатно), просто
// замени APPLICATION_ID на выданный оттуда "appId": так будет чище, но и текущий
// вариант с Android-идентификатором должен нормально работать для Auth/Firestore.
private const val FIREBASE_PROJECT_ID = "pingchat-c68f2"
private const val FIREBASE_APPLICATION_ID = "1:861929985523:android:15ca907a0e6ec18f5c6e86"
private const val FIREBASE_API_KEY = "AIzaSyC0MG4zYAN5w44DgHXvnqNWgNsKDZO15B4"

private var initialized = false

actual fun ensureFirebaseInitialized() {
    if (initialized) return
    initialized = true

    FirebasePlatform.initializeFirebasePlatform(DiskPersistedFirebasePlatform())
    Firebase.initialize(
        Application(),
        FirebaseOptions(
            applicationId = FIREBASE_APPLICATION_ID,
            apiKey = FIREBASE_API_KEY,
            projectId = FIREBASE_PROJECT_ID,
        ),
    )
}

// firebase-java-sdk (используется под капотом GitLive-SDK на JVM) использует этот
// интерфейс, чтобы сохранять сессию входа между запусками — как раз то, что нужно
// для нормального десктопного клиента (не логиниться заново при каждом старте).
// Файл лежит в %USERPROFILE%\.pinggram\firebase-kv.properties.
private class DiskPersistedFirebasePlatform : FirebasePlatform() {
    private val file: File = File(File(System.getProperty("user.home"), ".pinggram"), "firebase-kv.properties").apply {
        parentFile?.mkdirs()
    }
    private val props: Properties = Properties().apply {
        if (file.exists()) {
            runCatching { file.inputStream().use { load(it) } }
        }
    }

    private fun persist() {
        runCatching { file.outputStream().use { props.store(it, null) } }
    }

    override fun store(key: String, value: String) {
        props.setProperty(key, value)
        persist()
    }

    override fun retrieve(key: String): String? = props.getProperty(key)

    override fun clear(key: String) {
        props.remove(key)
        persist()
    }

    override fun log(msg: String) {
        println("[Firebase] $msg")
    }
}
