package com.rosechat.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import com.rosechat.ui.common.InAppBannerHost
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rosechat.CrashHandler
import com.rosechat.data.model.CallType
import com.rosechat.data.repository.FirebaseRepo
import com.rosechat.ui.archive.ArchiveScreen
import com.rosechat.ui.auth.AuthScreen
import com.rosechat.ui.auth.AuthStep
import com.rosechat.ui.auth.AuthViewModel
import com.rosechat.ui.calls.CallScreen
import com.rosechat.ui.channel.ChannelInfoScreen
import com.rosechat.ui.chat.ChatScreen
import com.rosechat.ui.chatsettings.ChatSettingsScreen
import com.rosechat.ui.developer.DeveloperScreen
import com.rosechat.ui.main.MainScreen
import com.rosechat.ui.premium.PremiumScreen
import com.rosechat.ui.profile.UserProfileScreen
import java.net.URLDecoder
import java.net.URLEncoder

// Раньше называлось "object R" и конфликтовало по имени со сгенерированным ресурсным
// классом com.rosechat.R (риск: R.string.default_web_client_id для Google Sign-In мог
// случайно резолвиться не туда при неаккуратном импорте). Переименовано в Routes.
object Routes {
    const val AUTH         = "auth"
    const val HOME         = "home"
    const val CHAT         = "chat/{chatId}/{chatName}"
    const val CHANNEL_INFO = "channel_info/{chatId}"
    const val DEV          = "developer"
    const val ARCHIVE      = "archive"
    const val CALL         = "call/{callId}/{isCaller}/{isVideo}/{peerName}"
    const val PREMIUM       = "premium"
    const val CHAT_SETTINGS = "chat_settings"
    const val USER_PROFILE  = "user_profile/{uid}"
    const val STORY_VIEWER  = "story_viewer/{startIndex}"

    fun storyViewer(startIndex: Int) = "story_viewer/$startIndex"

    // Имя чата кодируем — иначе название канала/группы со слэшем или спецсимволом
    // ломает парсинг маршрута Navigation Compose.
    private fun enc(s: String) = URLEncoder.encode(s.ifEmpty { "Чат" }, "UTF-8")
    fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

    fun chat(id: String, name: String) = "chat/$id/${enc(name)}"
    fun channelInfo(id: String) = "channel_info/$id"
    fun call(callId: String, isCaller: Boolean, isVideo: Boolean, peerName: String) =
        "call/$callId/$isCaller/$isVideo/${enc(peerName)}"
    fun userProfile(uid: String) = "user_profile/$uid"
}

