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

# Release stack traces remain usable without disabling obfuscation globally.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Runtime reflection and JSON adapters need annotations and generic signatures.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Honor AndroidX @Keep annotations used by app models and SDK integration points.
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}

# Gson relies on reflected fields. @SerializedName allows the field itself to be
# obfuscated while keeping the wire JSON names stable.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# API request/response DTOs are part of the server contract. Keep them stable so
# release builds cannot strip constructors, fields, or nested DTO references.
-keep class com.example.lcb.app.pairing.** { *; }

# Gson internals and optional platform hooks.
-dontwarn com.google.gson.**
-dontwarn sun.misc.**
