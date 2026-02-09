import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Load local.properties manually — findProperty() doesn't read custom keys from it
val localProps = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.kickstream"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kickstream.app"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "KICK_CLIENT_ID", "\"${localProps.getProperty("kick.client.id", "")}\"")
        buildConfigField("String", "KICK_CLIENT_SECRET", "\"${localProps.getProperty("kick.client.secret", "")}\"")
        buildConfigField("String", "KICK_REDIRECT_URI", "\"${localProps.getProperty("kick.redirect.uri", "http://127.0.0.1:8374/callback")}\"")
        buildConfigField("String", "KICK_EMULATOR_HOST", "\"${localProps.getProperty("kick.emulator.host", "")}\"")
    }

    signingConfigs {
        create("release") {
            val ksPath = localProps.getProperty("keystore.path", "")
            if (ksPath.isNotEmpty()) {
                storeFile = rootProject.file(ksPath)
                storePassword = localProps.getProperty("keystore.password", "")
                keyAlias = localProps.getProperty("keystore.alias", "")
                keyPassword = localProps.getProperty("keystore.alias.password", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.tv.material)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.pusher.java.client)

    implementation(libs.datastore.preferences)

    implementation(libs.coroutines.android)

    implementation(libs.zxing.core)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    implementation(libs.splashscreen)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
