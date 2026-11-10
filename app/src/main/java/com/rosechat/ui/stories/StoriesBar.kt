package com.rosechat.ui.stories

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.rosechat.data.local.StoriesFeedState
import com.rosechat.data.model.Story
import com.rosechat.data.model.User
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.ui.common.SmartImage
import com.rosechat.ui.common.hasImage
import com.rosechat.ui.theme.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File

// Лента историй — кружки сверху списка чатов, как в Telegram/Instagram. Первый кружок —
// "моя история" (+ добавить, либо открыть свою, если уже есть). Дальше — авторы из числа
// контактов (memberIds чатов пользователя), у кого есть активная (не старше 24ч) история.
// Непросмотренные — с градиентным кольцом, просмотренные — с блёклым.
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StoriesBar(me: User?, contactUids: Set<String>, onOpenViewer: (startIndex: Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allStories by remember { mutableStateOf<List<Story>>(emptyList()) }
    var uploading by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val camPerm = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        FirebaseRepo.getActiveStories()
            .catch { e ->
                android.util.Log.w("StoriesBar", "Истории не загрузились: ${e.message}")
                // Раньше эта ошибка (например, PERMISSION_DENIED, если правила Firestore
                // ещё не задеплоены после правки) уходила только в логи — снаружи это
                // выглядело просто как "историй нет ни у кого", неотличимо от нормального
                // состояния "их пока никто не публиковал". Дублируем в тост, чтобы разница
                // была видна.
                // Самый частый случай — PERMISSION_DENIED: правила для коллекции stories
                // лежат в firestore.rules, а этот файл деплоится ОТДЕЛЬНО от Cloud Functions.
                // Обрезанный текст ошибки Firestore ("Missing or ins...") ничего не подсказывал,
                // поэтому пишем прямо, что делать.
                val denied = e.message?.contains("PERMISSION_DENIED", true) == true ||
                    e.message?.contains("Missing or insufficient", true) == true
                val text = if (denied)
                    "Истории: правила Firestore не задеплоены. Выполни: firebase deploy --only firestore:rules"
                else "Не удалось загрузить истории: ${e.message}"
                android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
            }
            .collect { allStories = it }
    }

    val myUid = me?.uid ?: ""
    // Группируем по автору; свои — первыми (если есть), затем контакты по свежести последней истории.
    val groups = remember(allStories, myUid, contactUids) {
        val byUser = allStories.filter { it.userId == myUid || it.userId in contactUids }
            .groupBy { it.userId }.values.map { it.sortedBy { s -> s.createdAt?.seconds ?: 0 } }
        val mine = byUser.filter { it.first().userId == myUid }
        val others = byUser.filter { it.first().userId != myUid }
            .sortedByDescending { it.maxOf { s -> s.createdAt?.seconds ?: 0 } }
        mine + others
    }
    LaunchedEffect(groups) { StoriesFeedState.groups = groups }

    fun launchPostFlow(uri: Uri) {
        val u = me ?: return
        uploading = true
        scope.launch {
            // Раньше при неудаче (не смогли обработать фото, или запись в Firestore
            // не прошла) не было вообще никакой обратной связи — крутился спиннер и
            // просто ничего не появлялось, будто "истории не работают". Теперь как
            // минимум показываем тост, как и остальные ошибки медиа по проекту (см.
            // MediaSaver.kt) — молчаливого провала быть не должно.
            // Сжатие/загрузка теперь целиком внутри FirebaseRepo.postStory — она сама
            // решает, грузить ли в Cloudinary (щедрый бюджет) или паковать в Base64
            // (см. комментарий в FirebaseRepo.postStory про баг с бюджетом 600 КБ).
            FirebaseRepo.postStory(context, u, uri).onFailure { e ->
                android.widget.Toast.makeText(context, "Не удалось опубликовать историю: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
            uploading = false
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { launchPostFlow(it) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) launchPostFlow(uri)
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                MyStoryCircle(
                    me = me, hasStory = groups.firstOrNull()?.firstOrNull()?.userId == myUid && myUid.isNotBlank(),
                    uploading = uploading,
                    onAdd = { showPicker = true },
                    onOpen = { onOpenViewer(0) }
                )
            }
            items(groups.filter { it.first().userId != myUid }, key = { it.first().userId }) { group ->
                val idx = groups.indexOf(group)
                val unseen = group.any { myUid !in it.viewedBy }
                StoryCircle(group.first(), unseen) { onOpenViewer(idx) }
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {},
            title = { Text("Новая история", color = TextPrimary) },
            containerColor = BgSecondary,
            text = {
                Column {
                    Row(Modifier.fillMaxWidth().clickable {
                        showPicker = false
                        // Раньше тут сразу запускался системный камера-интент без проверки
                        // разрешения CAMERA. Т.к. приложение объявляет это разрешение в
                        // манифесте (нужно видеокружкам/звонкам), Android требует, чтобы оно
                        // было реально выдано — иначе даже неявный интент камеры молча не
                        // срабатывает. В ChatScreen/CallsScreen разрешение запрашивается перед
                        // использованием камеры (см. camPerm.launchPermissionRequest()) — тут
                        // этого не было, и это ломало именно кнопку "Камера" в историях.
                        if (!camPerm.status.isGranted) {
                            camPerm.launchPermissionRequest()
                        } else {
                            val file = File(context.cacheDir.resolve("story_captures").apply { mkdirs() }, "story_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, null, tint = Accent); Spacer(Modifier.width(12.dp))
                        Text("Камера", color = TextPrimary)
                    }
                    Row(Modifier.fillMaxWidth().clickable {
                        showPicker = false; galleryPicker.launch("image/*")
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhotoLibrary, null, tint = Accent); Spacer(Modifier.width(12.dp))
                        Text("Галерея", color = TextPrimary)
                    }
                }
            }
        )
    }
}

@Composable
private fun MyStoryCircle(me: User?, hasStory: Boolean, uploading: Boolean, onAdd: () -> Unit, onOpen: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(
            Modifier.size(60.dp).clip(CircleShape)
                .then(if (hasStory) Modifier.border(2.5.dp, Brush.sweepGradient(listOf(Accent, AccentLight, Accent)), CircleShape) else Modifier)
                .padding(3.dp).clip(CircleShape).background(BgTertiary)
                .clickable { if (hasStory) onOpen() else onAdd() },
            Alignment.Center
        ) {
            if (hasImage(me?.photoUrl ?: "", me?.photoData ?: ""))
                SmartImage(me?.photoUrl ?: "", me?.photoData ?: "", Modifier.fillMaxSize().clip(CircleShape))
            else Text(me?.displayName?.firstOrNull()?.toString() ?: "?", color = Color.White)

            if (uploading) CircularProgressIndicator(Modifier.size(24.dp), color = Accent, strokeWidth = 2.dp)
            else if (!hasStory) Box(Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(Accent).border(2.dp, BgPrimary, CircleShape), Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Моя история", style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StoryCircle(story: Story, unseen: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(
            Modifier.size(60.dp).clip(CircleShape)
                .border(2.5.dp, if (unseen) Brush.sweepGradient(listOf(Accent, AccentLight, Accent)) else Brush.linearGradient(listOf(TextTertiary, TextTertiary)), CircleShape)
                .padding(3.dp).clip(CircleShape).background(BgTertiary)
                .clickable(onClick = onClick),
            Alignment.Center
        ) {
            if (hasImage(story.userPhotoUrl, story.userPhotoData))
                SmartImage(story.userPhotoUrl, story.userPhotoData, Modifier.fillMaxSize().clip(CircleShape))
            else Text(story.userName.firstOrNull()?.toString() ?: "?", color = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(story.userName.ifBlank { "Без имени" }, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
