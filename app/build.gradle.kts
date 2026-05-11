plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.biasharaai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.biasharaai"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // LiteRT-LM 0.11.0 ships with Kotlin metadata 2.3.0 while our compiler is on the
        // 2.2.x line. The class layout is still readable, but the metadata version guard
        // refuses to load it by default. This flag relaxes the guard.
        freeCompilerArgs += "-Xskip-metadata-version-check"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    val material = "1.12.0"
    val coroutines = "1.9.0"
    val lifecycle = "2.8.7"
    val room = "2.8.4"
    val navigation = "2.8.5"
    val hilt = "2.57.2"
    val camerax = "1.4.1"

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:$material")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle")

    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("androidx.navigation:navigation-fragment-ktx:$navigation")
    implementation("androidx.navigation:navigation-ui-ktx:$navigation")

    implementation("com.google.dagger:hilt-android:$hilt")
    ksp("com.google.dagger:hilt-compiler:$hilt")

    // LiteRT-LM — same on-device LLM runtime used by Google AI Edge Gallery.
    // Replaces the older MediaPipe tasks-genai engine; required for `.litertlm` model files
    // (e.g. gemma-4-E2B-it.litertlm). The runtime applies the chat template internally, so the
    // host app must pass plain user text and let the engine handle the start/end-of-turn markers.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")

    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Phase 2 — Prompt U0 (see HANDOFF.md)
    // ML Kit Text Recognition (Receipt OCR — F2)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // WorkManager (Loss Prevention Alerts — F6)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ESC/POS Bluetooth thermal printer (MIT) — POS / receipt printing (see docs/POS_DESIGN_v1.0.md)
    implementation("com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0")
    // Room migration testing (U9): already `androidx.room:room-testing` in instrumented block below (2.6.1)
    // Turbine (Flow testing): already `app.cash.turbine:turbine:1.2.0` in unit + instrumented blocks (≥ 1.1.0)

    // ── Unit tests ───────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("app.cash.turbine:turbine:1.2.0")

    // ── Instrumented tests ──────────────────────────────────────────────
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.room:room-testing:$room")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines")
    androidTestImplementation("app.cash.turbine:turbine:1.2.0")
}
