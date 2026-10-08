import com.android.build.api.artifact.SingleArtifact
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release signing comes from an uncommitted keystore.properties (storeFile, storePassword, keyAlias, keyPassword).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use(::load)
}

// Backend settings come from the uncommitted local.properties (supabase.url, supabase.key, google.webClientId).
// Only publishable values belong there; the service role key stays on the server.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}
val backendConfig = mapOf(
    "SUPABASE_URL" to localProperties.getProperty("supabase.url", "").trim(),
    "SUPABASE_KEY" to localProperties.getProperty("supabase.key", "").trim(),
    "GOOGLE_WEB_CLIENT_ID" to localProperties.getProperty("google.webClientId", "").trim(),
)

android {
    namespace = "com.autoomstudio.mp3studio"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.autoomstudio.mp3studio"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "3.1"
        // An imported model file must match the same export the build bundles.
        val modelHash = rootProject.file("models/htdemucs.onnx.sha256").readText().trim().substringBefore(' ').lowercase()
        buildConfigField("String", "MODEL_SHA256", "\"$modelHash\"")
        backendConfig.forEach { (name, value) -> buildConfigField("String", name, "\"$value\"") }

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
        val permissions = tasks.register<CheckAllowedPermissions>("check${name}Permissions") {
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowed.set(ALLOWED_PERMISSIONS + "${variant.applicationId.get()}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
            report.set(layout.buildDirectory.file("reports/permissions/${variant.name}.txt"))
        }
        val backend = tasks.register<CheckBackendConfig>("check${name}BackendConfig") {
            values.set(backendConfig)
            required.set(variant.buildType == "release")
            report.set(layout.buildDirectory.file("reports/backend-config/${variant.name}.txt"))
        }
        tasks.configureEach {
            if (this.name == "assemble$name" || this.name == "bundle$name") dependsOn(permissions, backend)
        }
    }
}

/** Every permission the merged manifest may declare. Anything else (usually merged in by a library) fails the build. */
val ALLOWED_PERMISSIONS = setOf(
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
    "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
    "android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING",
    "android.permission.FOREGROUND_SERVICE_MICROPHONE",
    "android.permission.RECORD_AUDIO",
    "android.permission.WAKE_LOCK",
    "android.permission.WRITE_SETTINGS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
)

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

/** Fails the build when the merged manifest declares a permission outside [allowed]. */
abstract class CheckAllowedPermissions : DefaultTask() {
    @get:InputFile
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val allowed: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val declared = Regex("""<uses-permission(?:-sdk-23)?\s[^>]*android:name="([^"]+)"""")
            .findAll(manifest.get().asFile.readText())
            .map { it.groupValues[1] }
            .toSortedSet()
        val unexpected = declared - allowed.get()
        if (unexpected.isNotEmpty()) {
            throw GradleException(
                "The merged manifest declares permissions that aren't allowed: ${unexpected.joinToString()}. " +
                    "Remove them with tools:node=\"remove\" or add them to ALLOWED_PERMISSIONS in app/build.gradle.kts.",
            )
        }
        report.get().asFile.writeText(declared.joinToString("\n", postfix = "\n"))
    }
}

/** Release builds need the Supabase URL and key and the Google Web client ID; debug builds only warn. */
abstract class CheckBackendConfig : DefaultTask() {
    @get:Input
    abstract val values: MapProperty<String, String>

    @get:Input
    abstract val required: Property<Boolean>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val missing = values.get().filterValues { it.isBlank() }.keys
        if (missing.isNotEmpty()) {
            val problem = "Missing backend settings in local.properties for ${missing.joinToString()} " +
                "(supabase.url, supabase.key, google.webClientId). Sign-in won't work."
            if (required.get()) throw GradleException(problem)
            logger.warn("Warning: $problem")
        }
        report.get().asFile.writeText(if (missing.isEmpty()) "OK\n" else "Missing: ${missing.joinToString()}\n")
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
    implementation(libs.coil.network.okhttp)
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
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.tink.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
