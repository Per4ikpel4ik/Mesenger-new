@file:OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)

package com.rosechat.shared.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.CoreCrypto.CC_SHA256
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSURLSession
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// ─────────────────────────────────────────────────────────────────────────────────────────
// Вход через Google на iPhone: системное окно Safari (ASWebAuthenticationSession) + PKCE,
// без сторонних SDK. Дальше id_token отдаём Firebase напрямую (на iOS это нативный Firebase
// SDK, там signInWithCredential работает — в отличие от десктопа, где нужен Vercel).
//
// ЧТО НУЖНО СДЕЛАТЬ ОДИН РАЗ:
//   Открой GoogleService-Info.plist (из Firebase Console → iOS-приложение) и скопируй
//   значение ключа CLIENT_ID (вид: 861929985523-xxxxxxxx.apps.googleusercontent.com)
//   в константу ниже. Это публичный идентификатор, не секрет. Нужен именно тот CLIENT_ID,
//   который Firebase создал для iOS-приложения, — тогда Firebase примет токен.
// ─────────────────────────────────────────────────────────────────────────────────────────
private const val IOS_GOOGLE_CLIENT_ID = "861929985523-trk618kjaac5n9vgr42p7d3qk34vn6ap.apps.googleusercontent.com"

@Serializable
private data class IosGoogleTokenResponse(
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

private class GoogleAnchorProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        UIApplication.sharedApplication.keyWindow ?: UIWindow()
}

// Пока окно входа открыто, держим сессию и провайдера сами (UIKit хранит их слабой ссылкой).
private var activeAuthSession: Any? = null

private fun gNsDataToBytes(data: NSData): ByteArray {
    val size = data.length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return out
}

private fun gBytesToNsData(bytes: ByteArray): NSData {
    if (bytes.isEmpty()) return NSData()
    return bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
}

private fun formEncode(s: String): String {
    val hex = "0123456789ABCDEF"
    val sb = StringBuilder()
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if ((ch in 'A'..'Z') || (ch in 'a'..'z') || (ch in '0'..'9') || ch == '-' || ch == '.' || ch == '_' || ch == '~') {
            sb.append(ch)
        } else {
            sb.append('%')
            sb.append(hex[c shr 4])
            sb.append(hex[c and 0x0F])
        }
    }
    return sb.toString()
}

private fun sha256(input: ByteArray): ByteArray {
    val out = ByteArray(32)
    input.usePinned { inp ->
        out.usePinned { o ->
            CC_SHA256(inp.addressOf(0), input.size.toUInt(), o.addressOf(0).reinterpret<UByteVar>())
        }
    }
    return out
}

private fun base64Url(bytes: ByteArray): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)

// 64 символа [0-9A-F] — допустимая длина и алфавит для PKCE code_verifier.
private fun randomToken(): String = (NSUUID().UUIDString + NSUUID().UUIDString).replace("-", "")

// Открывает окно входа и возвращает code (или ошибку).
private suspend fun requestAuthCode(authUrl: NSURL, callbackScheme: String, expectedState: String): Result<String> =
    suspendCancellableCoroutine { cont ->
        val provider = GoogleAnchorProvider()
        val session = ASWebAuthenticationSession(
            authUrl,
            callbackScheme,
            completionHandler = { callbackUrl, error ->
                activeAuthSession = null
                if (callbackUrl == null) {
                    val cancelled = error?.code == 1L // 1 = человек закрыл окно
                    cont.resume(
                        Result.failure(
                            Exception(if (cancelled) "Вход отменён" else (error?.localizedDescription ?: "Не удалось открыть вход Google"))
                        )
                    )
                } else {
                    val items = NSURLComponents.componentsWithURL(callbackUrl, false)?.queryItems
                        ?.filterIsInstance<NSURLQueryItem>().orEmpty()
                    val params = items.associate { it.name to (it.value ?: "") }
                    val code = params["code"]
                    when {
                        params["error"] != null -> cont.resume(Result.failure(Exception("Google: ${params["error"]}")))
                        code.isNullOrBlank() || params["state"] != expectedState ->
                            cont.resume(Result.failure(Exception("Некорректный ответ от Google (state не совпал)")))
                        else -> cont.resume(Result.success(code))
                    }
                }
            },
        )
        session.presentationContextProvider = provider
        activeAuthSession = listOf(session, provider)
        cont.invokeOnCancellation { session.cancel() }
        if (!session.start()) {
            activeAuthSession = null
            cont.resume(Result.failure(Exception("Не удалось открыть окно входа Google")))
        }
    }

