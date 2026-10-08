// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
