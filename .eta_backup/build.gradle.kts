plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.apple.dock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.apple.dock"
        minSdk = 24 // 必须是 24，避免依赖冲突
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true // 不需要 viewBinding 了，全用 Compose
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
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.17.0")
        force("androidx.core:core-ktx:1.17.0")
        force("com.google.android.material:material:1.12.0")
        force("androidx.appcompat:appcompat:1.7.0")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
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
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    
    // 核心：解决 Service 里 Compose 崩溃
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation(libs.coroutines.android)

    // 液态玻璃底层库
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("io.github.kyant0:shapes:1.2.0")
}