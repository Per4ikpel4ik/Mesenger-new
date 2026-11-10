package com.rosechat.shared.data

// Запоминает выбор тёмная/светлая тема и акцентный цвет между запусками приложения.
// На Android не используется (там своя тема) — заглушка нужна только чтобы commonMain
// компилировался.
expect fun loadDarkThemePref(): Boolean
expect fun saveDarkThemePref(isDark: Boolean)

// name — один из ключей AccentOptions в App.kt ("pink", "blue", ...); хранится строкой,
// а не цветом, чтобы при желании поменять сами оттенки в App.kt не пришлось трогать
// сохранённые настройки людей.
expect fun loadAccentPref(): String
expect fun saveAccentPref(name: String)

expect fun loadBoolPref(key: String, default: Boolean): Boolean
expect fun saveBoolPref(key: String, value: Boolean)

// Строковые настройки (например, размер текста в чате: SMALL / MEDIUM / LARGE).
expect fun loadStringPref(key: String, default: String): String
expect fun saveStringPref(key: String, value: String)
