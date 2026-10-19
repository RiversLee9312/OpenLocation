plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "dev.openlocation.runtime"
    compileSdk = 36
    defaultConfig { minSdk = 34 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    api(project(":simulation-core"))
    implementation("androidx.core:core-ktx:1.16.0")
}
