import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget()
    jvm("desktop")

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)

                // Firebase Kotlin SDK (GitLive) — единственная реализация Firebase,
                // у которой common-код реально работает и на Android, и на JVM/Desktop
                // (через firebase-java-sdk, подтягивается транзитивно).
                implementation("dev.gitlive:firebase-auth:2.1.0")
                implementation("dev.gitlive:firebase-firestore:2.1.0")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
            }
        }
        val androidMain by getting {
            dependencies {
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
            }
        }
    }
}

android {
    namespace = "com.rosechat.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
}

// GitLive транзитивно тянет kotlinx-serialization новее, чем понимает Kotlin 2.0.21
// нашего проекта (несовместимость .kotlin_module метаданных: 2.2.0 vs ожидаемых 2.0.0).
// Жёстко прибиваем версию, которая точно совместима — Firestore/Auth от этого не пострадают,
// используемый нами API стабилен и там, и там.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.7.1")
        force("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    }
}

// Упаковка Windows .exe/.msi — запускается на его машине командой
// `./gradlew.bat :shared:packageReleaseExe` (или .../packageReleaseMsi), Gradle сам
// зовёт jpackage. ВАЖНО: у него уже случалась ошибка "Failed to check JDK distribution:
// 'jpackage.exe' is missing" — это значит, что Gradle взял JBR (JetBrains Runtime),
// вшитый в Android Studio, а тот конкретный сборкой урезан и не содержит jpackage.
// Настоящий фикс — на его стороне: поставить полноценный JDK 17+ (например Temurin с
// adoptium.net) и один раз прописать в gradle.properties (в корне проекта, НЕ здесь)
// строку вида org.gradle.java.home=C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.x-hotspot
// — тогда именно jpackage-задача возьмёт этот JDK, а не JBR из Android Studio.
compose.desktop {
    application {
        mainClass = "com.rosechat.shared.MainKt"
        // ProGuard в release-сборке падает на сотнях Android-классов, которые тянутся
        // транзитивно через firebase-java-sdk (proguardReleaseJars → "unresolved references").
        // Для десктопного .exe сжатие кода не нужно — выключаем, тогда release-таски работают.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
        nativeDistributions {
            includeAllModules = true
            targetFormats(TargetFormat.Exe)
            packageName = "PingGram"
            packageVersion = "1.6.0"
            windows {
                menuGroup = "PingGram"
                upgradeUuid = "8C21B1D6-9F2A-4E77-9B1E-4E0B2B9B8C11"
                iconFile.set(project.file("icons/pinggram.ico"))
            }
        }
    }
}
