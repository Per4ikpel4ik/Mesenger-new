package com.rosechat.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosechat.data.local.StoriesFeedState
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.ui.common.SmartImage
import com.rosechat.ui.common.hasImage
import com.rosechat.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STORY_MS = 5000

@Composable
fun StoryViewerScreen(startIndex: Int, onClose: () -> Unit) {
    val groups = StoriesFeedState.groups
    val myUid = FirebaseRepo.currentUid ?: ""
    var groupIdx by remember { mutableIntStateOf(startIndex.coerceIn(0, (groups.size - 1).coerceAtLeast(0))) }
    var storyIdx by remember { mutableIntStateOf(0) }
    var progress by remember(groupIdx, storyIdx) { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (groups.isEmpty() || groupIdx !in groups.indices) { onClose(); return }
    val group = groups[groupIdx]
    if (storyIdx !in group.indices) { onClose(); return }
    val story = group[storyIdx]
    val isMine = story.userId == myUid

    fun nextStory() {
        if (storyIdx < group.lastIndex) storyIdx++
        else if (groupIdx < groups.lastIndex) { groupIdx++; storyIdx = 0 }
        else onClose()
    }
    fun prevStory() {
        if (storyIdx > 0) storyIdx--
        else if (groupIdx > 0) { groupIdx--; storyIdx = (groups[groupIdx].size - 1).coerceAtLeast(0) }
    }

    LaunchedEffect(story.id) {
        if (!isMine) FirebaseRepo.markStoryViewed(story.id, myUid)
    }

    LaunchedEffect(groupIdx, storyIdx, paused) {
        val stepMs = 16L
        while (progress < 1f) {
            if (!paused) {
                delay(stepMs)
                progress += stepMs.toFloat() / STORY_MS
            } else delay(stepMs)
        }
        if (!paused) nextStory()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Раньше тут декодировался ТОЛЬКО story.mediaData (Base64) — с переездом историй на
        // Cloudinary (см. FirebaseRepo.postStory) часть историй приходит как обычная ссылка
        // в mediaUrl, и этот код показывал бы для них пустой чёрный экран. SmartImage —
        // тот же общий рендерер, что и везде по проекту — сам решает, откуда брать картинку.
        SmartImage(story.mediaUrl, story.mediaData, Modifier.fillMaxSize(), ContentScale.Fit)

        // Тап по краям — вперёд/назад, долгое нажатие — пауза; свайп в сторону — сосед по ленте (другой автор)
        Row(Modifier.fillMaxSize().pointerInput(groupIdx) {
            detectHorizontalDragGestures(
                onDragEnd = {},
                onHorizontalDrag = { _, dragAmount -> if (dragAmount > 40) prevStory() else if (dragAmount < -40) nextStory() }
            )
        }) {
            Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) {
                detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false }, onTap = { prevStory() })
            })
            Box(Modifier.weight(1f).fillMaxHeight().pointerInput(Unit) {
                detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false }, onTap = { nextStory() })
            })
        }

        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                group.forEachIndexed { i, _ ->
                    val p = when { i < storyIdx -> 1f; i == storyIdx -> progress; else -> 0f }
                    LinearProgressIndicator(
                        progress = { p }, modifier = Modifier.weight(1f).height(2.5.dp).clip(RoundedCornerShape(2.dp)),
                        color = Color.White, trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(BgTertiary)) {
                    if (hasImage(story.userPhotoUrl, story.userPhotoData))
                        SmartImage(story.userPhotoUrl, story.userPhotoData, Modifier.fillMaxSize().clip(CircleShape))
                }
                Spacer(Modifier.width(8.dp))
                Text(story.userName.ifBlank { "Без имени" }, color = Color.White, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (isMine) {
                    IconButton({ scope.launch { FirebaseRepo.deleteStory(story.id); nextStory() } }) {
                        Icon(Icons.Default.Delete, null, tint = Color.White)
                    }
                }
                IconButton(onClose) { Icon(Icons.Default.Close, null, tint = Color.White) }
            }
        }
    }
}
