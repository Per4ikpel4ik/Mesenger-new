package com.rosechat.data.local

import android.content.Context

// Запоминаем на устройстве, что пользователь уже принял соглашение —
// чтобы не спрашивать заново при каждом входе через телефон/Google
// (для email-регистрации это не нужно, там аккаунт создаётся один раз).
object TermsPrefs {
    private const val PREFS = "pinggram_prefs"
    private const val KEY_TERMS = "terms_accepted"

    fun isAccepted(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_TERMS, false)

    fun setAccepted(context: Context, accepted: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_TERMS, accepted).apply()
    }
}
