package com.rosechat.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.rosechat.R
import com.rosechat.data.local.TermsPrefs
import com.rosechat.data.model.PROFILE_STICKERS
import com.rosechat.ui.theme.*

@Composable
fun AuthScreen(vm: AuthViewModel = viewModel(), onSecretUnlock: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (state.step) {
        AuthStep.OTP          -> OtpScreen(vm)
        AuthStep.PICK_STICKER -> StickerPickScreen(vm)
        AuthStep.DONE         -> {}
        AuthStep.INPUT -> when (state.mode) {
            AuthMode.PHONE -> PhoneScreen(vm, onSecretUnlock)
            AuthMode.EMAIL -> EmailScreen(vm, onSecretUnlock)
        }
    }
}

@Composable
private fun StickerPickScreen(vm: AuthViewModel) {
    Column(
        Modifier.fillMaxSize().background(BgPrimary).padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        Text("Выбери стикер профиля", style = MaterialTheme.typography.headlineSmall, color = TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Будет виден рядом с твоим именем. Можно сменить позже в профиле.",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))

        var selected by remember { mutableStateOf<String?>(null) }

        FlowStickerGrid(selected) { selected = it }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { selected?.let { vm.setProfileSticker(it) } },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, disabledContainerColor = BgTertiary)
        ) { Text("Готово", color = Color.White, fontSize = 16.sp) }

        Spacer(Modifier.height(12.dp))
        TextButton({ vm.skipStickerPick() }) { Text("Пропустить", color = TextSecondary) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FlowStickerGrid(selected: String?, onSelect: (String) -> Unit) {
    val rows = PROFILE_STICKERS.chunked(5)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { s ->
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (selected == s) Accent.copy(0.25f) else BgTertiary)
                            .clickable { onSelect(s) },
                        Alignment.Center
                    ) { Text(s, fontSize = 28.sp) }
                }
            }
        }
    }
}

