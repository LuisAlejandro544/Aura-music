# AI_CONTEXT.md - Contexto de Inteligencia Artificial 🤖

Este archivo define el contexto de diseño, directrices técnicas y restricciones inviolables para cualquier modelo de lenguaje o agente de IA que interactúe, modifique o extienda la base de código de **Aura Music**.

---

## 🎯 Propósito del Proyecto
**Aura Music** es un reproductor de audio local fuera de línea para Android con una experiencia estética inspirada en Spotify, pero con diseño moderno, colores vibrantes de neón, fondos 100% opacos, ecualización paramétrica de **10 bandas impulsada por C++20**, compresión WebP sin pérdida, almacenamiento de datos organizado y una suite de diagnóstico propia (**Aura Monitor**).

---

## 🔒 Reglas Técnicas Inviolables

1. **Requisitos de Sistema**:
   - `minSdk = 26` (Android 8.0 Oreo). No bajar esta versión bajo ninguna circunstancia.
   - `targetSdk = 36` y `compileSdk = 36`.

2. **Sin Dependencia de Archivos `.env`**:
   - Este proyecto **NO DEBE requerir `.env`** ni el plugin de secretos para compilar. Debe mantenerse 100% autónomo y reproducible en cualquier entorno.

3. **Privacidad y Modelo de Acceso a Archivos**:
   - **PROHIBIDO** el escaneo masivo automático del almacenamiento del teléfono (`READ_EXTERNAL_STORAGE` o rastreo ciego del disco).
   - Todo archivo debe ser importado **explícitamente por el usuario** mediante el *Storage Access Framework* (SAF) con `OpenMultipleDocuments` u `OpenDocumentTree`, persistiendo permisos de lectura con `takePersistableUriPermission`.

4. **Almacenamiento Estructurado**:
   - El almacenamiento de datos de la app se organiza en `Android/data/com.aistudio.musicplayer.aurasound/files/`:
     - `images/`: Carátulas comprimidas en WebP sin pérdida de calidad.
     - `songs/`: Canciones locales y demos.
     - `lyrics/`: Letras de canciones sincronizadas (.lrc).
     - `metadata/`: Archivos JSON con información técnica y descriptiva de las pistas.
     - `videos/`: Videos de fondo (.mp4/.webm) y loops de Canvas vinculados.
   - Los registros de diagnóstico del monitor se guardan de forma atómica en `filesDir/aura_debug_logs.json`.

5. **Carátulas Procedurales & WebP**:
   - Las canciones sin carátula deben usar el generador procedural vectorial, no imágenes genéricas estáticas o de IA fijas.
   - Toda compresión de imágenes debe realizarse en hilos de fondo (`Dispatchers.IO`) en formato WebP con calidad máxima.

6. **Motor de Audio y C++20**:
   - Se debe utilizar **Jetpack Media3 (ExoPlayer)** (`androidx.media3:media3-*`).
   - El ecualizador paramétrico cuenta con **10 bandas ISO** y limitador anti-clipping en **ISO C++20** con filtros biquad de doble precisión.
   - El motor de **Audio 8D y 16D Multi-Órbita Espacial** está integrado en **ISO C++20** (`EightDProcessor`): soporta tanto el modo 8D clásico (órbita 360°) como el modo **16D Multi-Órbita** (doble capa contra-rotatoria con separación espectral a 260 Hz y micro-retardo Haas), sin radares ni logotipos animados decorativos.
   - El **Clarificador de Voces HD** (`VocalClarityProcessor` en `dsp_filters.h`) procesa el audio en **ISO C++20** mediante descomposición Mid-Side (`Mid = (L+R)*0.5`, `Side = (L-R)*0.5`), corte de turbidez a 180 Hz y realce biquad de presencia vocal (2.8 kHz y 5.5 kHz), accesible estratégicamente en las pestañas *Ecualizador* y *Velocidad/Voz*.
   - El ecualizador **NO DEBE TENER UN APARTADO APARTE DE PANTALLA COMPLETA**. Se integra y despliega como una hoja modal unificada (`AudioEffectsBottomSheet`) accesible desde el Mini Reproductor y Now Playing con un solo toque.
   - Los cambios de velocidad y tono (*playback parameters*) deben contar con amortiguación (*throttling* con corrutinas) para no saturar ExoPlayer ni pausar la canción accidentalmente.
   - La finalización de pista debe avanzar de forma continua a la siguiente canción, y las transiciones con fundido (*fade-out / fade-in*) deben reestablecer progresivamente el 100% del volumen original sin interrupciones.

