import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.teyvat.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.teyvat.launcher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    val keystoreProps =
        Properties().apply {
            val f = rootProject.file("keystore.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
    signingConfigs {
        if (keystoreProps.containsKey("storeFile")) {
            getByName("debug") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
        if (keystoreProps.containsKey("releaseStoreFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("releaseStoreFile"))
                storePassword = keystoreProps.getProperty("releaseStorePassword")
                keyAlias = keystoreProps.getProperty("releaseKeyAlias")
                keyPassword = keystoreProps.getProperty("releaseKeyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
}
