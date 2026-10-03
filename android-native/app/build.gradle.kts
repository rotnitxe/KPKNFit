import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Where `generateFoodDataManifest` writes the assets it generates (WP-S10); registered below as an assets source of the main source set.
val foodDataManifestDir = layout.buildDirectory.dir("generated/foodDataManifest")

android {
    namespace = "com.example.kpkn"
    // Revertimos a 36 porque las librerías actuales lo exigen
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.kpkn"
        minSdk = 24
        targetSdk = 35
        versionCode = 34
        versionName = "KPKN Beta 15"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "healthConnect"
    productFlavors {
        create("base") {
            dimension = "healthConnect"
        }
        create("health") {
            dimension = "healthConnect"
            minSdk = 26
        }
    }

    val releaseSigningProperties = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.isFile }
            ?.inputStream()?.use { load(it) }
    }
    val releaseStoreFile = releaseSigningProperties.getProperty("storeFile")
        ?.let { rootProject.file(it) }
        ?.takeIf { it.isFile }
    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseSigningProperties.getProperty("storePassword")
            keyAlias = releaseSigningProperties.getProperty("keyAlias")
            keyPassword = releaseSigningProperties.getProperty("keyPassword")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                // El release solo apunta a teléfonos (arm64 + 32-bit legacy);
                // x86/x86_64 quedan fuera: APK más chico y menos footprint en disco.
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
        debug {
            // Misma firma que release cuando hay keystore local, para no pelear
            // con una APK ya instalada. Sin keystore.properties, firma debug de Android.
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // Esquemas Room exportados como assets para MigrationTestHelper (androidTest + JVM)
    sourceSets {
        // The manifest of the catalog CSV (generateFoodDataManifest, WP-S10) is an asset of every variant: food_data/manifest.json.
        getByName("main").assets.srcDir(foodDataManifestDir.get().asFile)
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        getByName("test").assets.srcDir("$projectDir/schemas")
        maybeCreate("testBaseDebug").assets.srcDir("$projectDir/schemas")
        maybeCreate("testBase").assets.srcDir("$projectDir/schemas")
        maybeCreate("testDebug").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

/**
 * Writes `food_data/manifest.json` (WP-S10): the SHA-256 of every catalog CSV and one fingerprint over them,
 * `{"version":1,"files":{"<name>":{"sha256":"...","bytes":N}},"fingerprint":"<sha256 of the lines "<name>:<sha256>" joined by \n>"}`
 * with the files sorted by name. The app reads this small file (< 1 KB) at start (FoodImporter.expectedFingerprint) instead of hashing ~72 MB of
 * CSV, and a CSV that changes imports the catalog again without anybody bumping DATA_VERSION.
 */
abstract class GenerateFoodDataManifest : DefaultTask() {
    /** The catalog tables the importer reads (the CSV files of src/main/assets/food_data): only their names and contents matter. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val csvFiles: ConfigurableFileCollection

    /** Root of the generated assets: the manifest lands in `<outputDir>/food_data/manifest.json`. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val files = csvFiles.files.filter { it.isFile && it.extension == "csv" }.sortedBy { it.name }
        if (files.isEmpty()) logger.warn("No CSV under src/main/assets/food_data: the manifest is empty")
        val hashes = files.associate { it.name to sha256(it.inputStream()) }
        val fingerprint = sha256(files.joinToString("\n") { "${it.name}:${hashes.getValue(it.name)}" }.byteInputStream(Charsets.UTF_8))
        val manifest = buildString {
            append("{\"version\":1,\"files\":{")
            append(files.joinToString(",") { "\"${it.name}\":{\"sha256\":\"${hashes.getValue(it.name)}\",\"bytes\":${it.length()}}" })
            append("},\"fingerprint\":\"$fingerprint\"}")
        }
        val manifestFile = outputDir.get().asFile.resolve("food_data/manifest.json")
        manifestFile.parentFile.deleteRecursively()
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(manifest, Charsets.UTF_8)
    }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

val generateFoodDataManifest by tasks.registering(GenerateFoodDataManifest::class) {
    group = "build"
    description = "Writes food_data/manifest.json: the SHA-256 of every catalog CSV, the fingerprint of the import gate."
    csvFiles.from(fileTree("src/main/assets/food_data") { include("*.csv") })
    outputDir.set(foodDataManifestDir)
}

// The importer's start gate reads that asset: it is built before anything else (preBuild), and the asset merges that read the directory
// depend on it explicitly, so that Gradle never finds a task reading the output of one it does not wait for.
tasks.configureEach {
    if (name == "preBuild" || (name.startsWith("merge") && name.endsWith("Assets"))) {
        dependsOn(generateFoodDataManifest)
    }
}

val verifyDatasetKnowledge by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies that the compiled nutrition dataset matches its master JSON."
    workingDir(rootProject.projectDir)
    // On Windows the interpreter is `python` (no `python3` alias); elsewhere `python3`.
    val pythonCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "python" else "python3"
    commandLine(pythonCommand, "scripts/process_dataset.py", "--check")
    // The master JSON is an authoring input: a checkout that does not carry it has nothing to verify, so the task
    // is skipped instead of failing `check`.
    val masterDataset = rootProject.file("DATASET_KPKN_TRINIDAD_MASTER.json")
    onlyIf { masterDataset.isFile }
}

tasks.named("check").configure {
    dependsOn(verifyDatasetKnowledge)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.haze)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation("com.google.android.gms:play-services-location:21.4.0")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.camera:camera-video:1.4.2")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    // Bundled base pose model; applies to both healthConnect flavors (base + health).
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    implementation(libs.vosk.android)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation(libs.androidx.room.testing)
    // Health Connect dependency - only for health flavor
    "healthImplementation"(libs.androidx.health.connect.client)
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // MigrationTestHelper (Room v20→v22) lee los esquemas exportados como assets
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // LeakCanary: detects memory leaks in debug builds only (not included in release APK).
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
