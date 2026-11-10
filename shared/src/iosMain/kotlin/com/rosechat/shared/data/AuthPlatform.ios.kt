package com.rosechat.shared.data

// Для iPhone сделаем позже (вместе с остальным Этапом для iOS).
actual suspend fun sendPasswordReset(email: String): Result<Unit> =
    Result.failure(Exception("Пока не поддерживается на iPhone"))