7. **Diseño de Interfaz (Jetpack Compose & M3)**:
   - Fondos 100% opacos OLED: evitar transparencias que permitan que las listas o cabeceras se filtren por detrás del reproductor o mini reproductor.
   - La animación de salida de Now Playing hacia abajo debe realizarse sin superposiciones de capas negras residuales (`slideOutVertically` limpio sobre fondo nativo).
   - Todos los elementos interactivos deben cumplir con un tamaño mínimo de toque de **48.dp**.
   - Modularidad en pantallas: `Home`, `Library`, `Import`, `NowPlaying`, `Settings`, `PlaylistDetail`.
   - **Soporte Material You y Persistencia Permanente**: Permitir seleccionar tema dinámico Material You en Android 12+ (API 31+) armonizado con el wallpaper del sistema sin perder las superficies oscuras puras OLED. El tema elegido (`pref_aura_theme`), el Aura Dinámica de Carátula (`pref_dynamic_artwork_color_enabled`) y el modo de visualización de video (`pref_video_display_mode`) se persisten permanentemente en `SharedPreferences` para conservarse al cerrar y reabrir la app.
   - **Portadas en Playlists (Imagen Personalizada o Collage 1-4) y Detalle de Álbumes/Artistas**:
     - Las Playlists soportan una imagen personalizada comprimida en WebP sin pérdida (`customArtPath` en Room v3). Si no tienen imagen asignada, `PlaylistCoverCollage` genera un collage dinámico según la cantidad de canciones: 1 foto si hay 1 canción, collage de 2 fotos si hay 2, collage de 3 fotos si hay 3, y collage 2x2 de 4 fotos si hay 4 o más.
     - Al pulsar sobre un Artista o un Álbum en la Biblioteca, no se debe reproducir inmediatamente la primera canción, sino abrir la vista detallada de canciones (`PlaylistDetailScreen` con `openArtist` / `openAlbum`) mostrando todas sus pistas.
   - **Protección Tipográfica Fija**: La escala tipográfica del sistema se fija mediante `LocalDensity` con `fontScale = 1.0f` para garantizar que la configuración global de tamaño de texto del usuario en Android no altere ni rompa la composición visual ni corte títulos o etiquetas en la app.

8. **Suite de Diagnóstico Propia, Telemetría de Rendimiento & LeakCanary**:
   - La actividad `DebugMonitorActivity` ("Aura Monitor") es una aplicación independiente con su propio icono en el cajón de aplicaciones del teléfono (`taskAffinity="com.example.debug.monitor"`, `launchMode="singleTask"`), y también es accesible desde la pantalla de Configuración de Aura Music. Ambas aplicaciones conviven sin interferir entre sí.
   - Debe interceptar excepciones no controladas a nivel de proceso (`Thread.setDefaultUncaughtExceptionHandler`) y registrar datos del teléfono (modelo, CPU ABI, RAM, almacenamiento, versión de Android) junto con el stacktrace en crudo.
   - Pestaña de rendimiento: Monitorea en vivo el consumo de RAM segmentado (Java Heap, Native C++20 Heap, Gráficos y PSS), carga de CPU (%), hilos concurrentes activos (`Thread.getAllStackTraces()`) con trazas de pila completas y estado térmico del dispositivo sin necesidad de PC.
   - LeakCanary debe estar configurado en `debugImplementation` para auditar fugas de memoria en la JVM.

