import java.util.Properties
import java.io.FileInputStream
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// 直接使用绝对路径读取 release.properties，绕过 AndroidIDE 的路径解析 bug
val releaseProperties = Properties()
val releasePropertiesFile = File("/storage/emulated/0/AndroidIDEProjects/Dock/release.properties")
if (releasePropertiesFile.exists()) {
    releaseProperties.load(FileInputStream(releasePropertiesFile))
} else {
    println("⚠️ 警告: 找不到 release.properties 文件，请检查路径！")
}

android {
    namespace = "com.apple.dock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.apple.dock"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
        }
    }
    
    lint {
        checkReleaseBuilds = false
    }

    signingConfigs {
        create("release") {
            // 强制拼接绝对路径
            val keyName = releaseProperties.getProperty("RELEASE_STORE_FILE") ?: "SawaKawa.jks"
            storeFile = File("/storage/emulated/0/AndroidIDEProjects/Dock/$keyName")
            
            storePassword = releaseProperties.getProperty("RELEASE_STORE_PASSWORD")
            keyAlias = releaseProperties.getProperty("RELEASE_KEY_ALIAS")
            keyPassword = releaseProperties.getProperty("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.17.0")
        force("androidx.core:core-ktx:1.17.0")
        force("com.google.android.material:material:1.12.0")
        force("androidx.appcompat:appcompat:1.7.0")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        force("androidx.activity:activity:1.9.3")
        force("androidx.activity:activity-ktx:1.9.3")
        force("androidx.activity:activity-compose:1.9.3")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation(libs.coroutines.android)

    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("io.github.kyant0:shapes:1.2.0")
}