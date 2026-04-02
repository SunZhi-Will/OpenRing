plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// llama-kotlin-android 會拉較新的 androidx.core；與 compileSdk 36 對齊時強制統一 core 版本。
configurations.configureEach {
    resolutionStrategy {
        force("androidx.core:core-ktx:1.18.0")
        force("androidx.core:core:1.18.0")
    }
}

android {
    namespace = "com.openring"
    compileSdk = 36
    // 避免選到損毀的 build-tools（例如 Unity 內建的 35.0.0）；與 compileSdk 36 對齊。
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.openring"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.1.1"
        // llama-kotlin-android / llama.cpp：實機以 arm64 為主；模擬器用 x86_64。
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    lint {
        // 目前 lint 在 `lintVitalAnalyzeRelease` 會因 UAST 初始化/釋放問題崩潰（與程式碼無關），
        // 先避免 release 組建被 lint 工具本身阻擋；日後可透過升級 AGP / lint 修復後再打開。
        checkReleaseBuilds = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Workaround: AGP lint (UAST) crashes on some environments and breaks `./gradlew build`.
// Disable lint analysis tasks for now; re-enable after upgrading AGP/lint.
tasks.matching { it.name.startsWith("lint") }.configureEach {
    enabled = false
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // Room
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // Networking (Gemini Developer API via REST)
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    // QR scan for OpenRing Cloud relay URL (Fleet dashboard QR)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // QuickJS (Skill Plugin Engine)
    // NOTE: `app.cash.quickjs:quickjs-android` 內的 `libquickjs.so` 仍為 4KB LOAD 對齊，
    // 會在 Android 15+ 的 16KB page size 裝置觸發相容模式警告。
    // 改用支援 16KB page size 的 wrapper。
    implementation("wang.harlon.quickjs:wrapper-android:3.2.3")

    // BYOK secure storage (MVP)
    implementation("androidx.security:security-crypto:1.1.0")

    // On-device GGUF inference (text); see https://github.com/CodeShipping/llama-kotlin-android
    implementation("org.codeshipping:llama-kotlin-android:0.1.3")
}
