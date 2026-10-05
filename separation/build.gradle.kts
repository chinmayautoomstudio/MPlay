plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.autoomstudio.mplay.separation"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.onnxruntime.android)
    testImplementation(libs.junit)
}
