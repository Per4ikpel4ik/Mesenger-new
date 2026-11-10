package com.rosechat.shared.data

import com.sun.net.httpserver.HttpServer
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

// Вставь сюда Client ID и Client secret из Google Cloud Console → APIs & Services →
// Credentials → твой OAuth client ID ТИПА "Desktop app" (см. инструкцию в чате).
// Это не тот же самый ключ, что apiKey у Firebase — отдельная сущность именно для
// входа через Google-аккаунт. ВАЖНО: тот же самый CLIENT_ID нужно вписать в
// api/desktopGoogleSignIn.js (файл для Vercel) — значения в обоих местах обязаны
// совпадать.
private const val GOOGLE_DESKTOP_CLIENT_ID = "861929985523-ieqhkn3cibinka4070983kufgkfl2hb6.apps.googleusercontent.com"
// Секрет не хранится в репозитории: впиши GOOGLE_DESKTOP_CLIENT_SECRET=... в local.properties (он в .gitignore).
private val GOOGLE_DESKTOP_CLIENT_SECRET: String get() = localSetting("GOOGLE_DESKTOP_CLIENT_SECRET")
// Адрес твоей функции на Vercel (НЕ Firebase — Blaze для этого не нужен).
// Впиши сюда точный URL, который покажет Vercel после деплоя, например:
// https://pinggram-desktop-auth.vercel.app/api/desktopGoogleSignIn
private const val EXCHANGE_FUNCTION_URL = "https://pinggram-auth.vercel.app/api/desktopGoogleSignIn"

