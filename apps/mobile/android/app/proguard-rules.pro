# ── React Native ─────────────────────────────────────────────────────────────
-keep,allowobfuscation @interface com.facebook.proguard.annotations.DoNotStrip
-keep,allowobfuscation @interface com.facebook.proguard.annotations.KeepGettersAndSetters
-keep @com.facebook.proguard.annotations.DoNotStrip class *
-keepclassmembers class * { @com.facebook.proguard.annotations.DoNotStrip *; }
-keepclassmembers @com.facebook.proguard.annotations.KeepGettersAndSetters class * { void set*(***); *** get*(); }

# Hermes
-keep class com.facebook.hermes.unicode.** { *; }
-keep class com.facebook.jni.** { *; }

# ── KPKN native modules ─────────────────────────────────────────────────────
-keep class com.yourprime.app.modules.** { *; }
-keep class com.yourprime.app.Kpkn*WidgetProvider { *; }
-keep class com.yourprime.app.KpknWidgetHelper { *; }
-keep class com.yourprime.app.KpknBootReceiver { *; }

# ── MediaPipe (on-device AI) ────────────────────────────────────────────────
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ── AndroidX WorkManager ────────────────────────────────────────────────────
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# ── Google Play Asset Delivery ──────────────────────────────────────────────
-keep class com.google.android.play.core.assetpacks.** { *; }
-dontwarn com.google.android.play.core.assetpacks.**

# ── Guava (required by MediaPipe) ───────────────────────────────────────────
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# ── react-native-reanimated ─────────────────────────────────────────────────
-keep class com.swmansion.reanimated.** { *; }
-dontwarn com.swmansion.reanimated.**

# ── react-native-gesture-handler ────────────────────────────────────────────
-keep class com.swmansion.gesturehandler.** { *; }
-dontwarn com.swmansion.gesturehandler.**

# ── react-native-mmkv ──────────────────────────────────────────────────────
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# ── react-native-quick-sqlite ──────────────────────────────────────────────
-keep class com.nicepeoplethatcode.rnsqlite.** { *; }
-dontwarn com.nicepeoplethatcode.rnsqlite.**

# ── @notifee/react-native ──────────────────────────────────────────────────
-keep class io.invertase.notifee.** { *; }
-dontwarn io.invertase.notifee.**

# ── react-native-screens ───────────────────────────────────────────────────
-keep class com.swmansion.rnscreens.** { *; }
-dontwarn com.swmansion.rnscreens.**

# ── Suppress common harmless warnings ──────────────────────────────────────
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
