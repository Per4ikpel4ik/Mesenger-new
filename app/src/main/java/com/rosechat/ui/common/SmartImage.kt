package com.rosechat.ui.common

import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

// Единая точка рендера фото/картинок по проекту: пока нет Storage, часть изображений
// хранится как Base64 прямо в Firestore-документе (data), часть — как обычная ссылка (url,
// задел под будущий Storage). Показываем то, что есть; ничего не рисуем, если оба поля пустые —
// вызывающий код сам показывает заглушку (инициал, иконку и т.п.).
@Composable
fun SmartImage(url: String, data: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    when {
        data.isNotBlank() -> {
            val bytes = remember(data) { try { Base64.decode(data, Base64.NO_WRAP) } catch (_: Exception) { null } }
            if (bytes != null) AsyncImage(bytes, null, modifier, contentScale = contentScale)
        }
        url.isNotBlank() -> AsyncImage(url, null, modifier, contentScale = contentScale)
    }
}

fun hasImage(url: String, data: String) = url.isNotBlank() || data.isNotBlank()
