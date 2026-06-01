# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# ── kotlinx.serialization ──────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer;
}
-keepclassmembers class **$serializer {
    kotlinx.serialization.KSerializer serializer;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.Serializable <methods>;
}
-keep,includedescriptorclasses class com.example.kpkn.**$$serializer { *; }
-keepclassmembers class com.example.kpkn.** {
    *** Companion;
}

# ── KPKN domain models (serializable data classes) ────────────────────────────
-keep class com.example.kpkn.data.models.** { *; }
-keep class com.example.kpkn.data.exercises.** { *; }
-keep class com.example.kpkn.data.db.** { *; }
-keep class com.example.kpkn.data.food.** { *; }
-keep class com.example.kpkn.domain.** { *; }

# ── Room entities, DAOs, and database ─────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.TypeConverter class * { *; }
-keep @androidx.room.Database class * { *; }

# ── Haze (Blur effect) ────────────────────────────────────────────────────────
-keep class dev.chrisbanes.haze.** { *; }

# ── Compose & Navigation ──────────────────────────────────────────────────────
-keep class androidx.compose.material.icons.** { *; }
-keep class androidx.navigation.** { *; }
