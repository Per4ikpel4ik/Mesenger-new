package com.rosechat.ui.chatsettings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rosechat.data.local.ChatSettingsState
import com.rosechat.data.local.ChatTextSize
import com.rosechat.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    // Секретный код (см. ChatSettingsState.tryUnlockDeletedMessages) открывается долгим тапом
    // по заголовку экрана — ничем внешне не обозначен, поэтому и "секретный" (см. чат:
    // "она секретная и её надо отдельно подключать").
    var showSecretDialog by remember { mutableStateOf(false) }
    var secretInput by remember { mutableStateOf("") }
    var secretWrong by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Настройки чатов", color = TextPrimary,
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { if (!ChatSettingsState.deletedMessagesUnlocked) { secretInput = ""; secretWrong = false; showSecretDialog = true } }
                        )
                    )
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgSecondary)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {

            SettingSwitchRow(
                icon = Icons.Default.Keyboard, title = "Enter отправляет сообщение",
                subtitle = if (ChatSettingsState.enterToSend) "Включено — Enter сразу отправляет" else "Выключено — Enter добавляет новую строку",
                checked = ChatSettingsState.enterToSend, onCheckedChange = { ChatSettingsState.setEnterToSend(ctx, it) }
            )

            Spacer(Modifier.height(12.dp))
            SettingSwitchRow(
                icon = Icons.Default.EditNote, title = "Статус \"печатает…\"",
                subtitle = if (ChatSettingsState.showTyping) "Собеседник видит, когда ты печатаешь" else "Выключено — никто не увидит, что ты печатаешь",
                checked = ChatSettingsState.showTyping, onCheckedChange = { ChatSettingsState.setShowTyping(ctx, it) }
            )

            Spacer(Modifier.height(12.dp))
            SettingSwitchRow(
                icon = Icons.Default.DoneAll, title = "Отметки о прочтении",
                subtitle = if (ChatSettingsState.sendReadReceipts) "Собеседник видит галочки ✓✓ у своих сообщений" else "Выключено — собеседник не узнает, что ты прочитал",
                checked = ChatSettingsState.sendReadReceipts, onCheckedChange = { ChatSettingsState.setSendReadReceipts(ctx, it) }
            )

            // Появляется только после ввода секретного кода — до этого секции просто нет,
            // ничто в обычном интерфейсе не намекает на её существование.
            if (ChatSettingsState.deletedMessagesUnlocked) {
                Spacer(Modifier.height(12.dp))
                SettingSwitchRow(
                    icon = Icons.Default.VisibilityOff, title = "Показывать удалённые сообщения",
                    subtitle = if (ChatSettingsState.showDeletedMessages) "Видны тебе одному, помечены как удалённые" else "Выключено",
                    checked = ChatSettingsState.showDeletedMessages, onCheckedChange = { ChatSettingsState.setShowDeletedMessages(ctx, it) }
                )
            }

            Spacer(Modifier.height(12.dp))
            SettingSwitchRow(
                icon = Icons.Default.AutoAwesome, title = "Анимации в чате",
                subtitle = if (ChatSettingsState.interfaceAnimations) "Сообщения плавно появляются и сдвигаются" else "Выключено — список обновляется мгновенно",
                checked = ChatSettingsState.interfaceAnimations, onCheckedChange = { ChatSettingsState.setInterfaceAnimations(ctx, it) }
            )

            Spacer(Modifier.height(24.dp))
            Text("РАЗМЕР ТЕКСТА В ЧАТЕ", style = MaterialTheme.typography.labelMedium, color = TextTertiary, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TextFields, null, tint = Accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Пример: Привет! Как дела?", color = TextPrimary,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * ChatSettingsState.textSize.scale)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ChatTextSize.entries.forEach { size ->
                            val selected = ChatSettingsState.textSize == size
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) Accent else BgTertiary,
                                modifier = Modifier.weight(1f).clickable { ChatSettingsState.setTextSize(ctx, size) }
                            ) {
                                Text(
                                    size.label, color = if (selected) androidx.compose.ui.graphics.Color.White else TextSecondary,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSecretDialog) {
        AlertDialog(
            onDismissRequest = { showSecretDialog = false },
            containerColor = BgSecondary,
            title = { Text("Код", color = TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = secretInput, onValueChange = { secretInput = it; secretWrong = false },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Accent, unfocusedBorderColor = BgTertiary, cursorColor = Accent)
                    )
                    if (secretWrong) Text("Неверно", color = ErrorRed, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (ChatSettingsState.tryUnlockDeletedMessages(ctx, secretInput)) showSecretDialog = false
                    else secretWrong = true
                }) { Text("ОК", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { showSecretDialog = false }) { Text("Отмена", color = TextSecondary) } }
        )
    }
}

@Composable
private fun SettingSwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(BgTertiary), Alignment.Center) {
                Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = Accent))
        }
    }
}
