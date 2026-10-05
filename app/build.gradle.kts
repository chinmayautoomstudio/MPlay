import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release signing comes from an uncommitted keystore.properties (storeFile, storePassword, keyAlias, keyPassword).
// Both editions use the same key, so MPlay AI can replace standard MPlay in place.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use(::load)
}

android {
    namespace = "com.autoomstudio.mplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.autoomstudio.mplay"
        minSdk = 26
        targetSdk = 37
        // Shared by both editions: Android refuses to install a lower version code over a higher one.
        versionCode = 3
        versionName = "3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    flavorDimensions += "edition"
    productFlavors {
        create("standard") {
            dimension = "edition"
        }
        create("ai") {
            dimension = "edition"
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    androidResources {
        // The bundled model is memory-mapped straight from the APK, which only works uncompressed.
        noCompress += "onnx"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    // Debug AI builds install next to standard MPlay; release AI builds replace it.
    onVariants(selector().withFlavor("edition" to "ai").withBuildType("debug")) { variant ->
        variant.applicationId.set("com.autoomstudio.mplay.ai")
    }
    onVariants { variant ->
        val name = variant.name.replaceFirstChar(Char::uppercase)
        val check = tasks.register<CheckNoInternetPermission>("check${name}NoInternet") {
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            report.set(layout.buildDirectory.file("reports/no-internet/${variant.name}.txt"))
        }
        tasks.configureEach {
            if (this.name == "assemble$name" || this.name == "bundle$name") dependsOn(check)
        }
    }
}

/** MPlay is offline by design; fails the build if any dependency merges in the INTERNET permission. */
abstract class CheckNoInternetPermission : DefaultTask() {
    @get:InputFile
    abstract val manifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val text = manifest.get().asFile.readText()
        if (text.contains("android.permission.INTERNET")) {
            throw GradleException("The merged manifest declares android.permission.INTERNET; MPlay must stay offline.")
        }
        report.get().asFile.writeText("OK: no INTERNET permission\n")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.lottie.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    "aiImplementation"(project(":separation"))
    "aiImplementation"(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
