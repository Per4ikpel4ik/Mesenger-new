import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

// GIF-ключ (Klipy) читается из local.properties, а не хранится в коде —
// так он не попадёт в git и не засветится в логах/чате. Впиши свой ключ
// строкой `GIF_API_KEY=твой_ключ` в local.properties (рядом с sdk.dir).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val gifApiKey: String = (localProps.getProperty("GIF_API_KEY") ?: "").also {
    if (it.isEmpty()) logger.warn("GIF_API_KEY не задан в local.properties — фича GIF будет отключена в рантайме")
}

// Хранилище фото/видео/файлов (Cloudinary) — тот же принцип, что и с GIF-ключом выше.
// Unsigned upload preset не требует API secret на клиенте, поэтому тут только облачное
// имя и имя пресета. Без них фото/видео/файлы/аватар просто продолжат идти как раньше —
// Base64 прямо в документе Firestore (см. FirebaseRepo/ChatViewModel).
val cloudinaryCloudName: String = localProps.getProperty("CLOUDINARY_CLOUD_NAME") ?: ""
val cloudinaryUploadPreset: String = localProps.getProperty("CLOUDINARY_UPLOAD_PRESET") ?: ""
if (cloudinaryCloudName.isEmpty() || cloudinaryUploadPreset.isEmpty()) {
    logger.warn("CLOUDINARY_CLOUD_NAME/CLOUDINARY_UPLOAD_PRESET не заданы — фото/видео/файлы продолжат идти через Base64 в Firestore")
}

// Личный TURN-логин/пароль для звонков (Open Relay Project через metered.ca) — см.
// local.properties. Без них звонки продолжат использовать общие статические данные,
// как раньше.
val turnUsername: String = localProps.getProperty("TURN_USERNAME") ?: ""
val turnPassword: String = localProps.getProperty("TURN_PASSWORD") ?: ""
if (turnUsername.isEmpty() || turnPassword.isEmpty()) {
    logger.warn("TURN_USERNAME/TURN_PASSWORD не заданы — звонки продолжат использовать общие статические TURN-данные")
}

android {
    namespace = "com.rosechat"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.rosechat"
        minSdk = 26; targetSdk = 35
        versionCode = 69; versionName = "1.1.6.23"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GIF_API_KEY", "\"$gifApiKey\"")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"$cloudinaryCloudName\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"$cloudinaryUploadPreset\"")
        buildConfigField("String", "TURN_USERNAME", "\"$turnUsername\"")
        buildConfigField("String", "TURN_PASSWORD", "\"$turnPassword\"")
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom); androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    val firebaseBom = platform("com.google.firebase:firebase-bom:33.4.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    // Ловит именно нативные крэши (внутри .so-библиотеки WebRTC) — обычный try/catch
    // их не видит в принципе, отчёт уходит в Firebase Console → Crashlytics.
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-crashlytics-ndk")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")  // анимированные GIF (для API < 28, на 28+ Coil умеет сам через ImageDecoder)
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")
    // Реальные звонки (аудио/видео). Открытый форк GetStream — гугловский .aar webrtc
    // больше не публикуется в Maven Central. Сама библиотека бесплатна и не требует
    // Blaze/Storage — сигналинг у нас идёт через обычный Firestore (см. FirebaseRepo).
    implementation("io.getstream:stream-webrtc-android:1.3.9")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
