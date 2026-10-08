# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Reglas seguras de R8 / ProGuard para Aura Music y Aura Beta
# Preserva intactas todas las clases, modelos, entidades Room, ViewModels, UI y puentes JNI de la app
-keep class com.example.** { *; }
-keep interface com.example.** { *; }
-keep enum com.example.** { *; }

# Preservar métodos nativos JNI C++20 (libauramusic_dsp.so)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preservar metadatos de Kotlin, Coroutines y Serialización / Moshi / Room
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,SourceFile,LineNumberTable
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn kotlinx.coroutines.**
-dontwarn com.google.errorprone.annotations.**

