plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    // Нужен только чтобы поймать причину нативного (не-Kotlin) краша в звонках —
    // такой крашрепортер (в отличие от try/catch) ловит и падения внутри самой
    // библиотеки WebRTC. Бесплатно на любом плане Firebase, Blaze не требует.
    id("com.google.firebase.crashlytics") version "3.0.7" apply false

    // ── Windows-клиент (:shared) ──
    // Kotlin Multiplatform + Compose Multiplatform — общий модуль с бизнес-логикой
    // и UI, который компилируется и под Android (пока не подключён к :app), и под
    // десктоп (реальный Windows .exe). com.android.library нужен, потому что KMP-модуль
    // с androidTarget() технически собирается как Android-библиотека.
    id("com.android.library") version "8.7.0" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.compose") version "1.7.0" apply false
}
