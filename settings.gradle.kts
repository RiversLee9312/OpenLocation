pluginManagement {
    repositories {
        maven { url = uri(".gradle-home/maven-cache") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy.eachPlugin {
        when (requested.id.id) {
            "com.android.application",
            "com.android.library" ->
                useModule("com.android.tools.build:gradle:${requested.version}")
            "org.jetbrains.kotlin.android",
            "org.jetbrains.kotlin.jvm" ->
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            "org.jetbrains.kotlin.plugin.serialization" ->
                useModule("org.jetbrains.kotlin:kotlin-serialization:${requested.version}")
            "org.jetbrains.kotlin.plugin.compose" ->
                useModule(
                    "org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version}"
                )
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri(".gradle-home/maven-cache") }
        google()
        mavenCentral()
        maven("https://api.xposed.info/")
    }
}

rootProject.name = "OpenLocation"

include(
    ":app",
    ":scenario-format",
    ":simulation-core",
    ":android-runtime",
    ":hook-runtime",
    ":probe-app",
)
