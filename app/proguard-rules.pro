# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Reglas quirúrgicas de R8 / ProGuard para Aura Music y Aura Beta (Opción 1: Poda automática de material-icons-extended)
# Permite que R8 elimine los ~9,900 iconos no referenciados de androidx.compose.material:material-icons-extended
# y el código muerto de librerías, preservando al 100% los componentes críticos, JNI C++20, Room y modelos de datos.

# 1. Preservar el puente JNI hacia el motor nativo C++20 (libauramusic_dsp.so)
-keep class com.example.playback.NativeAudioEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# 2. Preservar Entidades, DAOs y Base de Datos Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.example.data.local.** { *; }

# 3. Preservar Modelos de Dominio, Serialización JSON / Moshi y Estados
-keep class com.example.model.** { *; }
-keep class com.example.debug.DebugLogEntry { *; }
-keep class com.example.debug.DeviceDiagnosticInfo { *; }
-keep class com.example.debug.PerformanceDiagnosticModels** { *; }

# 4. Preservar Componentes del Sistema registrados en AndroidManifest (Activities, Services, Widget, Application)
-keep class com.example.AuraApplication { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.debug.DebugMonitorActivity { *; }
-keep class com.example.playback.AuraMediaPlaybackService { *; }
-keep class com.example.playback.AuraDownloadService { *; }
-keep class com.example.widget.AuraMusicWidgetProvider { *; }

# 5. Preservar Enums para serialización segura y preferencias
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 6. Optimización agresiva de R8, poda de @kotlin.Metadata (no se usa kotlin-reflect) y eliminación de chequeos Intrinsics redundantes
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'aura'

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(java.lang.Object);
    public static void checkNotNull(java.lang.Object, java.lang.String);
    public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
}

# 7. Preservar únicamente anotaciones de runtime e información mínima sin retener tablas pesadas ni metadatos de reflexión Kotlin
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-dontwarn kotlin.Metadata
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn kotlinx.coroutines.**
-dontwarn com.google.errorprone.annotations.**


