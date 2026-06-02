import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Para este build pessoal, permite embutir chaves padrão vindas de local.properties.
// Se o app for distribuído publicamente no futuro, este mecanismo deve ser removido.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val defaultGroqKey: String = localProps.getProperty("groq.api.key", "")
val defaultGeminiKey: String = localProps.getProperty("gemini.api.key.primary", "")
val defaultGeminiSecondaryKey: String = localProps.getProperty("gemini.api.key.secondary", "")

android {
    namespace = "com.aikeyboard.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aikeyboard.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2026051902
        versionName = "1.1.1-turbo"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "GROQ_DEFAULT_API_KEY", "\"$defaultGroqKey\"")
        buildConfigField("String", "GEMINI_DEFAULT_API_KEY", "\"$defaultGeminiKey\"")
        buildConfigField("String", "GEMINI_SECONDARY_DEFAULT_API_KEY", "\"$defaultGeminiSecondaryKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        create("optimizedDebug") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-optimized"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release", "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Networking + serialization
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Storage
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)

    // Tests
    testImplementation("junit:junit:4.13.2")
}
