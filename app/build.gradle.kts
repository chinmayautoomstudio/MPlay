import com.android.build.api.artifact.SingleArtifact
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release signing comes from an uncommitted keystore.properties (storeFile, storePassword, keyAlias, keyPassword).
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
        versionCode = 4
        versionName = "3.1"
        // An imported model file must match the same export the build bundles.
        val modelHash = rootProject.file("models/htdemucs.onnx.sha256").readText().trim().substringBefore(' ').lowercase()
        buildConfigField("String", "MODEL_SHA256", "\"$modelHash\"")

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

    buildTypes {
        // ABIs that get ONNX Runtime, read by DeviceEligibility. Debug adds x86_64 so the emulator separates natively.
        debug {
            buildConfigField("String", "SEPARATION_ABIS", "\"${separationAbis(debug = true).joinToString(",")}\"")
        }
        release {
            buildConfigField("String", "SEPARATION_ABIS", "\"${separationAbis(debug = false).joinToString(",")}\"")
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
    // The app installs on every ABI, but ONNX Runtime (17 MB per ABI) only ships where separation can run.
    onVariants { variant ->
        val keep = separationAbis(debug = variant.buildType == "debug")
        (ALL_ABIS - keep).forEach { abi -> variant.packaging.jniLibs.excludes.add("lib/$abi/libonnxruntime*.so") }
    }
    // The model stays out of git: it is read from models/, checked against the committed hash and bundled.
    onVariants { variant ->
        val name = variant.name.replaceFirstChar(Char::uppercase)
        val prepare = tasks.register<PrepareModelAssets>("prepare${name}ModelAssets") {
            model.from(rootProject.file("models/htdemucs.onnx"))
            expectedHash.set(rootProject.file("models/htdemucs.onnx.sha256"))
            required.set(variant.buildType == "release")
            outputDir.set(layout.buildDirectory.dir("generated/modelAssets/${variant.name}"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(prepare, PrepareModelAssets::outputDir)
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

val ALL_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

fun separationAbis(debug: Boolean): Set<String> = if (debug) setOf("arm64-v8a", "x86_64") else setOf("arm64-v8a")

/**
 * Copies the separation model into the variant's assets after checking its SHA-256. Release builds fail without a
 * matching model; debug builds only warn, and run without one until a model file is imported in the app.
 */
abstract class PrepareModelAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val model: ConfigurableFileCollection

    @get:InputFile
    abstract val expectedHash: RegularFileProperty

    @get:Input
    abstract val required: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun prepare() {
        val out = outputDir.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val expected = expectedHash.get().asFile.readText().trim().substringBefore(' ').lowercase()
        val file = model.files.firstOrNull { it.isFile }
        val problem = when {
            file == null -> "models/htdemucs.onnx is missing; run tools/export_htdemucs.py --out models/htdemucs.onnx --fp16"
            sha256(file) != expected -> "models/htdemucs.onnx does not match models/htdemucs.onnx.sha256"
            else -> null
        }
        if (problem != null) {
            if (required.get()) throw GradleException(problem)
            logger.warn("Warning: $problem. This build has no separation model.")
            return
        }
        file!!.copyTo(File(out, file.name))
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
    implementation(project(":separation"))
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
