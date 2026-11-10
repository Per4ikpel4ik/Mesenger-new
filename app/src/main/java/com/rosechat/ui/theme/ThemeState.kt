package com.rosechat.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ThemeState {
    private const val PREFS = "pinggram_prefs"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_ACCENT = "accent_theme"

    var isDark by mutableStateOf(true)
        private set
    var accentTheme by mutableStateOf(AccentTheme.PINK)
        private set

    // Вызывается один раз в MainActivity.onCreate, до setContent — SharedPreferences
    // читаются синхронно и мгновенно, так что никакого "мигания" темой при старте нет.
    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        isDark = prefs.getBoolean(KEY_DARK, true)
        accentTheme = AccentTheme.fromId(prefs.getString(KEY_ACCENT, AccentTheme.PINK.name) ?: AccentTheme.PINK.name)
    }

    fun setDark(context: Context, dark: Boolean) {
        isDark = dark
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, dark).apply()
    }

    // Цвет темы — локальная настройка устройства (как isDark), не поле User в Firestore:
    // это оформление интерфейса, а не то, что должно синхронизироваться между устройствами.
    fun setAccent(context: Context, theme: AccentTheme) {
        accentTheme = theme
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACCENT, theme.name).apply()
    }
}
