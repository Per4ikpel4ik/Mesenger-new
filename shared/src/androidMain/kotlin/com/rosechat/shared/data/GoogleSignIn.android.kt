package com.rosechat.shared.data

// Android-приложение (:app) пока использует свой собственный нативный Google Sign-In,
// этот файл существует только чтобы commonMain компилировался и на androidTarget тоже.
actual suspend fun googleSignIn(): Result<String> =
    Result.failure(UnsupportedOperationException("На Android используется нативный Google Sign-In из :app"))
