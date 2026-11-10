package com.rosechat.shared.data

import platform.Foundation.NSUserDefaults

private val defaults: NSUserDefaults
    get() = NSUserDefaults.standardUserDefaults

actual fun loadBoolPref(key: String, default: Boolean): Boolean =
    if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

actual fun saveBoolPref(key: String, value: Boolean) {
    defaults.setBool(value, forKey = key)
}

actual fun loadStringPref(key: String, default: String): String = defaults.stringForKey(key) ?: default

actual fun saveStringPref(key: String, value: String) {
    defaults.setObject(value, forKey = key)
}

actual fun loadDarkThemePref(): Boolean = loadBoolPref("dark_theme", true)

actual fun saveDarkThemePref(isDark: Boolean) {
    saveBoolPref("dark_theme", isDark)
}

actual fun loadAccentPref(): String = defaults.stringForKey("accent") ?: "pink"

actual fun saveAccentPref(name: String) {
    defaults.setObject(name, forKey = "accent")
}
