package com.rosechat.shared.data

import java.util.prefs.Preferences

private val prefs: Preferences by lazy { Preferences.userRoot().node("com.rosechat.pinggram") }

actual fun loadDarkThemePref(): Boolean = prefs.getBoolean("dark_theme", true)
actual fun saveDarkThemePref(isDark: Boolean) {
    prefs.putBoolean("dark_theme", isDark)
}

actual fun loadAccentPref(): String = prefs.get("accent_theme", "pink")
actual fun saveAccentPref(name: String) {
    prefs.put("accent_theme", name)
}

actual fun loadBoolPref(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
actual fun saveBoolPref(key: String, value: Boolean) { prefs.putBoolean(key, value) }
