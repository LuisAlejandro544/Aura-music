# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Reglas quirúrgicas de R8 / ProGuard para Aura Music y Aura Beta:
# 1. Ofuscación activa en APK Beta y Release (sin -dontobfuscate), renombrando clases/métodos/campos
#    y unificando en el paquete 'a' para compactar en un único archivo classes.dex, SIN
#    -overloadaggressively/-mergeinterfacesaggressively/-allowaccessmodification (rompían
#    OkHttp/Okio/Coil/Media3 por reflexión y tumbaban Descarga por URL + Explorar en Beta).
# 2. Eliminación de comodines amplios ({ *; }) en Activities/Services y de -keep class com.example.model.** { *; },
#    permitiendo que R8 elimine al 100% DebugMonitorActivity y su UI en el APK Beta, y haga inlining/poda de métodos no usados.
# 3. Eliminación automática de logs de depuración (android.util.Log.v/d/i) y chequeos Intrinsics de Kotlin.
# 4. Keeps quirúrgicos para la pila de red/streaming (okhttp3/okio/coil/media3/webkit/corrutinas)
#    + JNI NativeAudioEngine con paquete preservado + tablas de línea para stacks Beta útiles.

# 1. Preservar el puente JNI hacia el motor nativo C++20 (libauramusic_dsp.so)
# La clase completa se conserva con su nombre/paquete original para que los símbolos
# estáticos Java_com_example_core_playback_NativeAudioEngine_* sigan coincidiendo
# tras -repackageclasses 'a' (las clases con -keep no se reempaquetan).
-keep,allowshrinking class com.example.core.playback.NativeAudioEngine {
    *;
}
-keeppackagenames com.example.core.playback
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
-keepclassmembers class com.example.core.playback.AuraMediaPlaybackService { <init>(); }
-keepclassmembers class com.example.core.playback.AuraDownloadService { <init>(); }
-keepclassmembers class com.example.widget.AuraMusicWidgetProvider { <init>(); }
-keepclassmembers class com.example.widget.AuraLibraryWidgetProvider { <init>(); }

# 4. Preservar métodos estáticos de Enums usados por el runtime de Kotlin/Java
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 5. Ofuscación compacta en un solo archivo DEX (Single DEX) SIN flags que rompen
# la pila de red/descarga-streaming (Descarga por URL + Explorar).
# Se eliminan -overloadaggressively, -mergeinterfacesaggressively y -allowaccessmodification
# porque renombran/fusionan métodos que OkHttp/Okio/Coil/Media3 resuelven por reflexión
# (Platform, Conscrypt, interceptores, MediaSessionService) y provocaban en Beta
# NoSuchMethodError/ClassNotFoundException en la cascada InnerTube/Invidious/Chunked/WebView.
-optimizationpasses 5
-repackageclasses 'a'
-flattenpackagehierarchy 'a'

# 5b. Pila de red y streaming usada por Descarga por URL y Explorar (OkHttp directo +
# org.json + WebView + Coil + Media3 + corrutinas). Sin estos -keep, R8 fullMode poda
# clases llamadas por reflexión y la cascada de 5 niveles nunca completa en Beta.
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.json.** { *; }
-keep class coil.** { *; }
-keep class androidx.media3.** { *; }
-keep class androidx.media.** { *; }
-keep class kotlinx.coroutines.** { *; }
# Conscrypt/Platform de OkHttp: detección por reflexión del proveedor TLS en Android.
-keep class com.android.org.conscrypt.** { *; }
-keep class org.conscrypt.** { *; }
# WebView del nivel 4 de la cascada (HeadlessWebViewExtractor): callbacks invocados por el framework.
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public *;
}
-keepclassmembers class android.webkit.WebView {
    public *;
}
# Servicios de notificación/reproducción invocados por el sistema (MediaSessionService + workers).
-keepclassmembers class * extends androidx.media3.session.MediaSessionService {
    public *;
}
-keepclassmembers class * extends android.app.Service {
    public *;
}
-keepclassmembers class * extends androidx.work.Worker {
    public *;
}
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public *;
}
# Serialización org.json / modelos de InnerTube/Invidious/SoundCloud/LRCLIB: conservar firmas.
-keepclassmembers class com.example.core.data.importer.** { *; }
-keepclassmembers class com.example.feature.streaming.** { *; }

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

# 7. Ofuscación de nombres de archivo pero CONSERVANDO tablas de línea y excepciones
# para que los crashes Beta (Descarga por URL / Explorar) lleguen con stack útil.
# Coste en peso mínimo (~KBs) a cambio de poder diagnosticar NoSuchMethodError/SSL/zipimport.
-renamesourcefileattribute 'SourceFile'
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,Exceptions,LineNumberTable,SourceFile
-dontwarn kotlin.Metadata
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn coil.**
-dontwarn androidx.media3.**
-dontwarn retrofit2.**
-dontwarn kotlinx.coroutines.**
-dontwarn com.google.errorprone.annotations.**



