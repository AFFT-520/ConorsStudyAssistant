plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.conor.quizzer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.conor.quizzer"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        viewBinding = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1" // Use the version compatible with your Kotlin version
        // Check official docs for the latest compatible versions
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    // Compose BOM (Bill of Materials) - Recommended for managing versions
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00") // Check for the latest BOM version
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Essential Compose Libraries
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview") // For previews in Android Studio
    implementation("androidx.compose.material3:material3")   // Or material for Material 2

    // Optional - For specific features
    implementation("androidx.activity:activity-compose:1.9.0") // For setContent in Activity (check latest version)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0") // For ViewModel integration (check latest version)

    // UI Tests for Compose
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling") // For UI inspection tools
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3") // Use a recent stable version
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // For Android main dispatcher
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")


    implementation(libs.gson)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.converter.gson)
    implementation(libs.retrofit2.kotlinx.serialization.converter)
    implementation(libs.logging.interceptor)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.autolink)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}