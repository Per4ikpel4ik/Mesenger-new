package com.rosechat.shared.data

actual fun loadDarkThemePref(): Boolean = true
actual fun saveDarkThemePref(isDark: Boolean) {}

actual fun loadAccentPref(): String = "pink"
actual fun saveAccentPref(name: String) {}

actual fun loadBoolPref(key: String, default: Boolean): Boolean = default
actual fun saveBoolPref(key: String, value: Boolean) {}

actual fun loadStringPref(key: String, default: String): String = default
actual fun saveStringPref(key: String, value: String) {}
