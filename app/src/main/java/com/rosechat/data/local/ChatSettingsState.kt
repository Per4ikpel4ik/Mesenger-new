package com.rosechat.data.local

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ChatTextSize(val scale: Float, val label: String) {
    SMALL(0.88f, "Мелкий"), MEDIUM(1f, "Обычный"), LARGE(1.18f, "Крупный")
}

object ChatSettingsState {
    private const val PREFS = "pinggram_prefs"
    private const val KEY_ENTER_SEND = "enter_to_send"
    private const val KEY_TEXT_SIZE = "chat_text_size"
    private const val KEY_SHOW_TYPING = "show_typing"
    private const val KEY_SEND_READ_RECEIPTS = "send_read_receipts"
    private const val KEY_ANIMATIONS = "interface_animations"
    private const val KEY_DELETED_UNLOCKED = "deleted_msgs_unlocked"
    private const val KEY_DELETED_SHOWN = "deleted_msgs_shown"
    private const val SECRET_CODE = "read1917"

    var enterToSend by mutableStateOf(true)
        private set
    var textSize by mutableStateOf(ChatTextSize.MEDIUM)
        private set
    var showTyping by mutableStateOf(true)        // показывать ли статус "печатает…" у других
        private set
    var sendReadReceipts by mutableStateOf(true)   // отправлять ли собеседнику отметку о прочтении (галочки)
        private set

    // "Сделай какие-нибудь анимацию в настройках" — плавная анимация появления/сдвига
    // сообщений в списке (см. Modifier.animateItem() в ChatScreen). Настройка на случай,
    // если кому-то приятнее, когда список обновляется мгновенно, без движения.
    var interfaceAnimations by mutableStateOf(true)
        private set

    // Секретный режим чтения удалённых сообщений. Удаление в проекте и так "мягкое" —
    // FirebaseRepo.deleteMessage только помечает isDeleted=true, текст/медиа никуда не
    // пропадают из Firestore (см. чат: "читать удалённые сообщения ... надо отдельно
    // подключать, она секретная"). unlocked — открыт ли переключатель вообще (появляется
    // только после ввода секретного кода в Настройках чата, долгий тап по заголовку экрана);
    // shown — сам переключатель "показывать", можно выключать обратно не вводя код снова —
    // код нужен только один раз, чтобы открыть саму возможность.
    var deletedMessagesUnlocked by mutableStateOf(false)
        private set
    var showDeletedMessages by mutableStateOf(false)
        private set

    // Вызывается один раз в MainActivity.onCreate — как ThemeState/NotificationsState.
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enterToSend = prefs.getBoolean(KEY_ENTER_SEND, true)
        showTyping = prefs.getBoolean(KEY_SHOW_TYPING, true)
        sendReadReceipts = prefs.getBoolean(KEY_SEND_READ_RECEIPTS, true)
        interfaceAnimations = prefs.getBoolean(KEY_ANIMATIONS, true)
        val savedSize = prefs.getString(KEY_TEXT_SIZE, ChatTextSize.MEDIUM.name)
        textSize = ChatTextSize.entries.firstOrNull { it.name == savedSize } ?: ChatTextSize.MEDIUM
        deletedMessagesUnlocked = prefs.getBoolean(KEY_DELETED_UNLOCKED, false)
        showDeletedMessages = prefs.getBoolean(KEY_DELETED_SHOWN, false)
    }

    fun setEnterToSend(context: Context, v: Boolean) {
        enterToSend = v
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENTER_SEND, v).apply()
    }

    fun setShowTyping(context: Context, v: Boolean) {
        showTyping = v
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_TYPING, v).apply()
    }

    fun setSendReadReceipts(context: Context, v: Boolean) {
        sendReadReceipts = v
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SEND_READ_RECEIPTS, v).apply()
    }

    fun setInterfaceAnimations(context: Context, v: Boolean) {
        interfaceAnimations = v
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ANIMATIONS, v).apply()
    }

    fun setTextSize(context: Context, v: ChatTextSize) {
        textSize = v
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TEXT_SIZE, v.name).apply()
    }

    // Код нигде не показывается в самом приложении — известен только тебе. Неверный код
    // просто ничего не делает, без каких-либо подсказок, что код в принципе существует.
    fun tryUnlockDeletedMessages(context: Context, code: String): Boolean {
        if (code != SECRET_CODE) return false
        deletedMessagesUnlocked = true
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DELETED_UNLOCKED, true).apply()
        return true
    }

    fun setShowDeletedMessages(context: Context, v: Boolean) {
        showDeletedMessages = v
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DELETED_SHOWN, v).apply()
    }
}
