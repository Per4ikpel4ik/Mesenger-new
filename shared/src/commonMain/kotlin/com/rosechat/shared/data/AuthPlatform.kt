package com.rosechat.shared.data

// Письмо для сброса пароля («Забыл пароль?» на экране входа). На Android это делает нативный
// FirebaseAuth.sendPasswordResetEmail; а в JVM-версии Firebase-SDK (firebase-java-sdk) эта
// функция не реализована, поэтому на десктопе идём напрямую в Firebase Auth REST API
// (identitytoolkit.googleapis.com) — тот же эффект: на почту приходит ссылка для смены пароля.
expect suspend fun sendPasswordReset(email: String): Result<Unit>
