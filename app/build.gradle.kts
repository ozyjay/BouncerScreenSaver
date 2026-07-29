plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Keep the semantic part of the version in source control, then append an
// always-increasing build number from CI or git history.
val baseVersionName = providers.gradleProperty("appVersionName").orElse("1.3").get()
val fallbackVersionCode = providers.gradleProperty("appVersionCode").orElse("4").get().toInt()

fun gitCommitCountOrNull(): Int? = try {
    val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    if (process.waitFor() == 0) output.toIntOrNull() else null
} catch (_: Exception) {
    null
}

// GitHub Actions provides a monotonic build number. Local builds fall back to
// commit count so debug artifacts still advance naturally.
val autoVersionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()
    ?: gitCommitCountOrNull()
    ?: fallbackVersionCode
val autoVersionName = "$baseVersionName.$autoVersionCode"

android {
    namespace = "net.crunchycodes.bouncer.live.wallpaper"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "net.crunchycodes.bouncer.live.wallpaper"
        minSdk = 24
        targetSdk = 36
        versionCode = autoVersionCode
        versionName = autoVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