@Composable
private fun PhoneScreen(vm: AuthViewModel, onSecretUnlock: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    val keyboard = LocalSoftwareKeyboardController.current

    // Если пользователь уже соглашался на этом устройстве раньше — не спрашиваем заново.
    LaunchedEffect(Unit) { vm.setAgreedToTerms(TermsPrefs.isAccepted(activity)) }

    Column(Modifier.fillMaxSize().background(BgPrimary).verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(64.dp))
        Image(
            painter = painterResource(R.drawable.logo_pinggram_mark),
            contentDescription = "PingGram",
            modifier = Modifier.size(96.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text("PingGram", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Быстрый и безопасный мессенджер", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        ModeToggle(AuthMode.PHONE) { vm.setMode(AuthMode.EMAIL) }
        Spacer(Modifier.height(24.dp))
        PingField(state.phone, { vm.onPhoneChanged(it) }, "Номер телефона", "+79001234567",
            KeyboardType.Phone, leadingIcon = { Icon(Icons.Default.Phone, null, tint = Accent) }, isError = state.error != null)
        ErrorText(state.error)
        Spacer(Modifier.height(12.dp))
        TermsCheckbox(state.agreedToTerms,
            onAgreedChange = { vm.setAgreedToTerms(it); TermsPrefs.setAccepted(activity, it) },
            onShowTerms = { vm.toggleTermsDialog(true) })
        Spacer(Modifier.height(16.dp))
        PingButton("Получить код", state.isLoading, state.phone.length >= 11 && state.agreedToTerms) { keyboard?.hide(); vm.sendOtp(activity) }
        Spacer(Modifier.height(20.dp))
        OrDivider()
        Spacer(Modifier.height(20.dp))
        GoogleSignInButton(vm)
        Spacer(Modifier.height(28.dp))
        CreditsFooter(onSecretUnlock)
    }

    if (state.showTerms) TermsDialog { vm.toggleTermsDialog(false) }
}

@Composable
private fun EmailScreen(vm: AuthViewModel, onSecretUnlock: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var showPwd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.setAgreedToTerms(TermsPrefs.isAccepted(context)) }

    Column(Modifier.fillMaxSize().background(BgPrimary).verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(60.dp))
        Text("🌸", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(if (state.isRegister) "Регистрация" else "Вход", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Spacer(Modifier.height(28.dp))
        ModeToggle(AuthMode.EMAIL) { vm.setMode(AuthMode.PHONE) }
        Spacer(Modifier.height(20.dp))
        AnimatedVisibility(state.isRegister, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                PingField(state.displayName, { vm.onNameChanged(it) }, "Имя", "Как тебя зовут?",
                    leadingIcon = { Icon(Icons.Default.Person, null, tint = Accent) })
                Spacer(Modifier.height(12.dp))
                PingField(state.username, { vm.onUsernameChanged(it) }, "@username", "мой_ник",
                    leadingIcon = { Icon(Icons.Default.AlternateEmail, null, tint = Accent) })
                Spacer(Modifier.height(12.dp))
            }
        }
        PingField(state.email, { vm.onEmailChanged(it) }, "Email", "example@mail.com",
            KeyboardType.Email, leadingIcon = { Icon(Icons.Default.Email, null, tint = Accent) }, isError = state.error != null)
        Spacer(Modifier.height(12.dp))
        PingField(state.password, { vm.onPasswordChanged(it) }, "Пароль", "Минимум 6 символов",
            KeyboardType.Password,
            visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = Accent) },
            trailingIcon = { IconButton({ showPwd = !showPwd }) { Icon(if (showPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextSecondary) } },
            isError = state.error != null)
        AnimatedVisibility(state.isRegister, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                Spacer(Modifier.height(12.dp))
                PingField(state.confirmPassword, { vm.onConfirmChanged(it) }, "Подтверди пароль", "",
                    KeyboardType.Password, visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.LockOpen, null, tint = Accent) },
                    imeAction = ImeAction.Done, onIme = { keyboard?.hide(); vm.submitEmail() })
                Spacer(Modifier.height(12.dp))
                TermsCheckbox(state.agreedToTerms,
                    onAgreedChange = { vm.setAgreedToTerms(it); TermsPrefs.setAccepted(context, it) },
                    onShowTerms = { vm.toggleTermsDialog(true) })
            }
        }
        ErrorText(state.error)
        if (state.resetSent) { Spacer(Modifier.height(6.dp)); Text("✅ Письмо отправлено", color = Online, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(24.dp))
        PingButton(if (state.isRegister) "Зарегистрироваться" else "Войти", state.isLoading,
            state.email.isNotBlank() && state.password.isNotBlank() && (!state.isRegister || state.agreedToTerms)) { keyboard?.hide(); vm.submitEmail() }
        Spacer(Modifier.height(16.dp))
        Row {
            Text(if (state.isRegister) "Уже есть аккаунт? " else "Нет аккаунта? ", color = TextSecondary)
            Text(if (state.isRegister) "Войти" else "Зарегистрироваться", color = Accent, modifier = Modifier.clickable { vm.setRegister(!state.isRegister) })
        }
        if (!state.isRegister) {
            Spacer(Modifier.height(12.dp))
            Text("Забыл пароль?", color = AccentLight, modifier = Modifier.clickable { vm.resetPassword() })
        }
        Spacer(Modifier.height(32.dp))
        CreditsFooter(onSecretUnlock)
    }

    if (state.showTerms) TermsDialog { vm.toggleTermsDialog(false) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtpScreen(vm: AuthViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        TopAppBar(title = {}, navigationIcon = {
            IconButton({ vm.setMode(AuthMode.PHONE) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            Text("📱", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text("Код из SMS", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text("Отправили на ${state.phone}", color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(40.dp))
            Box(contentAlignment = Alignment.Center) {
                BasicTextField(state.otp, { vm.onOtpChanged(it) }, Modifier.size(1.dp).focusRequester(focus),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), cursorBrush = SolidColor(Color.Transparent))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(6) { i ->
                        val d = state.otp.getOrNull(i)?.toString() ?: ""
                        val bc = when { state.error != null -> ErrorRed; i == state.otp.length -> Accent; d.isNotEmpty() -> AccentMuted; else -> BgTertiary }
                        Box(Modifier.size(48.dp, 60.dp).clip(RoundedCornerShape(12.dp)).background(BgSecondary)
                            .border(if (i == state.otp.length) 2.dp else 1.dp, bc, RoundedCornerShape(12.dp)), Alignment.Center) {
                            if (d.isNotEmpty()) Text(d, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                            else if (i == state.otp.length) Box(Modifier.size(2.dp, 28.dp).background(Accent))
                        }
                    }
                }
            }
            ErrorText(state.error)
            Spacer(Modifier.height(32.dp))
            PingButton("Подтвердить", state.isLoading, state.otp.length == 6) { vm.verifyOtp() }
        }
    }
}

// ── Shared UI ─────────────────────────────────────────────

@Composable
fun CreditsFooter(onSecretUnlock: () -> Unit = {}) {
    // Скрытый вход в панель разработчика (DeveloperScreen) — 5 нажатий на подпись
    // авторства подряд, не медленнее раза в 1.5с между нажатиями. Сама панель всё равно
    // защищена PIN-кодом (см. DeveloperViewModel.PIN) — это только способ до неё
    // добраться: иначе кнопка её открытия видна лишь тем, у кого уже стоит
    // isDeveloper=true в Firestore, а выставить его там больше неоткуда.
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable {
            val now = System.currentTimeMillis()
            tapCount = if (now - lastTapAt > 1500) 1 else tapCount + 1
            lastTapAt = now
            if (tapCount >= 5) {
                tapCount = 0
                onSecretUnlock()
            }
        }
    ) {
        Text("by Ivashka", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        Spacer(Modifier.height(2.dp))
        Text("by Claude", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
    }
}

@Composable
fun ModeToggle(current: AuthMode, onSwitch: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgSecondary).padding(4.dp)) {
        listOf(AuthMode.PHONE to "📱 Телефон", AuthMode.EMAIL to "✉️ Email").forEach { (m, label) ->
            val sel = current == m
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .background(if (sel) Accent else Color.Transparent)
                .clickable(enabled = !sel) { onSwitch() }.padding(vertical = 12.dp), Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) Color.White else TextSecondary)
            }
        }
    }
}

