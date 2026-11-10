package com.rosechat.shared.data

// На Android это no-op — приложение :app само инициализирует Firebase через
// google-services плагин + AndroidManifest, как и раньше, ещё до того как
// какой-либо код :shared вообще может быть вызван.
//
// На Desktop это делает реальную работу: FirebasePlatform.initializeFirebasePlatform(...)
// + Firebase.initialize(...) — без этого вызова ЛЮБОЕ обращение к Firebase.auth /
// Firebase.firestore на Windows упадёт с исключением ещё до сетевого запроса.
expect fun ensureFirebaseInitialized()
