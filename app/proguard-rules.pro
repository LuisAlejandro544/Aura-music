# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Reglas R8 / ProGuard para Aura Music y Aura Beta:
# El APK Beta minifica para ser liviano, pero SIN romper Descarga por URL ni Explorar.
# Causa raíz anterior: -repackageclasses/-flatten/-overloadaggressively/-mergeinterfaces
# + fullMode + falta de -keep para OkHttp/Okio/Coil/Media3/WebView + JNI estático con
# nombres de paquete + poda agresiva de stdlib Python. Todo eso se corrige aquí.

# 1. Puente JNI hacia el motor nativo C++20 (libauramusic_dsp.so)
# Los símbolos C++ usan nombres estáticos Java_com_example_core_playback_NativeAudioEngine_*,
# por lo que el paquete y el nombre de la clase NUNCA deben renombrarse ni reempaquetarse.
-keep class com.example.core.playback.NativeAudioEngine { *; }
-keeppackagenames com.example.core.playback
-keepclasseswithmembernames class * {
    native <methods>;
}

# 2. Room SQLite: preservar estructura requerida (entidades, DAOs, DB)
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep @androidx.room.Entity class * {
    <init>(...);
    <fields>;
    <methods>;
}
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# 3. Componentes registrados en AndroidManifest (Application, Activities, Services, Widgets)
# Se conservan completos (no solo <init>) para que MediaSessionService, DownloadService
# y Widgets no pierdan callbacks por inlining/poda.
-keep class com.example.AuraApplication { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.core.playback.AuraMediaPlaybackService { *; }
-keep class com.example.core.playback.AuraDownloadService { *; }
-keep class com.example.widget.AuraMusicWidgetProvider { *; }
-keep class com.example.widget.AuraLibraryWidgetProvider { *; }
-keep class com.example.debug.AuraDebugManager { *; }

# 4. Enums usados por el runtime de Kotlin/Java
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 5. Pila de red y streaming usada por Descarga por URL y Explorar (InnerTube/Invidious/
# Chunked/TikTok/LRCLIB/SoundCloud/YT Music). Sin estos -keep, R8 fullMode + overload/
# repackage rompe la reflexión/plataforma de OkHttp/Okio y la cascada de 5 niveles falla.
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**
-keep class org.json.** { *; }
-keep class coil.** { *; }
-keep interface coil.** { *; }
-dontwarn coil.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
# Conscrypt / plataforma TLS (detección Platform de OkHttp en Android 14+)
-keep class org.conscrypt.** { *; }
-keep class com.android.org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
# Media3 / ExoPlayer (ForwardingPlayer, MediaSessionService, renderers por reflexión)
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**
# Modelos y motores propios de descarga/streaming: evitar poda de niveles de fallback
-keep class com.example.core.data.importer.** { *; }
-keep class com.example.core.data.streaming.** { *; }
-keep class com.example.core.network.** { *; }
-keep class com.example.core.playback.** { *; }
-keep class com.example.core.data.updater.** { *; }
# Parcelable / Serializable usados al pasar pistas entre componentes
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 6. WebView del nivel 4 de la cascada (HeadlessWebViewExtractor: shouldOverrideUrlLoading,
# shouldInterceptRequest, onPageFinished, evaluateJavascript + @JavascriptInterface)
-keepclassmembers class * extends android.webkit.WebViewClient {
    *;
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    *;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class android.webkit.** { *; }

# 7. Optimización moderada SIN reempaquetado agresivo.
# Se eliminan -repackageclasses, -flattenpackagehierarchy, -overloadaggressively,
# -mergeinterfacesaggressively y -allowaccessmodification porque renombran/fusionan
# métodos que OkHttp/Okio/Coil/Media3 resuelven por reflexión y rompen los símbolos
# JNI estáticos (UnsatisfiedLinkError) y la plataforma TLS.
-optimizationpasses 5

# 8. Eliminar solo logs de depuración verbose/debug/info; conservar w/e/wtf para
# diagnosticar fallos de red/streaming en Beta (antes se borraba todo rastro útil).
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

# 9. Conservar atributos de excepción y líneas para stacks útiles en Beta.
# El peso extra (~100-200 KB en classes.dex) permite ver NoSuchMethodError /
# ClassNotFoundException / errores python/ytdlp/ssl en logcat en vez de un crash mudo.
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,Exceptions,SourceFile,LineNumberTable
-dontwarn kotlin.Metadata
-dontwarn retrofit2.**
-dontwarn kotlinx.coroutines.**
-dontwarn com.google.errorprone.annotations.**
