package com.rosechat.shared.data

// Реализация: десктоп (GoogleSignIn.desktop.kt, через Vercel) и iPhone (GoogleSignIn.ios.kt, через ASWebAuthenticationSession).
// браузер на страницу входа Google, ловит ответ локальным сервером на 127.0.0.1,
// меняет код на токены и логинится в Firebase тем же способом, что и Android —
// просто без нативного Google Play Services SDK, через чистый OAuth.
expect suspend fun googleSignIn(): Result<String>
