plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.autoomstudio.mp3studio.spike"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.autoomstudio.mp3studio.ai.spike"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "spike"
        ndk { abiFilters += "arm64-v8a" }
    }

    androidResources {
        noCompress += "onnx"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":separation"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
}
