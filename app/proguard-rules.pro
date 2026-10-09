# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Reglas quirúrgicas de R8 / ProGuard para Aura Music y Aura Beta:
# 1. Ofuscación activa en APK Beta y Release (sin -dontobfuscate), renombrando clases/métodos/campos a identificadores de 1 letra
#    y unificando todo en el paquete 'a' para compactar la tabla de strings en un único archivo classes.dex.
# 2. Eliminación de comodines amplios ({ *; }) en Activities/Services y de -keep class com.example.model.** { *; },
#    permitiendo que R8 elimine al 100% DebugMonitorActivity y su UI en el APK Beta, y haga inlining/poda de métodos no usados.
# 3. Eliminación automática de logs de depuración (android.util.Log.v/d/i) y chequeos Intrinsics de Kotlin.

# 1. Preservar el puente JNI hacia el motor nativo C++20 (libauramusic_dsp.so)
# Solo se preservan los nombres de métodos 'native' para que el enlace dinámico JNI funcione sin impedir la ofuscación del resto.
-keep class com.example.playback.NativeAudioEngine {
    native <methods>;
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# 2. Preservar únicamente la estructura requerida por Room SQLite (sin forzar *; en métodos auxiliares no usados)
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep @androidx.room.Entity class * {
    <init>(...);
    <fields>;
}
-keep @androidx.room.Dao interface * { *; }

# 3. Componentes registrados en AndroidManifest:
# aapt2 genera automáticamente las reglas -keep para los componentes presentes en el AndroidManifest final de cada variante
# (así, en el APK Beta donde DebugMonitorActivity tiene tools:node="remove", R8 elimina toda la suite de depuración del .dex).
# Solo aseguramos que se conserven los constructores por defecto sin usar el comodín { *; }.
-keepclassmembers class com.example.AuraApplication { <init>(); }
-keepclassmembers class com.example.MainActivity { <init>(); }
-keepclassmembers class com.example.playback.AuraMediaPlaybackService { <init>(); }
-keepclassmembers class com.example.playback.AuraDownloadService { <init>(); }
-keepclassmembers class com.example.widget.AuraMusicWidgetProvider { <init>(); }

# 4. Preservar métodos estáticos de Enums usados por el runtime de Kotlin/Java
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 5. Ofuscación máxima, unificación de paquete y compactación de Strings en un solo archivo DEX (Single DEX)
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'a'
-flattenpackagehierarchy 'a'
-overloadaggressively
-mergeinterfacesaggressively

# 6. Eliminar logs de depuración de Android y chequeos nulos/trazas de Kotlin en Release y Beta
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

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
    public static void throwUninitializedPropertyAccessException(java.lang.String);
}

# 7. Omitir tablas de nombres de archivos y números de línea (SourceFile, LineNumberTable) y metadatos de reflexión Kotlin
# para reducir drásticamente el peso de classes.dex y maximizar la ofuscación del APK Beta/Release.
-renamesourcefileattribute ''
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-dontwarn kotlin.Metadata
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn kotlinx.coroutines.**
-dontwarn com.google.errorprone.annotations.**



