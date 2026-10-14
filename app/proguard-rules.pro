# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Reglas de R8 / ProGuard para Aura Music y Aura Beta:
# 1. Sin ofuscación de nombres (-dontobfuscate) y sin reempaquetado agresivo para
#    garantizar paridad funcional Beta = Debug en Descarga por URL y Explorar.
# 2. Eliminación de comodines amplios ({ *; }) solo donde R8 puede podar con seguridad;
#    la pila de red (OkHttp/Okio/Coil/Media3/WebView/corrutinas) y el puente JNI
#    se preservan explícitamente en las secciones 1 y 4b.
# 3. Eliminación automática de logs de depuración (android.util.Log.v/d/i) y chequeos Intrinsics de Kotlin.

# 1. Preservar el puente JNI hacia el motor nativo C++20 (libauramusic_dsp.so)
# El nombre del paquete debe conservarse íntegro: JNI estático usa
# Java_com_example_core_playback_NativeAudioEngine_* y con -repackageclasses
# el símbolo dejaría de coincidir (UnsatisfiedLinkError). Por eso se conserva
# la clase completa con su nombre y firmas.
-keep class com.example.core.playback.NativeAudioEngine {
    *;
}
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepnames class com.example.core.playback.NativeAudioEngine

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
-keepclassmembers class com.example.core.playback.AuraMediaPlaybackService { <init>(); }
-keepclassmembers class com.example.core.playback.AuraDownloadService { <init>(); }
-keepclassmembers class com.example.widget.AuraMusicWidgetProvider { <init>(); }
-keepclassmembers class com.example.widget.AuraLibraryWidgetProvider { <init>(); }

# 4. Preservar métodos estáticos de Enums usados por el runtime de Kotlin/Java
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 4b. Pila de red / streaming / descarga por URL / Explorar (Beta = Debug).
# Sin estos -keep, R8 fullMode + overloadaggressively renombra/fusiona métodos
# que OkHttp/Okio/Coil/Media3/WebView resuelven por reflexión/plataforma y la
# cascada de 5 niveles (yt-dlp -> InnerTube -> Invidious -> WebView -> directa)
# falla con NoSuchMethodError / ClassNotFoundException solo en Beta.
# Red HTTP directa (OkHttp + org.json en InnerTube/Invidious/Chunked/TikTok/Lyrics/SoundCloud)
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.json.** { *; }
-keep class com.example.core.network.** { *; }
-keep class com.example.core.streaming.** { *; }
-keep class com.example.core.data.importer.** { *; }
-keep class com.example.core.data.streaming.** { *; }
-keep class com.example.feature.explore.** { *; }
-keep class com.example.feature.download.** { *; }
# Coil (carátulas remotas https:// + Video Canvas)
-keep class coil.** { *; }
-keep class coil3.** { *; }
# Jetpack Media3 / ExoPlayer (reproducción local + streaming + MediaSessionService)
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-keep class androidx.media.session.** { *; }
# WebView sin cabeza (nivel 4 de la cascada: shouldOverrideUrlLoading /
# shouldInterceptRequest / onPageFinished / evaluateJavascript)
-keepclassmembers class * extends android.webkit.WebViewClient {
    *;
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    *;
}
-keep class android.webkit.** { *; }
# Corrutinas Kotlin (throttling de velocidad/tono + prefetch no lineal)
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }
-keep class kotlin.Metadata { *; }
# Room ya cubierto arriba; conservar TypeConverters y entidades de streaming/caché
-keep class * extends androidx.room.TypeConverter { *; }
# DataStore / Preferences (persistencia de calidad, caché, scopes de EQ, etc.)
-keep class androidx.datastore.** { *; }
# Navigation / SavedState (Explorar + submenús de Ajustes a pantalla completa)
-keep class androidx.navigation.** { *; }
-keep class androidx.savedstate.** { *; }
-keep class androidx.lifecycle.** { *; }

# 5. Optimización segura sin reempaquetado agresivo.
# Se eliminan -repackageclasses / -flattenpackagehierarchy / -overloadaggressively /
# -mergeinterfacesaggressively / -allowaccessmodification porque rompen:
# JNI estático (cambio de paquete), OkHttp Platform/Conscrypt, interceptores,
# MediaSessionService y WebView. Se conserva -optimizationpasses para compactar
# sin romper la pila de red. El APK Beta pesará algo más, pero Descarga por URL
# y Explorar funcionarán igual que en Debug.
-optimizationpasses 5
-dontobfuscate
# NOTA: sin ofuscación de nombres en Beta/Release para garantizar paridad con Debug.
# Si se quiere reactivar la ofuscación, NO usar repackage/flatten/overload/merge
# sin los -keep de la sección 4b.

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

# 7. Conservar tablas de nombres de archivos y números de línea (SourceFile, LineNumberTable),
# Exceptions y metadatos de reflexión Kotlin para obtener stacks útiles en Beta/Release.
# Antes se borraban con -renamesourcefileattribute '' y -keepattributes sin Exceptions,
# lo que dejaba los crashes Beta sin stack mientras en Debug sí salía el log.
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,Exceptions,SourceFile,LineNumberTable
-dontwarn kotlin.Metadata
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn kotlinx.coroutines.**
-dontwarn com.google.errorprone.annotations.**



