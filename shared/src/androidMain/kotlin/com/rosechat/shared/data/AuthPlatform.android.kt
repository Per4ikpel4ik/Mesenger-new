package com.rosechat.shared.data

// :app на Android использует свой FirebaseRepo.resetPassword — эта заглушка нужна только
// для компиляции commonMain.
actual suspend fun sendPasswordReset(email: String): Result<Unit> =
    Result.failure(Exception("Не поддерживается на этой платформе"))
