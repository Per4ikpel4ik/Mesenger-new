package com.rosechat.shared.data

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Тот же apiKey проекта pingchat-c68f2, что и в FirebaseInit.desktop.kt (это публичный ключ
// клиента, не секрет — он и так лежит в каждом APK в google-services.json).
private const val RESET_API_KEY = "AIzaSyC0MG4zYAN5w44DgHXvnqNWgNsKDZO15B4"

private val resetHttp: HttpClient by lazy {
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
}

private fun jsonEscape(s: String): String = buildString {
    for (ch in s) when (ch) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> append(ch)
    }
}

actual suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val body = "{\"requestType\":\"PASSWORD_RESET\",\"email\":\"${jsonEscape(email.trim())}\"}"
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$RESET_API_KEY"))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = resetHttp.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() in 200..299) {
            Result.success(Unit)
        } else {
            val text = response.body()
            val message = when {
                text.contains("EMAIL_NOT_FOUND") -> "Аккаунт с таким email не найден"
                text.contains("INVALID_EMAIL") -> "Некорректный email"
                text.contains("TOO_MANY_ATTEMPTS") || text.contains("QUOTA_EXCEEDED") -> "Слишком много попыток, попробуйте позже"
                else -> "Не удалось отправить письмо (код ${response.statusCode()})"
            }
            Result.failure(Exception(message))
        }
    } catch (e: Exception) {
        Result.failure(Exception("Нет соединения с сервером: ${e.message ?: "ошибка сети"}"))
    }
}
