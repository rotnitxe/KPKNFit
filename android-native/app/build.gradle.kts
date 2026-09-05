import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.kpkn"
    // Revertimos a 36 porque las librerías actuales lo exigen
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.kpkn"
        minSdk = 24
        targetSdk = 35
        versionCode = 32
        versionName = "KPKN Beta 14.7"

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

    signingConfigs {
        create("release") {
            // Firma local forzada para evitar el error de "paquete no válido"
            storeFile = file("kpkn-release.keystore")
            storePassword = "kpkn2024"
            keyAlias = "kpkn"
            keyPassword = "kpkn2024"
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
            signingConfig = signingConfigs.getByName("release")
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
            // El debug también usa la firma de release para evitar conflictos de instalación
            signingConfig = signingConfigs.getByName("release")
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

    // Esquemas Room exportados como assets de androidTest para MigrationTestHelper
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val verifyDatasetKnowledge by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verifies that the compiled nutrition dataset matches its master JSON."
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/process_dataset.py", "--check")
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
    implementation(libs.vosk.android)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
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
