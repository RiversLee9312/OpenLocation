import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.openlocation.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.openlocation.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    val signingFile = rootProject.file(".secrets/release-signing.properties")
    if (signingFile.exists()) {
        val keys = Properties().apply { signingFile.inputStream().use { load(it) } }
        signingConfigs {
            create("privateRelease") {
                storeFile = rootProject.file(keys.getProperty("storeFile"))
                storePassword = keys.getProperty("storePassword")
                keyAlias = keys.getProperty("keyAlias")
                keyPassword = keys.getProperty("keyPassword")
            }
        }
        buildTypes {
            getByName("release") { signingConfig = signingConfigs.getByName("privateRelease") }
            if (providers.gradleProperty("signDebugWithRelease").orNull == "true") {
                getByName("debug") { signingConfig = signingConfigs.getByName("privateRelease") }
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testBuildType = "release"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation(project(":android-runtime"))
    implementation(project(":hook-runtime"))
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.material3:material3:1.5.0-alpha14")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.maplibre.gl:android-sdk:11.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