@Composable
fun PingField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String = "",
              keyboardType: KeyboardType = KeyboardType.Text, imeAction: ImeAction = ImeAction.Next,
              onIme: (() -> Unit)? = null, visualTransformation: VisualTransformation = VisualTransformation.None,
              leadingIcon: (@Composable () -> Unit)? = null, trailingIcon: (@Composable () -> Unit)? = null, isError: Boolean = false) {
    OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(),
        label = { Text(label, color = TextSecondary) },
        placeholder = { Text(placeholder, color = TextTertiary) },
        leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        singleLine = true, isError = isError, visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onIme?.invoke() }, onNext = { onIme?.invoke() }),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            focusedBorderColor = Accent, unfocusedBorderColor = BgTertiary,
            errorBorderColor = ErrorRed, focusedLabelColor = Accent, unfocusedLabelColor = TextSecondary,
            cursorColor = Accent, focusedContainerColor = BgSecondary, unfocusedContainerColor = BgSecondary, errorContainerColor = BgSecondary))
}

@Composable
fun PingButton(text: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().height(54.dp), enabled = enabled && !loading, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White,
            disabledContainerColor = AccentMuted.copy(0.4f), disabledContentColor = Color.White.copy(0.4f))) {
        AnimatedContent(loading, label = "btn") { l ->
            if (l) CircularProgressIndicator(Modifier.size(22.dp), Color.White, strokeWidth = 2.5.dp)
            else Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun ErrorText(error: String?) {
    AnimatedVisibility(error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Text(error ?: "", color = ErrorRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    }
}

@Composable
fun OrDivider() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), 1.dp, BgTertiary)
        Text("  или  ", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(Modifier.weight(1f), 1.dp, BgTertiary)
    }
}

@Composable
fun TermsCheckbox(agreed: Boolean, onAgreedChange: (Boolean) -> Unit, onShowTerms: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = agreed, onCheckedChange = onAgreedChange,
            colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = TextSecondary)
        )
        Row {
            Text("Принимаю ", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Text(
                "условия использования", color = AccentLight, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { onShowTerms() }
            )
        }
    }
}