@Serializable
private data class GoogleTokenResponse(
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

@Serializable
private data class ExchangeResponse(
    @SerialName("customToken") val customToken: String? = null,
    @SerialName("error") val error: String? = null,
)

actual suspend fun googleSignIn(): Result<String> = try {
    if (GOOGLE_DESKTOP_CLIENT_ID.startsWith("ВСТАВЬ")) {
        Result.failure(Exception("Сначала впиши GOOGLE_DESKTOP_CLIENT_ID / SECRET в GoogleSignIn.desktop.kt"))
    } else {
        val verifier = randomUrlSafe(64)
        val challenge = base64UrlSafe(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val state = randomUrlSafe(24)

        // Порт 0 = ОС сама выдаст свободный порт. Для клиентов типа "Desktop app"
        // Google не требует заранее регистрировать конкретный порт — только сам
        // адрес http://127.0.0.1, так что это безопасно и всегда работает.
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        val redirectUri = "http://127.0.0.1:$port"
        val codeResult = CompletableDeferred<String>()

        server.createContext("/") { exchange ->
            val query = exchange.requestURI.query.orEmpty()
            val params = query.split("&").filter { it.isNotBlank() }.associate { pair ->
                val idx = pair.indexOf('=')
                if (idx < 0) pair to "" else pair.substring(0, idx) to URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            }
            val html = if (params["code"] != null && params["state"] == state) {
                "<html><body style='font-family:sans-serif;padding:40px'><h2>Готово! Можно закрыть эту вкладку и вернуться в PingGram.</h2></body></html>"
            } else {
                "<html><body style='font-family:sans-serif;padding:40px'><h2>Не получилось войти. Вернись в PingGram и попробуй ещё раз.</h2></body></html>"
            }
            val bytes = html.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }

            when {
                params["code"] != null && params["state"] == state -> codeResult.complete(params.getValue("code"))
                params["error"] != null -> codeResult.completeExceptionally(Exception("Google: ${params["error"]}"))
                else -> codeResult.completeExceptionally(Exception("Некорректный ответ от Google (state не совпал)"))
            }
        }
        server.start()

        val authUrl = buildString {
            append("https://accounts.google.com/o/oauth2/v2/auth")
            append("?client_id=").append(enc(GOOGLE_DESKTOP_CLIENT_ID))
            append("&redirect_uri=").append(enc(redirectUri))
            append("&response_type=code")
            append("&scope=").append(enc("openid email profile"))
            append("&code_challenge=").append(enc(challenge))
            append("&code_challenge_method=S256")
            append("&state=").append(enc(state))
            append("&prompt=select_account")
        }
        Desktop.getDesktop().browse(URI(authUrl))

        val code = try {
            withTimeout(180_000) { codeResult.await() }
        } finally {
            server.stop(0)
        }

        val form = buildString {
            append("code=").append(enc(code))
            append("&client_id=").append(enc(GOOGLE_DESKTOP_CLIENT_ID))
            append("&client_secret=").append(enc(GOOGLE_DESKTOP_CLIENT_SECRET))
            append("&redirect_uri=").append(enc(redirectUri))
            append("&grant_type=authorization_code")
            append("&code_verifier=").append(enc(verifier))
        }
        val httpClient = HttpClient.newHttpClient()
        val tokenRequest = HttpRequest.newBuilder()
            .uri(URI("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString())
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<GoogleTokenResponse>(tokenResponse.body())

        if (parsed.idToken == null) {
            Result.failure(Exception(parsed.errorDescription ?: parsed.error ?: "Google не вернул id_token"))
        } else {
            // ВАЖНО: GitLive Firebase SDK на JVM/Desktop (firebase-java-sdk) официально
            // реализует только маленький список Auth-методов — createUserWithEmailAndPassword,
            // signInAnonymously, signInWithCustomToken, signInWithEmailAndPassword, signOut и
            // немного по мелочи. signInWithCredential (вход по готовому Google id_token) в
            // этот список НЕ входит и просто бросает NotImplementedError — это и есть та самая
            // ошибка "An operation is not implemented", это не про старый/новый файл, это
            // фундаментальное ограничение библиотеки. Поэтому здесь id_token отправляется на
            // свою функцию на Vercel (не на Firebase — Blaze не нужен), которая проверяет его
            // подлинность через Google и в ответ выдаёт Firebase custom token — а
            // signInWithCustomToken на Desktop уже реально работает.
            val exchangeBody = "{\"idToken\":\"${parsed.idToken.replace("\\", "\\\\").replace("\"", "\\\"")}\"}"
            val exchangeRequest = HttpRequest.newBuilder()
                .uri(URI(EXCHANGE_FUNCTION_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(exchangeBody))
                .build()
            val exchangeResponse = httpClient.send(exchangeRequest, HttpResponse.BodyHandlers.ofString())
            val exchanged = try {
                Json { ignoreUnknownKeys = true }.decodeFromString<ExchangeResponse>(exchangeResponse.body())
            } catch (e: Exception) {
                ExchangeResponse(error = "Сервер вернул неожиданный ответ (код ${exchangeResponse.statusCode()}): ${exchangeResponse.body().take(200)}")
            }

            if (exchanged.customToken == null) {
                Result.failure(Exception(exchanged.error ?: "Сервер не вернул customToken"))
            } else {
                // Известный баг firebase-java-sdk (GitLiveApp/firebase-java-sdk issue #60):
                // внутри signInWithCustomToken библиотека на JVM/Desktop пытается прочитать
                // результат Task'а СИНХРОННО, до того как его внутренняя continueWith-цепочка
                // (updateByGetAccountInfo) реально завершилась — либо бросает
                // "IllegalStateException: Task is not yet complete", либо (это и был твой
                // "спиннер крутится вечно") просто зависает, ожидая callback, который эта
                // сломанная цепочка так и не вызывает. При этом сама авторизация и
                // обновление локальной сессии внутри Firebase проходят независимо и обычно
                // всё же завершаются на фоне. Поэтому: (1) жёстко ограничиваем сам вызов
                // таймаутом, чтобы кнопка не крутилась бесконечно, и (2) если он не вернул
                // uid — упал с ошибкой, вернул null или не уложился в таймаут — даём Firebase
                // немного времени и несколько раз перепроверяем currentUser: если сессия
                // внутри всё же обновилась, засчитываем вход как успешный, несмотря на
                // сломанный результат самого вызова.
                val uidFromCall = try {
                    withTimeout(20_000) { Firebase.auth.signInWithCustomToken(exchanged.customToken).user?.uid }
                } catch (e: Exception) {
                    // Не глотаем ошибку молча — печатаем в консоль (видно в Run-панели
                    // Android Studio), чтобы можно было увидеть настоящую причину, даже
                    // если она затем "прикроется" успешной проверкой currentUser ниже.
                    println("[GoogleSignIn] signInWithCustomToken выбросил: ${e::class.simpleName}: ${e.message}")
                    null
                }
                val uid = uidFromCall ?: run {
                    var found: String? = null
                    repeat(6) {
                        if (found == null) {
                            delay(500)
                            found = Firebase.auth.currentUser?.uid
                        }
                    }
                    found
                }
                if (uid == null) {
                    Result.failure(Exception("Firebase не подтвердил вход (похоже на известный баг signInWithCustomToken в firebase-java-sdk). Попробуй ещё раз — обычно срабатывает со второго раза."))
                } else {
                    Result.success(uid)
                }
            }
        }
    }
} catch (e: Exception) {
    // Печатаем полный стектрейс в консоль (Run-панель Android Studio) — по нему
    // видно настоящую причину, даже когда у e.message пусто. И подстраховываем
    // сообщение для экрана: если у исключения нет текста, показываем хотя бы имя
    // класса ошибки вместо голого "Не удалось войти через Google" без деталей.
    e.printStackTrace()
    val shownMessage = e.message ?: "Внутренняя ошибка: ${e::class.simpleName ?: "неизвестно"} (смотри консоль Android Studio)"
    Result.failure(Exception(shownMessage, e))
}

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

private fun randomUrlSafe(byteLength: Int): String {
    val bytes = ByteArray(byteLength)
    SecureRandom().nextBytes(bytes)
    return base64UrlSafe(bytes)
}

private fun base64UrlSafe(bytes: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