private suspend fun postForm(url: String, form: String): Result<String> =
    suspendCancellableCoroutine { cont ->
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            cont.resume(Result.failure(IllegalArgumentException("Некорректный адрес")))
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest.requestWithURL(nsUrl)
        request.HTTPMethod = "POST"
        request.setTimeoutInterval(30.0)
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField = "Content-Type")
        request.HTTPBody = gBytesToNsData(form.encodeToByteArray())
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
            val text = data?.let { gNsDataToBytes(it) }?.decodeToString() ?: ""
            if (error != null && status == null) {
                cont.resume(Result.failure(Exception("Нет соединения: ${error.localizedDescription}")))
            } else {
                cont.resume(Result.success(text))
            }
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }

actual suspend fun googleSignIn(): Result<String> {
    if (IOS_GOOGLE_CLIENT_ID.startsWith("ВСТАВЬ")) {
        return Result.failure(Exception("Впиши CLIENT_ID из GoogleService-Info.plist в GoogleSignIn.ios.kt"))
    }
    return try {
        val scheme = "com.googleusercontent.apps." + IOS_GOOGLE_CLIENT_ID.removeSuffix(".apps.googleusercontent.com")
        val redirectUri = "$scheme:/oauth2redirect"
        val verifier = randomToken()
        val challenge = base64Url(sha256(verifier.encodeToByteArray()))
        val state = randomToken().take(32)

        val authUrlString = buildString {
            append("https://accounts.google.com/o/oauth2/v2/auth")
            append("?client_id=").append(formEncode(IOS_GOOGLE_CLIENT_ID))
            append("&redirect_uri=").append(formEncode(redirectUri))
            append("&response_type=code")
            append("&scope=").append(formEncode("openid email profile"))
            append("&code_challenge=").append(formEncode(challenge))
            append("&code_challenge_method=S256")
            append("&state=").append(formEncode(state))
            append("&prompt=select_account")
        }
        val authUrl = NSURL.URLWithString(authUrlString)
            ?: return Result.failure(Exception("Не удалось собрать адрес входа"))

        val code = requestAuthCode(authUrl, scheme, state).getOrElse { return Result.failure(it) }

        val form = buildString {
            append("code=").append(formEncode(code))
            append("&client_id=").append(formEncode(IOS_GOOGLE_CLIENT_ID))
            append("&redirect_uri=").append(formEncode(redirectUri))
            append("&grant_type=authorization_code")
            append("&code_verifier=").append(formEncode(verifier))
        }
        val body = postForm("https://oauth2.googleapis.com/token", form).getOrElse { return Result.failure(it) }
        val parsed = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<IosGoogleTokenResponse>(body)
        } catch (e: Exception) {
            IosGoogleTokenResponse(error = "Неожиданный ответ Google: ${body.take(200)}")
        }
        val idToken = parsed.idToken
            ?: return Result.failure(Exception(parsed.errorDescription ?: parsed.error ?: "Google не вернул id_token"))

        val credential = GoogleAuthProvider.credential(idToken, parsed.accessToken)
        val uid = Firebase.auth.signInWithCredential(credential).user?.uid
        if (uid == null) Result.failure(Exception("Firebase не подтвердил вход")) else Result.success(uid)
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Внутренняя ошибка: ${e::class.simpleName}", e))
    }
}