@Composable
fun TermsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSecondary,
        title = { Text("Пользовательское соглашение", color = TextPrimary) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(TERMS_TEXT, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Понятно", color = Accent) } }
    )
}

// Черновик пользовательского соглашения. Ивашка, это заготовка для старта — перед публикацией
// в Google Play стоит показать её юристу или хотя бы внимательно вычитать самому:
// формулировки про обработку персональных данных лучше сверить с 152-ФЗ, если целевая
// аудитория — Россия, либо с GDPR, если планируется международная аудитория.
private const val TERMS_TEXT = """1. ОБЩИЕ ПОЛОЖЕНИЯ
Используя приложение PingGram ("Приложение"), вы соглашаетесь с настоящими условиями. Если вы не согласны — пожалуйста, не используйте Приложение.

2. РЕГИСТРАЦИЯ
Для использования Приложения нужно создать аккаунт по email или номеру телефона. Вы обязуетесь предоставлять достоверные данные и несёте ответственность за сохранность доступа к своему аккаунту.

3. КАКИЕ ДАННЫЕ МЫ ОБРАБАТЫВАЕМ
Мы обрабатываем: имя, email или номер телефона, аватар (если вы его загрузили), а также сообщения и файлы, которые вы отправляете через Приложение, — для обеспечения его работы.

4. КАК МЫ ИСПОЛЬЗУЕМ ДАННЫЕ
Данные используются исключительно для работы функций Приложения (переписка, звонки, поиск пользователей). Мы не продаём и не передаём ваши данные третьим лицам в рекламных целях.

5. ХРАНЕНИЕ ДАННЫХ
Данные хранятся на серверах Firebase (Google). Мы принимаем разумные меры для защиты данных, но не можем гарантировать абсолютную защиту от несанкционированного доступа.

6. ВАШИ ПРАВА
Вы можете в любой момент запросить удаление своего аккаунта и связанных с ним данных, обратившись в поддержку Приложения.

7. ПРАВИЛА ПОВЕДЕНИЯ
Запрещается использовать Приложение для рассылки спама, угроз, незаконного контента, а также для действий, нарушающих законодательство.

8. ВОЗРАСТНЫЕ ОГРАНИЧЕНИЯ
Приложением могут пользоваться лица старше 14 лет. Если вам меньше 18 лет, использование Приложения должно быть согласовано с родителем или законным представителем.

9. ОГРАНИЧЕНИЕ ОТВЕТСТВЕННОСТИ
Приложение предоставляется "как есть". Разработчик не несёт ответственности за перерывы в работе, потерю данных или иные убытки, возникшие в результате использования Приложения.

10. ИЗМЕНЕНИЕ УСЛОВИЙ
Мы можем обновлять эти условия. Продолжение использования Приложения после изменений означает согласие с новой редакцией."""

// Вход через Google-аккаунт. web client id берётся автоматически из google-services.json
// (ресурс default_web_client_id генерируется плагином com.google.gms.google-services).
@Composable
fun GoogleSignInButton(vm: AuthViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) vm.signInWithGoogle(idToken)
            else vm.googleSignInFailed("Google не вернул токен. Попробуй ещё раз.")
        } catch (e: ApiException) {
            // Код 12501 — пользователь сам закрыл окно выбора аккаунта, это не ошибка
            if (e.statusCode != 12501) vm.googleSignInFailed("Ошибка Google Sign-In (${e.statusCode})")
        }
    }

    OutlinedButton(
        onClick = { launcher.launch(googleClient.signInIntent) },
        modifier = Modifier.fillMaxWidth().height(54.dp),
        enabled = !state.isLoading && state.agreedToTerms,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        border = androidx.compose.foundation.BorderStroke(1.dp, BgTertiary)
    ) {
        Icon(Icons.Default.AccountCircle, null, tint = Accent)
        Spacer(Modifier.width(10.dp))
        Text("Войти через Google", style = MaterialTheme.typography.titleMedium)
    }
}