9. **Video de Fondo Multifuncional (Rellenar Pantalla, Adaptado Horizontal, Lienzo o Desactivado)**:
   - Soporte para asociar videos a pistas individuales. Los videos cortos (≤ 10s - 20s) se optimizan en `FFmpegNativeEngine` con dos estilos seleccionables por el usuario (`CanvasLoopStyle`): **Seamless Loop con Crossfade** (`xfade`) o **Efecto Boomerang / Ping-Pong** (`reverse` + `concat=n=2:v=1:a=0`) para repetición cíclica continua de ida y vuelta sin saltos bruscos; los videos largos (> 20s) se estructuran con **Keyframes (GOP Corto a 30fps)** cada 1 segundo para saltos temporales (`seekTo`) instantáneos con 0ms de congelamiento. Permite elegir tanto el modo (`Auto`, `Loop`, `Sync`), el estilo de bucle (`Crossfade` o `Boomerang`) y el **encuadre de pantalla** (`FULLSCREEN_BACKGROUND` Rellenar vs `FULLSCREEN_ADAPTED` Adaptado horizontal) en `EditTrackDialog`, `VideoToMusicDialog`, `DownloadFromLinkDialog` y `VideoDisplayModeDialog`.
   - **Encuadre Personalizable 16:9 / 9:16 sin Divisiones Centrales**: Por defecto `processVideoForCanvas` conserva la proporción limpia del video para permitir alternar en tiempo real en `BackgroundVideoPlayer` entre `FULLSCREEN_BACKGROUND` (`RESIZE_MODE_ZOOM`, llena el 100% de la pantalla de arriba a abajo aunque recorte laterales/caras) y `FULLSCREEN_ADAPTED` (`RESIZE_MODE_FIT`, muestra el video horizontal completo sin recortar caras y desvanece el placeholder estático para evitar líneas horizontales a mitad de pantalla).
   - **Erradicación de Video Residual y Transición Suave**: Uso obligatorio de `key(currentTrack.id, currentTrack.videoUri)` y actualización reactiva en `AndroidView` para desmontar y liberar de inmediato el video de la canción anterior al terminar la pista o cambiar de canción, realizando una transición con fundido suave (*crossfade*) sobre la carátula de respaldo oficial.
   - **4 Modos de Visualización Seleccionables (`VideoDisplayMode`)**:
     - `FULLSCREEN_BACKGROUND`: El video rellena todo el fondo verticalmente (`RESIZE_MODE_ZOOM`) con velo oscuro y la carátula flotando al frente.
     - `FULLSCREEN_ADAPTED`: El video horizontal se adapta al ancho completo (`RESIZE_MODE_FIT`) sin recortar caras.
     - `CARD_CANVAS`: El video se reproduce dentro del marco central de la carátula (1:1).
     - `OFF`: Desactivado; solo se muestra la carátula estática.
   - **Selección de Diseños del Reproductor (`NowPlayingDesignMode`)**:
     - El usuario puede alternar libremente entre el **Modo Clásico** (carátula grande 1:1, visualizador de 28 bandas y controles tradicionales), el **Modo Cinemático Canvas** (estilo Spotify con video de fondo completo despejado, minicarátula de 54dp, frase lírica flotante, controles en el tercio inferior con botón Play/Pausa de 64dp y acceso a vista previa de letras) o el modo **Automático Inteligente** (conmuta al estilo cinemático al haber Video Canvas activo). La preferencia se persiste en `pref_now_playing_design_mode`.
   - **Armonización Cromática Sin Interferencia**: Cuando el video está activo, el resplandor ambiental (*ambient aura*), visualizador y acentos se extraen de un fotograma clave del video en lugar de la carátula, garantizando que el color de la carátula estática no choque con la imagen en movimiento del video.
   - El reproductor de video de fondo opera con `volume = 0.0f` para no contaminar el procesador PCM ni el motor DSP C++20 de audio principal.
   - **Sincronización Reactiva de Favoritos**: El corazón en Now Playing refleja instantáneamente el estado de `isFavorite` (rojo al estar marcado) sincronizándose con Room y ExoPlayer. La lista de inicio "Populares en tu biblioteca" permanece estable y nunca oculta canciones al marcar favoritos.

