# Reglas R8 / ProGuard para Aura Music y Aura Beta (versión funcional).
# Causa raíz del crash Beta: fullMode + -repackageclasses/-overloadaggressively +
# -allowaccessmodification renombraba/fusionaba clases que OkHttp/Okio/Coil/Media3/
# WebView resuelven por reflexión, y rompía el JNI estático
# Java_com_example_core_playback_NativeAudioEngine_* (sin RegisterNatives).
# Debug funcionaba porque no minificaba. Beta ahora lleva minify=false en
# app/build.gradle.kts; estas reglas quedan como red de seguridad para Release
# y para cualquier futura reactivación de minify en Beta: NO omiten nada crítico.

# 1. Puente JNI hacia el motor nativo C++20 (libauramusic_dsp.so).
# Los símbolos C++ son estáticos con el paquete completo en el nombre, así que
# se conserva la CLASE ENTERA (nombre + paquete + métodos) y se prohíbe el
# reempaquetado (sin -repackageclasses/-flattenpackagehierarchy).
-keep class com.example.core.playback.NativeAudioEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
# No reempaquetar ni fusionar nunca: protege el JNI estático y la reflexión.
-dontobfuscate class com.example.core.playback.NativeAudioEngine
-keepnames class com.example.core.playback.NativeAudioEngine

# 2. Room SQLite.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep @androidx.room.Entity class * {
    <init>(...);
    <fields>;
}
-keep @androidx.room.Dao interface * { *; }

# 3. Componentes registrados en AndroidManifest (constructores por defecto).
-keepclassmembers class com.example.AuraApplication { <init>(); }
-keepclassmembers class com.example.MainActivity { <init>(); }
-keepclassmembers class com.example.core.playback.AuraMediaPlaybackService { <init>(); }
-keepclassmembers class com.example.core.playback.AuraDownloadService { <init>(); }
-keepclassmembers class com.example.widget.AuraMusicWidgetProvider { <init>(); }
-keepclassmembers class com.example.widget.AuraLibraryWidgetProvider { <init>(); }
# Aura Monitor: aunque Beta lo elimina del manifiesto, se conserva la clase para
# que la referencia guardada por BuildConfig.ENABLE_DEBUG_MONITOR nunca lance
# NoClassDefFoundError/ActivityNotFoundException.
-keep class com.example.debug.DebugMonitorActivity { <init>(); }

# 4. Enums de Kotlin/Java.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 5. Pila de red/streaming usada por Descarga por URL y Explorar (cascada 5 niveles:
# YTDLP -> InnerTube multi-cliente -> Invidious -> WebView -> descarga directa).
# Todo pasa por OkHttp + org.json + corrutinas + WebView + Coil + Media3.
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep class coil.** { *; }
-keep class coil3.** { *; }
-keep class androidx.media3.** { *; }
-keep class androidx.media.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class org.json.** { *; }
-keep class com.squareup.moshi.** { *; }
-keep class retrofit2.** { *; }
# WebView Headless (nivel 4): WebViewClient.shouldOverrideUrlLoading /
# shouldInterceptRequest / onPageFinished + evaluateJavascript.
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
    protected *;
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public *;
    protected *;
}
-keep class android.webkit.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# DataStore / Preferences / Credential / Palette usados en Ajustes.
-keep class androidx.datastore.** { *; }
-keep class androidx.credentials.** { *; }
-keep class androidx.palette.** { *; }
# TLS / certificados (Android 14+ CERTIFICATE_VERIFY_FAILED): Conscrypt APEX,
# KeyStore PEM y validación SSL de yt-dlp/InnerTube/Invidious/Chunked/LRCLIB.
# Se resuelven por reflexión/plataforma; R8 nunca debe renombrarlos ni podarlos.
-keep class org.conscrypt.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class org.openjsse.** { *; }
-keep class com.android.org.conscrypt.** { *; }
# Transitives de OkHttp/Okio/Coil/Media3 usados por la cascada de 5 niveles
# (detección Platform, interceptores, fetchers, datasources, extractores).
-keep class com.google.common.** { *; }
-keep class okhttp3.internal.** { *; }
-keep class okio.internal.** { *; }
-keep class coil.fetch.** { *; }
-keep class coil.request.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.exoplayer.** { *; }

# 6. Optimización SEGURA (sin repackage/flatten/overload/merge/access-modification).
-optimizationpasses 3
-dontusemixedcaseclassnames
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,Exceptions,SourceFile,LineNumberTable

# 7. Logs: se conservan w/e (imprescindibles para diagnosticar Beta) y solo se
# permite podar v/d/i. Intrinsics de Kotlin NO se tocan (evita crashes oscuros).
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

-dontwarn kotlin.Metadata
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn kotlinx.coroutines.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
