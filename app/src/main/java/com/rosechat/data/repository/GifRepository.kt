package com.rosechat.data.repository

import android.util.Log
import com.rosechat.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

// GIF через Klipy (docs.klipy.com) — бесплатный без лимита сервис, пришедший на замену
// Tenor (тот закрылся 30.06.2026) и вместо платного теперь Giphy. Ключ берётся из
// BuildConfig.GIF_API_KEY (см. local.properties → GIF_API_KEY=...).
data class GifResult(val id: String, val previewUrl: String, val fullUrl: String)

object GifRepository {
    private const val TAG = "GifRepository"
    private const val BASE = "https://api.klipy.com/api/v1"
    private val apiKey get() = BuildConfig.GIF_API_KEY

    // Klipy привязывает выдачу/рекламу к customer_id — держим свой анонимный id на устройство,
    // без персональных данных, только для соответствия их API.
    private var cachedCustomerId: String? = null
    private fun customerId(): String {
        cachedCustomerId?.let { return it }
        val id = "pinggram-" + UUID.randomUUID().toString().take(12)
        cachedCustomerId = id
        return id
    }

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    suspend fun trending(page: Int = 1): Result<List<GifResult>> =
        request("$BASE/$apiKey/gifs/trending?page=$page&per_page=24&customer_id=${customerId()}")

    suspend fun search(query: String, page: Int = 1): Result<List<GifResult>> {
        val q = URLEncoder.encode(query, "UTF-8")
        return request("$BASE/$apiKey/gifs/search?q=$q&page=$page&per_page=24&customer_id=${customerId()}")
    }

    private suspend fun request(urlStr: String): Result<List<GifResult>> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IllegalStateException("GIF_API_KEY не задан в local.properties"))
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 10_000
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                Log.w(TAG, "Klipy HTTP $code: ${body.take(300)}")
                return@withContext Result.failure(Exception("Klipy HTTP $code"))
            }
            // Раньше тут сразу парсили тело и не проверяли поле "result" — если Klipy само
            // сообщало об ошибке (например, ключ ещё не подтверждён/не прошёл модерацию,
            // превышен лимит тестового ключа и т.п.), это выглядело как HTTP 200 с пустым
            // списком, то есть неотличимо от "гифок правда нет". Теперь читаем и "result",
            // и любое поле с текстом ошибки, если оно есть в ответе.
            val root = try { JSONObject(body) } catch (e: Exception) {
                Log.w(TAG, "Klipy: не-JSON ответ: ${body.take(300)}")
                return@withContext Result.failure(Exception("Klipy: непонятный ответ сервера"))
            }
            if (!root.optBoolean("result", true)) {
                val msg = root.optString("message").ifBlank { root.optString("error").ifBlank { "result=false" } }
                Log.w(TAG, "Klipy result=false: $msg (body: ${body.take(300)})")
                return@withContext Result.failure(Exception("Klipy: $msg"))
            }
            val parsed = parseGifs(root)
            val itemsArray = root.optJSONObject("data")?.optJSONArray("data") ?: root.optJSONArray("data")
            val rawItemCount = itemsArray?.length() ?: 0
            if (parsed.isEmpty() && rawItemCount > 0) {
                // Если и рекурсивный разбор ничего не нашёл — покажем прямо в приложении,
                // какие поля вообще есть у элемента. Logcat для этого больше не нужен.
                val fields = itemsArray?.optJSONObject(0)?.keys()?.asSequence()?.take(8)?.joinToString(",") ?: "?"
                Log.w(TAG, "Klipy: $rawItemCount элементов, ни одной ссылки. Поля: $fields. Пример: ${body.take(500)}")
                return@withContext Result.failure(Exception("Klipy вернул $rawItemCount элементов без ссылок. Поля: $fields"))
            }
            Result.success(parsed)
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка запроса GIF: ${e.message}")
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    // Ответ Klipy: { "result": true, "data": { "data": [ {...gif...}, ... ], "has_next": bool } }
    // Точная вложенность files.<формат>.<размер>.url в доке не зафиксирована буквально —
    // поэтому URL достаём рекурсивным поиском первого поля "url", это устойчиво к разным
    // вариантам вложенности форматов/размеров.
    private fun parseGifs(root: JSONObject): List<GifResult> {
        val items = root.optJSONObject("data")?.optJSONArray("data")
            ?: root.optJSONArray("data")
            ?: JSONArray()
        val out = mutableListOf<GifResult>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            // Индекс в конце — гарантия уникальности ключа: LazyVerticalGrid в панели GIF
            // падает, если два элемента получат одинаковый key, а Klipy вполне может
            // прислать один и тот же слаг дважды на странице.
            val id = item.optString("id", "").ifBlank { item.optString("slug", "") }.ifBlank { "gif" } + "_$i"

            // РАНЬШЕ: брали строго item["files"] и, если этого поля нет (а Klipy в
            // зависимости от типа контента и версии кладёт ссылки то в "files", то в "file",
            // то в "media"/"variants"), молча пропускали элемент через `continue`. Отсюда и
            // "24 элемента, 0 распознано" на скриншоте: элементы приходили нормальные, просто
            // разбор искал их не там. Теперь ищем ссылки рекурсивно по ВСЕМУ элементу —
            // какая бы вложенность ни была, рабочая ссылка находится.
            val urls = collectUrls(item)
            if (urls.isEmpty()) continue

            // Для показа в сетке берём самый "лёгкий" вариант (обычно в пути есть sm/xs/
            // preview/thumb), для отправки — полноразмерный gif/webp.
            val full = urls.firstOrNull { it.endsWith(".gif", true) }
                ?: urls.firstOrNull { it.endsWith(".webp", true) }
                ?: urls.first()
            // Превью — облегчённый вариант той же гифки, если он есть в ответе. Ищем по
            // маркерам размера именно как отдельный кусок пути ("/sm/", "_xs."), а не просто
            // по вхождению букв — иначе легко зацепить случайный id.
            val preview = urls.firstOrNull { u ->
                (u.endsWith(".gif", true) || u.endsWith(".webp", true)) &&
                    listOf("/sm", "_sm", "/xs", "_xs", "thumb", "preview", "tiny", "nano").any { u.contains(it, true) }
            } ?: full

            out.add(GifResult(id = id, previewUrl = preview, fullUrl = full))
        }
        return out
    }

    // Собирает все http(s)-ссылки на картинки/видео из произвольно вложенного JSON.
    // Порядок сохраняется (LinkedHashSet) — первым идёт то, что лежит ближе к началу ответа.
    private fun collectUrls(node: Any?, acc: LinkedHashSet<String> = LinkedHashSet()): List<String> {
        when (node) {
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) collectUrls(node.opt(keys.next()), acc)
            }
            is JSONArray -> for (i in 0 until node.length()) collectUrls(node.opt(i), acc)
            is String -> {
                if (node.startsWith("http", true) &&
                    listOf(".gif", ".webp", ".mp4", ".jpg", ".jpeg", ".png").any { node.contains(it, true) }
                ) acc.add(node)
            }
            else -> {}
        }
        return acc.toList()
    }
}