10. **Modo Video a Música (Extracción 3 en 1)**:
    - Permite a los usuarios móviles sin PC convertir videos de su galería en canciones locales.
    - Se realiza mediante demuxing directo de audio sin recodificación (*Direct Stream Demuxing*) con `MediaExtractor` y `MediaMuxer` a `.m4a` en `songs/`, garantizando velocidad instantánea (1-2s) y cero pérdida acústica.
    - Captura automática de fotograma de video en alta definición a WebP sin pérdida en `images/` como carátula de álbum.
    - Vinculación automática del Video Canvas sincronizado y reproducción inmediata tras la conversión.

11. **Descarga Directa desde TikTok, YouTube y Enlaces Web con Servicio en Segundo Plano (`AuraDownloadService`)**:
    - Permite a los usuarios descargar cualquier canción completa, parodia o audio de cualquier duración pegando un enlace de TikTok, YouTube o URL web.
    - **Servicio en Primer Plano y Notificación Nativa (`AuraDownloadService`)**: Ejecuta toda la descarga HTTP Range, extracción de carátula WebP (con recorte automático de franjas negras 4:3 `removeHorizontalLetterboxBars`) y optimización FFmpeg en un `ForegroundService` (`dataSync`) independiente de la pantalla. Si el usuario sale de la app, continúa descargando y publica una notificación nativa al finalizar que reproduce la pista al tocarla.
    - **Arquitectura de Extracción Resiliente de 3 Niveles (InnerTube + Invidious Bypass + WebView Móvil)**:
      - *Motor InnerTube*: API nativa directa de YouTube mediante clientes de baja fricción (`ANDROID_VR` y `VISIONOS`). 100% gratuita, ultrarrápida (<300ms) y libre de restricciones de inicio de sesión (`LOGIN_REQUIRED`) o firmas cifradas (`n-sig`).
      - *Bypass de Respaldo Invidious*: Instancias públicas libres para resolución inmediata de canciones oficiales con restricciones de derechos estrictas.
      - *Motor WebView Reparado con Garantía de Carátula*: Navegador efímero en segundo plano cargado sobre `m.youtube.com` (sin bloqueos de inserción ni error 150) con timeout de 22s, extracción de miniatura en DOM y **descarga en cascada multinivel** (`maxresdefault.jpg` -> `hqdefault.jpg` -> `mqdefault.jpg` -> `i.ytimg.com` -> fotograma clave de video).
      - *Selector Interactivo*: Permite al usuario alternar entre InnerTube y WebView en el diálogo con auto-fallback cruzado de 3 capas.
    - **Motor FFmpeg Puro y Entorno Python con yt-dlp Integrados en el APK Final (`FFmpegNativeEngine` & `YtDlpNativeEngine`)**:
      - Binarios nativos ejecutables `libffmpeg.so`, `libpython.so` y `libqjs.so` empaquetados en `jniLibs/` para todas las arquitecturas (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`), asegurando ejecución nativa sin wrappers de terceros obsoletos.
      - **Compilación Autónoma y Caché en GitHub Actions**: En CI/CD, las dependencias nativas se restauran en 3-5 segundos vía `actions/cache@v4` o se compilan y ensamblan automáticamente con el Android NDK usando la tarea Gradle `:app:provisionNativeDeps` en `app/build.gradle.kts` ante cache-miss o cambio de parámetros.
      - Paquetes de librerías dinámicas optimizados para audio/video (`libffmpeg.zip.so`) y entorno CPython 3.11 (`libpython.zip.so`) con OpenSSL, SQLite y módulos C nativos, descomprimidos atómicamente en segundo plano sin inflar el APK con encoders innecesarios.
      - Copia base de `yt-dlp` en `assets/bin/yt-dlp` para funcionamiento inmediato offline y actualización en caliente OTA desde GitHub Releases en `files/bin/yt-dlp` con verificación criptográfica SHA-256 (`SHA2-256SUMS`) sin forzar la publicación de nuevos APKs en Uptodown.
      - **Verificación y Actualización Transparente de Paquetes al Iniciar (`YtDlpAutoUpdater` & `PackageUpdateBanner`)**: Al abrir la app, `AuraApplication` inicia en segundo plano la verificación de paquetes mostrando una notificación nativa en la barra de estado (*"Verificando paquetes necesarios..."*) y un banner compacto no intrusivo en la interfaz. Si detecta actualización, muestra barra de progreso en vivo alcanzando el 100% verificado con SHA-256, cancelando de inmediato la notificación ante caídas de red para prevenir estados congelados, y aplicando la actualización en caliente de forma inmediata sin forzar reinicios destructivos. Se erradicó el bucle de re-descarga forzada ante fallos de extracción y el diálogo de descargas activa InnerTube por defecto para garantizar resolución instantánea (<300ms) sin bloqueos.
      - **Aprovisionamiento Nativo 100% Puro en Gradle y CMake (Cero `.sh`)**: Eliminación total de scripts `.sh`; toda la resolución de artefactos Maven multi-ABI, verificación SHA-256 y compilación de lanzadores PIE en C17 (`native_python_launcher.c`, `native_ffmpeg_launcher.c`, QuickJS C99 puro) reside en `app/build.gradle.kts` y `app/src/main/cpp/`, erradicando los fallos `sh: python3: inaccessible or not found`.
      - **Blindaje contra RCE, Zip Slip, Inyección de Argumentos y Certificados TLS Android 14+**: Validación canónica en `extractZip`, control de integridad SHA-256 en OTA, delimitador `--` en invocaciones CLI, y consolidación automática de certificados CA de Conscrypt APEX (`/apex/com.android.conscrypt/cacerts/`) y KeyStore en `usr/etc/tls/cert.pem` con auto-recuperación ante fallos de emisor local (`CERTIFICATE_VERIFY_FAILED`).
      - **Permisos de Red Obligatorios**: Declaración fija de `android.permission.INTERNET` y `ACCESS_NETWORK_STATE` en `AndroidManifest.xml`.
    - **Acelerador de Descarga sin Estrangulamiento (Chunked Range Download)**: Neutraliza la limitación artificial de ~63 KB/s de YouTube mediante solicitudes HTTP fragmentadas (`Range: bytes=X-Y` de 2.5 MB) descargando a 10 - 40 MB/s.
    - **Resolución de Video Canvas 480p por Defecto (MP4 H.264 Libre de HLS/m3u8)**: Tanto `yt-dlp` como `InnerTube` e `Invidious` priorizan estrictamente flujos de video directos HTTP a 480p (`height=480`, `ext=mp4`, `vcodec^=avc1`), excluyendo manifiestos `.m3u8`/`.mpd`, preservando los `http_headers` firmados y resolviendo activamente un stream de video dedicado si inicialmente `videoUrl == audioUrl`, garantizando la vinculación automática al Video Canvas de fondo sin omitir jamás el video.
    - Almacenamiento organizado: audio `.m4a`/`.mp3` en `songs/`, carátula oficial en WebP sin pérdida en `images/`, y video vinculado en `videos/` para reproducir el Video Canvas de fondo.
    - Interfaz adaptativa: Degradados visuales reactivos (Cyan/Magenta para TikTok, Rojo Carmesí/Naranja para YouTube/Web) y reproducción inmediata en Now Playing tras descargar (o aviso en notificación nativa si el usuario salió de la app).

12. **Suite de Auriculares y Ajustes Acústicos**:
    - **Filtro Crossfeed en C++20**: Algoritmo Bauer / Chu Moy que se activa **exclusivamente** cuando hay auriculares conectados (`isHeadphoneConnected`). Debe permanecer en reposo cuando se reproduzca por los altavoces del teléfono.
    - **Balance Estéreo Fino L/R**: Ajustable en tiempo real tanto en la pantalla de *Ajustes* como directamente en la pantalla completa *Now Playing* y en la hoja modal de efectos con botón de centrado.
    - **Protección Becoming Noisy y Fade-In**: Pausa inmediata ante desconexión de audífonos (cable o Bluetooth) y aumento progresivo de volumen en reanudación (~1s) para cuidar la salud auditiva.
    - **Controles de Auriculares Personalizables**: Manejo configurable de 1 clic, 2 clics, 3 clics y pulsación prolongada (hold) con mapeo flexible.

13. **Notificación Nativa del Sistema y MediaSessionService (Media3)**:
    - La reproducción en segundo plano debe apoyarse en `AuraMediaPlaybackService` extendiendo `MediaSessionService`.
    - Proporciona el controlador multimedia del sistema (System Media Controls) en Android 13, 14, 15+ (con carátula HD y barra ondulada de progreso) y compatibilidad retroactiva limpia en Android 8.0 a 12 (`MediaStyle`).
    - El canal de notificación debe ser silencioso (`IMPORTANCE_LOW`) para evitar pitidos en cada cambio de canción.
    - En Android 13+ (API 33+) se debe solicitar el permiso en tiempo de ejecución `POST_NOTIFICATIONS`.

14. **Carátula Limpia y Estática, Armonización Dinámica de Video Canvas y Cero Pantallas Negras**:
    - La carátula central y el Video Canvas se muestran con visibilidad completa del 100% sin cápsulas superpuestas que obstruyan la ilustración. Su escala se mantiene fija en 1.0f para garantizar máxima nitidez sin movimientos no deseados por la música.
    - Durante la reproducción de Video Canvas, se muestrean fotogramas del video en tiempo real exacto mediante `MediaMetadataRetriever.OPTION_CLOSEST` y retriever persistente, adaptando de forma inmediata (`tween(180)`) los tonos del halo lumínico y visualizador según las escenas del video sin el retraso de 2.5s de los keyframes.
    - Eliminación de fondos negros temporales o parpadeos al abrir Now Playing o el Mini Reproductor: `BackgroundVideoPlayer` desactiva el shutter negro, sincroniza la posición antes de `prepare()`, coloca la carátula oficial como placeholder y efectúa un fundido suave al emitirse `onRenderedFirstFrame()`.
    - El Mini Reproductor aplica un fondo completamente tintado y degradado con los colores extraídos de la pista activa, manteniendo consistencia visual en todas las pantallas.
    - Los atajos inferiores de Now Playing se organizan en 3 módulos equilibrados de igual proporción (`EQ FX`, `Letras`/`Carátula` y `Cola`), con área táctil superior a 48dp y texto en una sola línea sin cortes verticales.

15. **Letras Sincronizadas (.LRC y .TXT) Estilo Karaoke, Búsqueda Interactiva y Recomendación Oficial**:
    - Integración de analizador de marcas de tiempo `[mm:ss.xx]` con persistencia local en `Android/data/.../files/lyrics/track_{id}.lrc`.
    - **Búsqueda Interactiva Personalizada (`SearchLyricsDialog`)**: Permite al usuario ingresar y modificar el nombre de la canción y el artista libremente, consultando LRCLIB y visualizando las distintas versiones comunitarias disponibles.
    - **Recomendación de Lírica Oficial en Primer Puesto**: El motor prioriza la versión canónica oficial de LRCLIB (o la coincidencia más fiel con timestamps) situándola en el puesto #1 con insignia luminosa `⭐ OFICIAL / RECOMENDADA` y borde de neón.
    - **Importador Local de Letras**: Botón en la tarjeta de Karaoke y en estado vacío para importar archivos `.lrc` y `.txt` desde el celular mediante SAF.
    - **Auto-Detección y Vinculación Local**: Al importar canciones desde el almacenamiento o apps externas, detecta automáticamente archivos hermanos `.lrc` o `.txt` con el mismo nombre o metadatos incrustados, asignando la letra de inmediato sin depender de internet.
    - Descarga automática libre desde **LRCLIB** al cambiar a canciones sin letra, sin registro ni API keys.
    - Interfaz Karaoke interactiva con desplazamiento automático suave (*auto-scroll*), tipografía resaltada neón para el verso actual, y salto directo en la canción (*Seek-to-time*) al compás de cualquier línea pulsada. Soporte para visualización de letras planas en `.txt`.

16. **Recepción Inteligente 'Abrir con...' y 'Compartir con...' (Puente con Gestores de Descarga y Apps Externas)**:
    - Debe responder a `ACTION_VIEW`, `ACTION_SEND` y `ACTION_SEND_MULTIPLE` mediante `IncomingMediaHandler`.
    - Si es audio (`audio/*`), preguntar mediante diálogo con interruptor de recorte de silencios, copiar a la subcarpeta estructurada `songs/` para garantizar persistencia sin conexión de por vida, añadir a Room e iniciar reproducción instantánea con Now Playing expandido.
    - Si es video (`video/*`), activar automáticamente el diálogo de conversión 'Video a Música' 3 en 1 para demuxing rápido a `.m4a`, carátula WebP y Video Canvas vinculado.
    - Si es enlace web (`text/plain` o URL), abrir el diálogo de descarga de enlaces con auto-resolución de información (título, autor y carátula).
    - Soportar `singleTop` y `onNewIntent` sin recreación destructiva de la interfaz ni reinicio de pistas en reproducción.

17. **Eliminación Inteligente de Silencios, Repetidor A-B, Marquesina, Reverb C++20 y Velocidad 2.0x**:
    - Antes de cada importación (SAF archivos/carpetas, Video a Música, descargas TikTok/YouTube y 'Abrir con...'), el usuario dispone de un interruptor interactivo para eliminar automáticamente los silencios iniciales y finales (`AudioSilenceTrimmer`, umbral `-42 dB` RMS).
    - El modal `DownloadFromLinkDialog` debe ser completamente deslizable verticalmente (`verticalScroll`) para que todos los controles e interruptores de YouTube y TikTok sean accesibles en cualquier tamaño de pantalla.
    - El Reverb debe ejecutarse exclusivamente en el motor nativo C++20 (`ReverbProcessor`) sobre el flujo PCM sin activar `EnvironmentalReverb`/`PresetReverb` de Android (`android.media.audiofx`), evitando que el driver LVREV silencie la señal directa o retenga el canal al desactivarse.
    - Tanto **Velocidad de la Música** como **Velocidad de Voz / Tono (`Pitch`)** permiten ajuste de `0.50x` a `2.00x` con botones rápidos (`0.8x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`) en ambos controles.
    - El repetidor de segmento A-B (`ABLoopState`) se controla tanto desde la barra compacta junto al progreso en *Now Playing* como desde la pestaña de transiciones/bucle en la hoja modal de efectos (con ajuste fino de ±1s).
    - La barra superior `NowPlayingTopBar` debe mantenerse despejada sin el texto redundante "REPRODUCIENDO AURA / álbum".
    - Los títulos largos deben desplazarse suavemente con `basicMarquee` en *Now Playing*, *Mini Reproductor* y en la canción en reproducción dentro de las listas y la cola.

18. **Transparencia y Gestión de Medios Guardados, Navegación de Ajustes a Pantalla Completa, Carátulas en Cola y Consistencia en Letras**:
    - En la pantalla de Configuración & Ajustes (accesible desde **"Ajustes"** en `BottomNavBar`), no existe barra superior de pestañas redundante; en su lugar, organiza directamente todas las categorías (*Diseño del Reproductor*, *Aura Dinámica & Video*, *Paleta Base Predeterminada*, *Auriculares & Acústica DSP*, *Medios & Almacenamiento*, *Motores Nativos & Diagnóstico* y *Bienvenida*) mediante tarjetas navegables (`SettingsNavigationCard`). Al pulsar cualquiera de ellas, en lugar de abrir un diálogo modal emergente, se abre un **menú independiente a pantalla completa** (`SettingsSubMenuScreen`) con cabecera de retorno y `BackHandler`.
    - El apartado 'Medios & Almacenamiento' (`StoredMediaSettingsTab`) ofrece total transparencia sobre el almacenamiento: lista exhaustiva de canciones con carátula WebP o Video Canvas MP4 en disco (`images/` y `videos/`), tamaño consumido en KB/MB y botones individuales con confirmación para borrar la carátula o el video de cualquier pista, liberando espacio físico de inmediato.
    - La hoja modal de la cola de reproducción (`NowPlayingQueueSheet`) incluye miniatura en alta resolución (`ArtworkImage` de 42dp con esquinas redondeadas) junto a cada canción para su reconocimiento visual inmediato.
    - `NowPlayingLyricsCard` comparte idénticas dimensiones y proporción (`fillMaxWidth(0.86f).aspectRatio(1f)` con esquinas de 26dp) que `NowPlayingArtworkCard` para erradicar cualquier salto vertical al alternar entre carátula y letras; el texto de insignias en `SearchLyricsDialog` está optimizado para evitar colisiones o desbordamientos en cualquier teléfono móvil.

19. **Limpieza Atómica de Buffer (Buffer Flushing) y Arquitectura Modular**:
    - **Buffer Flushing en C++20 y Media3**: Purgado atómico de acumuladores IIR en las 10 bandas del EQ, filtros de graves, retardo de Crossfeed y colas de Reverb en `playTrack`, `seekTo`, `pause`, `release` y `onFlush()` de `NativeAudioProcessor`. Erradica definitivamente pops, clics y colas de eco transitorias.
    - **Desarrollo Modular Activo**: Organización del código C++20 en submódulos especializados (`dsp_filters.h`, `dsp_spatial.h`, `dsp_crossfeed.h`, `dsp_reverb.h` y `auramusic_dsp.h`), desacoplamiento de `MusicViewModel.kt` en coordinadores (`LyricsCoordinator`, `IncomingMediaCoordinator`, `HeadphoneSettingsCoordinator`, `MixtapeCoordinator`), modularización de `AuraAudioPlayer.kt` (desacoplado en `PlayerQueueController`, `AudioFadeController`, `ABLoopController`, `MediaSessionBridge`), modularización de `OnlineVideoAudioImporter.kt` (desacoplado en `ChunkedStreamDownloader`, `TikTokMediaResolver`, `MediaAssetProcessor`) y modularización de `MusicRepository.kt` (desacoplado en `PlaylistRepository` y `SafTrackImporter`), garantizando que todos los archivos se mantengan preferentemente bajo 500 líneas con 100% de compatibilidad pública.

20. **Mixtape Maker / Fusión de Canciones con Crossfade Continuo en FFmpeg**:
    - Fusión nativa en un solo archivo continuo `.m4a` con fundido cruzado (*acrossfade* en FFmpeg) y selector de duración de transición (3s, 5s, 8s, 10s) con normalización acústica previa a 44.1 kHz estéreo para cero chasquidos o desajustes de reloj.
    - **Capítulos Reactivos para Carátula y Video Canvas en Tiempo Real**: Sistema de marcas temporales exactas (`metadata/mixtape_{id}.json` y `MixtapeChapter`) que conmuta de forma reactiva el título, artista, carátula oficial WebP y Video Canvas de la canción correspondiente en *Now Playing* y en el *Mini Reproductor* al transcurrir el audio o al hacer seek, junto con la sincronización cromática de fondo y la insignia `MIX X/Y`.
    - **Karaoke con Letras LRC Continuas Concatenadas**: Concatenación automática de las letras sincronizadas (.lrc) sumando el offset temporal acumulado de cada capítulo, permitiendo auto-scroll continuo y karaoke fluido a lo largo de todo el mix continuo.
    - **Portada Oficial en Collage WebP**: Generación automática de portada en collage de 1 a 4 fotos comprimida en WebP Lossless en `images/`, registrándose como pista completa en Room bajo el álbum *"Aura Mixtapes"*.

21. **Idioma de Comunicación**:
   - La documentación, comentarios en código, cadenas de usuario (`strings.xml`) y mensajes de commit deben redactarse en **español**.
