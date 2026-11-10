package com.rosechat.shared.data

import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

// GIF через Klipy (docs.klipy.com) — тот же сервис и тот же разбор ответа, что GifRepository
// на Android. Запросы идут через общий fetchBytes (MediaPlatform), поэтому отдельной
// платформенной реализации сети не нужно.
data class GifResult(val id: String, val previewUrl: String, val fullUrl: String)

object GifRepository {
    private const val BASE = "https://api.klipy.com/api/v1"
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    // Анонимный id устройства для Klipy (без персональных данных) — как customerId() на Android.
    private val customerId: String by lazy {
        "pinggram-" + (1..12).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")
    }

    val isConfigured: Boolean get() = gifApiKey().isNotBlank()

    suspend fun trending(page: Int = 1): Result<List<GifResult>> =
        request("$BASE/${gifApiKey()}/gifs/trending?page=$page&per_page=24&customer_id=$customerId")

    suspend fun search(query: String, page: Int = 1): Result<List<GifResult>> =
        request("$BASE/${gifApiKey()}/gifs/search?q=${urlEncode(query)}&page=$page&per_page=24&customer_id=$customerId")

    private suspend fun request(url: String): Result<List<GifResult>> {
        if (!isConfigured) return Result.failure(IllegalStateException("GIF_API_KEY не найден"))
        val bytes = fetchBytes(url).getOrElse { return Result.failure(it) }
        val root = try {
            Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        } catch (e: Exception) {
            return Result.failure(Exception("Klipy: непонятный ответ сервера"))
        }
        val ok = (root["result"] as? JsonPrimitive)?.booleanOrNull ?: true
        if (!ok) {
            val msg = root["message"].str() ?: root["error"].str() ?: "result=false"
            return Result.failure(Exception("Klipy: $msg"))
        }
        val items: JsonArray = ((root["data"] as? JsonObject)?.get("data") as? JsonArray)
            ?: (root["data"] as? JsonArray)
            ?: JsonArray(emptyList())

        val out = ArrayList<GifResult>()
        items.forEachIndexed { i, el ->
            val obj = (el as? JsonObject) ?: return@forEachIndexed
            // Индекс в конце — гарантия уникального key в сетке (Klipy может прислать один slug дважды).
            val id = (obj["id"].str() ?: obj["slug"].str() ?: "gif") + "_$i"
            val acc = LinkedHashSet<String>()
            collectUrls(obj, acc)
            if (acc.isEmpty()) return@forEachIndexed
            val list = acc.toList()
            val full = list.firstOrNull { it.endsWith(".gif", true) }
                ?: list.firstOrNull { it.endsWith(".webp", true) }
                ?: list.first()
            val markers = listOf("/sm", "_sm", "/xs", "_xs", "thumb", "preview", "tiny", "nano")
            val preview = list.firstOrNull { u ->
                (u.endsWith(".gif", true) || u.endsWith(".webp", true)) && markers.any { u.contains(it, true) }
            } ?: full
            out.add(GifResult(id = id, previewUrl = preview, fullUrl = full))
        }
        return Result.success(out)
    }

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull

    // Все http(s)-ссылки на картинки/видео из произвольно вложенного JSON (как collectUrls на Android).
    private fun collectUrls(node: JsonElement?, acc: MutableSet<String>) {
        when (node) {
            is JsonObject -> node.values.forEach { collectUrls(it, acc) }
            is JsonArray -> node.forEach { collectUrls(it, acc) }
            is JsonPrimitive -> if (node.isString) {
                val s = node.content
                if (s.startsWith("http", true) &&
                    listOf(".gif", ".webp", ".mp4", ".jpg", ".jpeg", ".png").any { s.contains(it, true) }
                ) acc.add(s)
            }
            else -> {}
        }
    }

    private fun urlEncode(s: String): String = buildString {
        val hex = "0123456789ABCDEF"
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                append(ch)
            } else {
                append('%'); append(hex[c shr 4]); append(hex[c and 15])
            }
        }
    }
}
