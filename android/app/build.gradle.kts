plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.beammental.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.beammental.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 14
        versionName = "1.8.1"
        // Backend URL can be overridden by CI via BEAM_SERVER_URL env var.
        // vars.BEAM_SERVER_URL in Actions resolves to "" when unset, which
        // counts as a present env var — fall back to prod when blank.
        buildConfigField(
            "String",
            "BEAM_URL",
            "\"${providers.environmentVariable("BEAM_SERVER_URL")
                .getOrElse("https://beam-mental-health.vercel.app")
                .trim()
                .ifEmpty { "https://beam-mental-health.vercel.app" }}\""
        )
        // Gemini Live API key, injected by CI via the BEAM_GOOGLE_KEY variable.
        // Blank for local builds — VoiceScreen shows a notice instead of crashing.
        buildConfigField(
            "String",
            "BEAM_GOOGLE_KEY",
            "\"${providers.environmentVariable("BEAM_GOOGLE_KEY").getOrElse("").trim()}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            // CI-friendly: installable without repo secrets.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // lintVital breaks on Windows paths with this project layout; CI builds release anyway
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
