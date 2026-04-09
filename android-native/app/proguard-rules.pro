# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ── LiteRT-LM (Gemma 4) ──────────────────────────────────────────────────────
-keep class com.google.ai.edge.litertlm.** { *; }
-keep interface com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# Protobuf Lite — field names must survive obfuscation
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.protobuf.**

# LiteRT runtime (formerly TensorFlow Lite)
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**
-keep class com.google.android.gms.tflite.** { *; }
-dontwarn com.google.android.gms.tflite.**