@Composable
fun NavGraph(
    nav: NavHostController = rememberNavController(),
    deepLinkChatId: String? = null,
    deepLinkCallId: String? = null,
    deepLinkCallerName: String? = null,
    deepLinkIsVideo: Boolean = false
) {
    val start = if (FirebaseRepo.isLoggedIn) Routes.HOME else Routes.AUTH
    val authVm: AuthViewModel = viewModel()
    val authState by authVm.state.collectAsStateWithLifecycle()

    // Один звонок за раз — id уже показанного, чтобы не переоткрывать один и тот же
    // экран повторно при каждом апдейте документа звонка.
    var handledCallId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(authState.step) {
        if (authState.step == AuthStep.DONE)
            nav.navigate(Routes.HOME) { popUpTo(Routes.AUTH) { inclusive = true } }
    }

    // Открытие чата из пуш-уведомления. Реальное имя подтянется само —
    // ChatScreen следит за документом чата через FirebaseRepo.getChat.
    LaunchedEffect(deepLinkChatId) {
        if (!deepLinkChatId.isNullOrBlank() && FirebaseRepo.isLoggedIn) {
            nav.navigate(Routes.chat(deepLinkChatId, "Чат"))
        }
    }

    // Тап по пуш-уведомлению о звонке (приложение было свёрнуто/убито) — сразу на экран
    // входящего звонка. Имя и isVideo уже пришли в самом пуше (см. FcmService), полный
    // Call-документ (offerSdp и т.п.) CallViewModel подтянет сам через observeCall.
    LaunchedEffect(deepLinkCallId) {
        if (!deepLinkCallId.isNullOrBlank() && FirebaseRepo.isLoggedIn) {
            handledCallId = deepLinkCallId
            com.rosechat.data.repository.NotificationHelper.cancelCall(com.rosechat.RoseChatApp.instance, deepLinkCallId)
            nav.navigate(Routes.call(deepLinkCallId, isCaller = false, isVideo = deepLinkIsVideo, peerName = deepLinkCallerName ?: "PingGram"))
        }
    }

    // Входящий звонок, пока приложение открыто (foreground/фон, но процесс жив) —
    // ловим напрямую из Firestore, без пуша. Пуш (FcmService) нужен именно на случай,
    // когда приложение полностью закрыто.
    LaunchedEffect(FirebaseRepo.currentUid) {
        val uid = FirebaseRepo.currentUid ?: return@LaunchedEffect
        FirebaseRepo.listenIncomingCalls(uid).collect { call ->
            if (call != null && call.id != handledCallId) {
                handledCallId = call.id
                com.rosechat.data.repository.NotificationHelper.cancelCall(com.rosechat.RoseChatApp.instance, call.id)
                nav.navigate(Routes.call(call.id, isCaller = false, isVideo = call.type == CallType.VIDEO, peerName = call.callerName))
            }
        }
    }

    // Лог последнего краша (если был) — на случай, если Android Studio/Logcat под
    // рукой нет. Показываем сразу при открытии приложения, с кнопкой "Скопировать".
    val context = LocalContext.current
    var crashLog by remember { mutableStateOf(CrashHandler.getSavedCrash(context)) }
    if (crashLog != null) {
        CrashLogDialog(text = crashLog!!, onDismiss = { CrashHandler.clearSavedCrash(context); crashLog = null })
    }

    Box(Modifier.fillMaxSize()) {
    NavHost(nav, start) {
        composable(Routes.AUTH) { AuthScreen(authVm, onSecretUnlock = { nav.navigate(Routes.DEV) }) }

        composable(Routes.HOME) {
            MainScreen(
                onOpenChat      = { id, name -> nav.navigate(Routes.chat(id, name)) },
                onOpenCalls     = { /* TODO: calls list */ },
                onOpenDeveloper = { nav.navigate(Routes.DEV) },
                onOpenArchive   = { nav.navigate(Routes.ARCHIVE) },
                onOpenPremium      = { nav.navigate(Routes.PREMIUM) },
                onOpenChatSettings = { nav.navigate(Routes.CHAT_SETTINGS) },
                onOpenStoryViewer  = { idx -> nav.navigate(Routes.storyViewer(idx)) },
                onSignOut       = { nav.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } } }
            )
        }

        composable(Routes.ARCHIVE) {
            ArchiveScreen(
                onBack = { nav.popBackStack() },
                onOpenChat = { id, name -> nav.navigate(Routes.chat(id, name)) }
            )
        }

        composable(Routes.CHAT) { back ->
            val chatId = back.arguments?.getString("chatId") ?: ""
            val chatName = Routes.dec(back.arguments?.getString("chatName") ?: "")
            ChatScreen(
                chatId     = chatId,
                chatName   = chatName,
                onBack     = { nav.popBackStack() },
                onCall     = { callId, peerName, isVideo -> nav.navigate(Routes.call(callId, isCaller = true, isVideo = isVideo, peerName = peerName)) },
                onOpenInfo = { nav.navigate(Routes.channelInfo(chatId)) },
                onOpenProfile = { uid -> nav.navigate(Routes.userProfile(uid)) }
            )
        }

        composable(Routes.USER_PROFILE) { back ->
            val uid = back.arguments?.getString("uid") ?: ""
            UserProfileScreen(
                uid = uid,
                onBack = { nav.popBackStack() },
                onOpenChat = { id, name -> nav.navigate(Routes.chat(id, name)) { popUpTo(Routes.HOME) } }
            )
        }

        composable(Routes.PREMIUM) {
            PremiumScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.CHAT_SETTINGS) {
            ChatSettingsScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.STORY_VIEWER) { back ->
            val startIndex = back.arguments?.getString("startIndex")?.toIntOrNull() ?: 0
            com.rosechat.ui.stories.StoryViewerScreen(startIndex = startIndex, onClose = { nav.popBackStack() })
        }

        composable(Routes.CHANNEL_INFO) { back ->
            ChannelInfoScreen(
                chatId = back.arguments?.getString("chatId") ?: "",
                onBack = { nav.popBackStack() },
                onLeft = { nav.popBackStack(Routes.HOME, inclusive = false) }
            )
        }

        composable(Routes.DEV) {
            DeveloperScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.CALL) { back ->
            val callId   = back.arguments?.getString("callId") ?: ""
            val isCaller = back.arguments?.getString("isCaller") == "true"
            val isVideo  = back.arguments?.getString("isVideo") == "true"
            val peerName = Routes.dec(back.arguments?.getString("peerName") ?: "")
            CallScreen(
                callId = callId, isCaller = isCaller, isVideo = isVideo, peerName = peerName,
                onFinish = { nav.popBackStack() }
            )
        }
    }
    // Уведомления внутри приложения: плашка о новом сообщении поверх любого экрана.
    InAppBannerHost(
        onOpenChat = { id, name -> if (FirebaseRepo.isLoggedIn) nav.navigate(Routes.chat(id, name)) },
        modifier = Modifier.align(Alignment.TopCenter)
    )
    }
}

// Текст краша выделяемый (SelectionContainer) — чтобы можно было скопировать вручную,
// если кнопка "Скопировать" почему-то не сработает на конкретном устройстве.
@Composable
private fun CrashLogDialog(text: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Найден лог последнего краша") },
        text = {
            Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Скопировать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
