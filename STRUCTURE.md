# Estructura del Proyecto Aura Music 🎧

Este documento detalla la arquitectura de software, organización de módulos, responsabilidades y componentes de **Aura Music**.

---

## 🏛️ Visión General de Arquitectura (MVVM + Clean Architecture)

El proyecto sigue una arquitectura reactiva y desacoplada organizada en capas:

1. **Capa Nativa (C++20 / NDK)**: Motores de procesamiento de audio digital DSP en tiempo real (Ecualizador paramétrico de 10 bandas IIR, Audio 8D/16D Multi-Órbita, Clarificador de Voces Mid-Side HD, Reverb Schroeder/Moorer y Crossfeed Chu Moy) + binarios nativos optimizados de FFmpeg y QuickJS/CPython.
2. **Capa de Datos & Almacenamiento (Data & Storage)**:
   - Base de datos SQLite reactiva con **Room**.
   - Acceso al almacenamiento estructurado en `Android/data/com.aistudio.musicplayer.aurasound/files/` (Debug) y `Android/data/com.auramusic.beta/files/` (APK Beta `Aura Beta` • Codename `Nebula`) con subcarpetas (`songs/`, `images/`, `lyrics/`, `metadata/`, `videos/`).
   - Motores de importación, sincronización de letras LRCLIB, extracción FFmpeg, descarga resiliente (Chunked Range Download, InnerTube, Invidious y fallback `yt-dlp` blindado con actualización OTA activa en Debug y Beta) y actualizador automático de versiones APK (`AppReleaseUpdater` con comparador semántico de tags dinámicos `-beta`, selección por arquitectura móvil y verificación SHA-256).
3. **Capa de Control & Reproducción (Playback Layer)**:
   - `AuraAudioPlayer` con ExoPlayer/Media3, modularizado con controladores especializados (`PlayerQueueController`, `AudioFadeController`, `ABLoopController`, `MediaSessionBridge`).
   - `NativeAudioEngine` conectando buffers de audio PCM en C++20 vía JNI.
4. **Capa de Lógica de Negocio & Estado (ViewModel Layer)**:
   - `MusicViewModel` modularizado y ultra liviano, orquestado por coordinadores y delegados especializados:
     - `AudioEffectsCoordinator`: Ecualizador C++20 de 10 bandas, Audio 8D/16D, Clarificador de Voces HD, Reverb, Normalizador EBU R128, Automix DJ y Sleep Timer.
     - `NavigationCoordinator`: Rutas de navegación, temas visuales, modo de diseño del reproductor y selección de colecciones.
     - `TrackLibraryCoordinator`: CRUD de pistas y playlists, importación SAF de archivos/carpetas, Video a Música y descargas en segundo plano con `AuraDownloadService`.
     - `LyricsCoordinator`: Gestión de letras locales y remotas LRCLIB.
     - `IncomingMediaCoordinator`: Recepción "Abrir con..." y "Compartir con...".
     - `HeadphoneSettingsCoordinator`: Detección acústica de auriculares, modo Bit-Perfect/AAudio, guardado y balance estéreo.
     - `MixtapeCoordinator`: Fusión continua de canciones en FFmpeg y capítulos reactivos en tiempo real.
5. **Capa de Presentación (UI con Jetpack Compose & Material 3)**:
   - Pantallas modulares (`HomeScreen`, `LibraryScreen`, `NowPlayingScreen`, `SettingsScreen`, `ImportMusicScreen`, `OnboardingScreen`, `PlaylistDetailScreen`).
   - Host global modularizado (`AuraMusicAppContent`, `GlobalDialogsHost`).
   - Componentes modulares reutilizables organizados en submódulos temáticos.

---

## 📂 Árbol de Directorios y Módulos

```text
app/
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   ├── cpp/                                # Núcleo C++20 Nativo DSP & Launchers
│   │   │   ├── CMakeLists.txt
│   │   │   ├── auramusic_dsp.h                 # Fachada pública del motor DSP
│   │   │   ├── auramusic_dsp.cpp               # Implementación JNI y ruteo de buffers
│   │   │   ├── dsp_filters.h                   # Filtros biquad IIR, EQ 10 bandas y Vocal Clarity
│   │   │   ├── dsp_spatial.h                   # Paneo 8D y Audio Espacial 16D Multi-Órbita
│   │   │   ├── dsp_crossfeed.h                 # Filtro Crossfeed acústico binaural Chu Moy
│   │   │   ├── dsp_reverb.h                    # Reverb digital Schroeder con líneas de retardo
│   │   │   ├── dsp_bitperfect.h                # Motor Bit-Perfect 1:1 y salida AAudio Ultra-Baja Latencia
│   │   │   ├── native_ffmpeg_launcher.c        # Invocación directa POSIX a FFmpeg
│   │   │   ├── native_quickjs_cli.c            # Intérprete QuickJS C99
│   │   │   └── native_python_launcher.c        # Embebedor CPython 3.11
│   │   │
│   │   └── java/com/example/
│   │       ├── AuraApplication.kt             # Inicialización de la aplicación y DebugManager
│   │       ├── MainActivity.kt                 # Actividad principal modularizada (< 140 líneas)
│   │       │
│   │       ├── data/
│   │       │   ├── local/                      # Capa de Persistencia Local (Room)
│   │       │   │   ├── AppDatabase.kt          # Base de datos Room
│   │       │   │   ├── dao/
│   │       │   │   │   ├── TrackDao.kt
│   │       │   │   │   └── PlaylistDao.kt
│   │       │   │   └── entity/
│   │       │   │       ├── TrackEntity.kt
│   │       │   │       ├── PlaylistEntity.kt
│   │       │   │       └── PlaylistTrackCrossRef.kt
│   │       │   │
│   │       │   ├── repository/                 # Repositorios y Operaciones de Datos
│   │       │   │   ├── MusicRepository.kt      # Fachada principal de repositorio
│   │       │   │   ├── PlaylistRepository.kt   # Gestión de listas y cross-references
│   │       │   │   └── SafTrackImporter.kt     # Importador blindado contra colisiones de extensiones
│   │       │   │
│   │       │   ├── storage/
│   │       │   │   └── AppStorageManager.kt    # Manejo de disco en Android/data/.../files/
│   │       │   │
│   │       │   ├── updater/
│   │       │   │   └── AppReleaseUpdater.kt    # Actualizador automático de versiones APK desde GitHub Releases
│   │       │   │
│   │       │   └── importer/                   # Motores de Descarga, Conversión y Medios Organizados
│   │       │       ├── IncomingMediaHandler.kt # Triaje seguro de Intents ("Abrir con..." y "Compartir con...")
│   │       │       ├── engines/                # Motores Nativos CLI y Actualizador OTA
│   │       │       │   ├── FFmpegNativeEngine.kt   # Extracción y Canvas con prevención de Flag Injection
│   │       │       │   ├── YtDlpNativeEngine.kt    # Entorno nativo de ejecución con TLS/SSL y Android 14+ APEX CA
│   │       │       │   └── YtDlpAutoUpdater.kt     # Actualizador OTA con blindaje SHA-256 verificado
│   │       │       ├── extractors/             # Extractores de Video y Audio Web
│   │       │       │   ├── OnlineVideoAudioImporter.kt # Orquestador de importación web
│   │       │       │   ├── InnerTubeClient.kt          # Cliente directo YouTube InnerTube (<300ms)
│   │       │       │   ├── InvidiousStreamResolver.kt  # Bypass de streams Invidious
│   │       │       │   ├── HeadlessWebViewExtractor.kt # Extractor headless de respaldo con cascada de portadas
│   │       │       │   └── WebStreamExtractor.kt       # Extractor web universal
│   │       │       ├── audio/                  # Procesamiento Acústico, Formatos Especiales y Metadatos
│   │       │       │   ├── AudioMetadataParser.kt      # Extracción de tags ID3, Vorbis y WebP
│   │       │       │   ├── AudioSilenceTrimmer.kt      # Supresión de silencios al inicio y fin (-42 dB RMS)
│   │       │       │   ├── SpecialAudioFormatDecoder.kt # Decodificador nativo DSD, APE, WavPack y Chiptune
│   │       │       │   ├── VideoAudioExtractor.kt      # Conversor Video a Música 3 en 1 (Demuxing directo)
│   │       │       │   └── SampleMusicGenerator.kt     # Generador procedural de pistas demo
│   │       │       ├── artwork/                # Procesamiento de Portadas WebP y Arte Procedural
│   │       │       │   ├── MediaAssetProcessor.kt          # Procesamiento de carátulas WebP y Canvas
│   │       │       │   └── ProceduralArtworkGenerator.kt   # Carátulas matemáticas procedurales
│   │       │       ├── lyrics/                 # Motor de Letras Sincronizadas (.LRC / .TXT)
│   │       │       │   └── LyricsManager.kt    # Descarga de letras sincronizadas LRCLIB y caché
│   │       │       ├── mixtape/                # Motor de Fusión y Capítulos de Mixtape
│   │       │       │   └── MixtapeEngine.kt    # Fusión FFmpeg acrossfade, collage WebP y capítulos
│   │       │       ├── download/               # Acelerador de Descargas HTTP Range
│   │       │       │   └── ChunkedStreamDownloader.kt # Descarga acelerada HTTP Range multi-bloque
│   │       │       └── tiktok/                 # Resolución Directa de TikTok
│   │       │           └── TikTokMediaResolver.kt # Extracción libre de audio/video TikTok
│   │       │
│   │       ├── widget/                         # Widgets Interactivos de Escritorio
│   │       │   └── AuraMusicWidgetProvider.kt  # AppWidgetProvider con Material You, carátula y Bit-Perfect
│   │       │
│   │       ├── playback/                       # Capa de Audio y Reproducción ExoPlayer
│   │       │   ├── AuraAudioPlayer.kt          # Fachada del reproductor de audio
│   │       │   ├── AuraMediaPlaybackService.kt # MediaSessionService para controles del sistema
│   │       │   ├── AuraDownloadService.kt      # Servicio en primer plano para descargas
│   │       │   ├── AudioEffectManager.kt       # Gestión de ecualizador y efectos nativos
│   │       │   ├── HeadphoneController.kt      # BroadcastReceiver para audífonos y Becoming Noisy
│   │       │   ├── NativeAudioEngine.kt        # Interfaz JNI C++20
│   │       │   ├── NativeAudioProcessor.kt     # Pipe de ExoPlayer hacia DSP C++20
│   │       │   └── controllers/
│   │       │       ├── PlayerQueueController.kt # Gestión de cola y modos de repetición/aleatorio
│   │       │       ├── AudioFadeController.kt  # Fundidos de entrada, salida y Sleep Timer
│   │       │       ├── ABLoopController.kt     # Bucle de repetición de segmento A-B
│   │       │       └── MediaSessionBridge.kt   # Notificación nativa System Media Controls
│   │       │
│   │       ├── viewmodel/                      # Capa ViewModel y Coordinadores
│   │       │   ├── MusicViewModel.kt           # ViewModel principal desacoplado
│   │       │   └── delegates/
│   │       │       ├── AudioEffectsCoordinator.kt       # Coordinador de efectos C++20 y Sleep Timer
│   │       │       ├── NavigationCoordinator.kt         # Coordinador de navegación, temas y modos visuales
│   │       │       ├── TrackLibraryCoordinator.kt       # Operaciones de pistas, playlists y descargas
│   │       │       ├── LyricsCoordinator.kt             # Coordinación de letras sincronizadas
│   │       │       ├── IncomingMediaCoordinator.kt      # Recepción de Intents externos
│   │       │       ├── HeadphoneSettingsCoordinator.kt  # Ajustes de acústica, Bit-Perfect y auriculares
│   │       │       └── MixtapeCoordinator.kt            # Coordinador reactivo de Mixtapes y capítulos
│   │       │
│   │       ├── model/                          # Modelos de Dominio y Datos
│   │       │   ├── Track.kt
│   │       │   ├── Playlist.kt
│   │       │   ├── AppUpdateModels.kt               # Modelos de estado y metadatos de actualización APK
│   │       │   ├── MixtapeModels.kt                 # Modelos de capítulos y metadatos de Mixtape
│   │       │   ├── LyricsState.kt
│   │       │   ├── LyricSearchResult.kt
│   │       │   ├── EqualizerConfig.kt
│   │       │   ├── AudioEffectsConfig.kt
│   │       │   ├── HeadphoneConfig.kt
│   │       │   ├── DownloadProgress.kt
│   │       │   ├── PackageUpdateState.kt
│   │       │   ├── RepeatMode.kt
│   │       │   ├── ThemePalette.kt
│   │       │   ├── VideoDisplayMode.kt
│   │       │   └── NowPlayingDesignMode.kt          # Modos de diseño: Clásico, Cinemático Canvas y Auto
│   │       │
│   │       ├── ui/                             # Capa de Interfaz de Usuario (Compose M3)
│   │       │   ├── AuraMusicAppContent.kt      # Orquestador del Scaffold y navegación principal
│   │       │   ├── GlobalDialogsHost.kt        # Contenedor modular de diálogos globales
│   │       │   │
│   │       │   ├── navigation/
│   │       │   │   └── NavScreen.kt            # Destinos de navegación
│   │       │   │
│   │       │   ├── screens/                    # Pantallas de la aplicación
│   │       │   │   ├── home/
│   │       │   │   │   └── HomeScreen.kt
│   │       │   │   ├── library/
│   │       │   │   │   ├── LibraryScreen.kt
│   │       │   │   │   └── components/
│   │       │   │   ├── nowplaying/
│   │       │   │   │   ├── NowPlayingScreen.kt
│   │       │   │   │   └── components/
│   │       │   │   │       ├── FullScreenLyricsScreen.kt # Modo Karaoke Inmersivo a Pantalla Completa
│   │       │   │   │       ├── NowPlayingCinematicLayout.kt # Diseño Cinemático Canvas (Estilo Spotify)
│   │       │   │   │       ├── NowPlayingDesignSelectorDialog.kt # Selector de Diseño de Reproductor
│   │       │   │   │       ├── NowPlayingArtworkCard.kt
│   │       │   │   │       ├── NowPlayingLyricsCard.kt
│   │       │   │   │       ├── NowPlayingPlaybackControls.kt
│   │       │   │   │       ├── NowPlayingQueueSheet.kt
│   │       │   │   │       ├── NowPlayingTopBar.kt
│   │       │   │   │       ├── NowPlayingBalanceBar.kt
│   │       │   │   │       ├── AudioSpecsDialog.kt
│   │       │   │   │       └── VideoDisplayModeDialog.kt
│   │       │   │   ├── settings/
│   │       │   │   │   ├── SettingsScreen.kt   # Orquestador de Ajustes y navegación a pantallas completas
│   │       │   │   │   └── components/
│   │       │   │   │       ├── SettingDetailRow.kt            # Fila reutilizable de información de ajuste
│   │       │   │   │       ├── SettingsSubScreenComponents.kt # Tarjetas navegables y contenedor de pantalla completa
│   │       │   │   │       ├── appearance/                    # Ajustes de Apariencia, Temas y Diseño del Reproductor
│   │       │   │   │       │   ├── AppearanceSettingsTab.kt       # Menú principal de Ajustes y secciones dedicadas
│   │       │   │   │       │   └── PlayerDesignSettingsContent.kt # Pantalla dedicada de Diseño del Reproductor
│   │       │   │   │       ├── headphones/                    # Ajustes de Auriculares, Acústica DSP, Bit-Perfect y Gestos
│   │       │   │   │       │   ├── BitPerfectSettingsSection.kt   # Configuración Bit-Perfect 1:1 y Google AAudio
│   │       │   │   │       │   ├── HeadphoneStatusCard.kt         # Tarjeta de estado de conexión de audífonos
│   │       │   │   │       │   ├── HeadphonesAcousticsSection.kt  # Crossfeed Chu Moy y Balance Estéreo L/R
│   │       │   │   │       │   ├── HeadphonesSecuritySection.kt   # Becoming Noisy Guard, Fade-In y Memoria de Volumen
│   │       │   │   │       │   ├── HeadphonesGesturesSection.kt   # Mapeo de 1, 2, 3 clics y pulsación larga
│   │       │   │   │       │   └── HeadsetButtonActionDialog.kt   # Selector modal de acción de botón de auricular
│   │       │   │   │       ├── updater/                       # Actualizador Automático de Versiones APK
│   │       │   │   │       │   └── AppReleaseUpdateSettingsCard.kt # Tarjeta de verificación y descarga de APK Beta
│   │       │   │   │       └── media/                         # Gestión y Transparencia de Medios Almacenados
│   │       │   │   │           ├── StoredMediaSettingsTab.kt      # Pestaña Medios modularizada
│   │       │   │   │           ├── SavedMediaStorageSection.kt    # Resumen de rutas y almacenamiento físico
│   │       │   │   │           ├── StoredMediaFormatUtils.kt      # Formateo de pesos KB/MB y proporciones
│   │       │   │   │           ├── StoredMediaSummaryHeader.kt    # Cabecera estadística de medios guardados
│   │       │   │   │           ├── StoredMediaTrackCard.kt        # Tarjeta individual de pista con carátula/video
│   │       │   │   │           └── StoredMediaDeleteDialogs.kt    # Diálogos de confirmación de borrado físico
│   │       │   │   ├── playlist/
│   │       │   │   │   └── PlaylistDetailScreen.kt
│   │       │   │   ├── importmusic/
│   │       │   │   │   └── ImportMusicScreen.kt
│   │       │   │   └── onboarding/
│   │       │   │       ├── OnboardingScreen.kt
│   │       │   │       └── OnboardingStepComponents.kt
│   │       │   │
│   │       │   └── components/                 # Componentes y Widgets Modulares Organizados
│   │       │       ├── MiniPlayer.kt           # Mini reproductor flotante persistente con Video Canvas
│   │       │       ├── BottomNavBar.kt         # Barra de navegación inferior con insignia de descubrimiento
│   │       │       ├── TrackListItem.kt        # Elemento modular de canción con marquesina
│   │       │       ├── AudioEffectsBottomSheet.kt # Hoja modal unificada del estudio acústico C++20
│   │       │       ├── artwork/                # Portadas, Collages, Arte Procedural, Visualizador y Video Canvas
│   │       │       │   ├── ArtworkImage.kt         # Renderizador WebP con recorte de franjas negras 4:3
│   │       │       │   ├── PlaylistCoverCollage.kt # Collage dinámico de 1 a 4 fotos para Playlists
│   │       │       │   ├── ProceduralArtwork.kt    # Lienzo matemático vectorial para pistas sin carátula
│   │       │       │   ├── BackgroundVideoPlayer.kt # Reproductor Video Canvas en bucle o sincronizado
│   │       │       │   └── AudioVisualizer.kt      # Visualizador espectral de 28 bandas en C++20
│   │       │       ├── banners/                # Avisos Superiores y Banners Interactivos
│   │       │       │   ├── PackageUpdateBanner.kt  # Banner de verificación y actualización OTA de yt-dlp
│   │       │       │   └── SettingsDiscoveryBanner.kt # Aviso de recomendación de Ajustes para nuevos usuarios
│   │       │       ├── dialogs/                # Diálogos Modales Globales
│   │       │       │   ├── AppUpdateDialog.kt          # Modal de actualización automática de versión APK
│   │       │       │   ├── CreateMixtapeDialog.kt      # Diálogo de creación de Mixtape continuo en FFmpeg
│   │       │       │   ├── DownloadFromLinkDialog.kt   # Diálogo de descarga web (YouTube/TikTok) modularizado
│   │       │       │   ├── EditTrackDialog.kt          # Editor de metadatos, carátula WebP y Video Canvas
│   │       │       │   ├── SearchLyricsDialog.kt       # Buscador interactivo de letras LRCLIB con versión oficial #1
│   │       │       │   └── VideoToMusicDialog.kt       # Conversor 3 en 1 de Video a Música (.m4a + WebP + Canvas)
│   │       │       ├── audioeffects/           # Pestañas de efectos de audio
│   │       │       └── download/               # Subcomponentes de descarga
│   │       │           ├── DownloadProgressStatusCard.kt
│   │       │           ├── DownloadSourceAndEngineSelector.kt
│   │       │           ├── DownloadResolvedMediaCard.kt
│   │       │           └── DownloadCanvasOptionsSection.kt
│   │       │
│   │       └── debug/                          # Suite Aura Monitor y Diagnósticos
│   │           ├── AuraDebugManager.kt
│   │           ├── DebugMonitorActivity.kt
│   │           ├── PerformanceTelemetryManager.kt
│   │           └── ui/
```

---

## 🛡️ Blindaje y Solución de Vulnerabilidades

1. **Omisión Silenciosa de SHA-256 en Actualización OTA (`YtDlpAutoUpdater.kt`)**:
   - `expectedSha256` es de verificación estricta obligatoria.
   - Si no se obtiene la suma criptográfica oficial desde `SHA2-256SUMS`, la actualización se cancela inmediatamente con registro de incidente en Aura Monitor.
   - No se permite la instalación de binarios ejecutables sin validación de integridad previa.

2. **Desactivación Global de Verificación TLS/SSL en yt-dlp (`YtDlpNativeEngine.kt`)**:
   - Eliminado el argumento permisivo `--no-check-certificates` tanto en la ejecución inicial como en el bloque de reintento ante errores SSL.
   - Inyección rigurosa y reconstrucción bajo demanda (`forceRebuild = true`) del almacén de certificados CA oficial (`cert.pem` embebido local en `/usr/etc/tls/cert.pem`, Conscrypt APEX `/apex/com.android.conscrypt/cacerts` y `/system/etc/security/cacerts`).

3. **Blindaje de Memoria Nativa C++20 y Sincronización Thread-Safe (`auramusic_dsp.cpp`)**:
   - Validación estricta de capacidad en `DirectByteBuffer` mediante `GetDirectBufferCapacity(byteBuffer)` previniendo lecturas o escrituras fuera de límites (*Buffer Over-read / Over-write*).
   - Sincronización mediante `std::mutex` (`sDspMutex`) entre el hilo de audio PCM de ExoPlayer y los ajustes en tiempo real de la interfaz.

4. **Protección contra Path Traversal, File Stealing y SSRF (`AppStorageManager.kt`, `IncomingMediaHandler.kt`, `InvidiousStreamResolver.kt`, `TikTokMediaResolver.kt`)**:
   - Resolución canónica obligatoria (`resolveSafeChildFile` e `isInsideDirectoryCanonical`) en `AppStorageManager.kt` al guardar y eliminar canciones, carátulas, letras y metadatos.
   - Bloqueo de ataques *File Stealing* por enlaces simbólicos o URIs que apunten al sandbox privado interno (`dataDir`, `filesDir`, `cacheDir`) en `IncomingMediaHandler.kt` y eliminación del esquema inseguro `file://` en `AndroidManifest.xml`.
   - Exclusión de binarios ejecutables (`bin/`, `env/`) y logs en `backup_rules.xml` y `data_extraction_rules.xml`.
   - Validación estricta de esquema `https://` y bloqueo de direcciones IP privadas/loopback (anti-SSRF) en `InvidiousStreamResolver.kt` y `TikTokMediaResolver.kt`.

5. **Riesgo de Colisión de Nombres y Extensiones en SAF (`SafTrackImporter.kt`)**:
   - Normalización de nombres de archivo y sanitización contra secuencias de escape y caracteres peligrosos (`..`, `/`, `\`).
   - Mapeo estricto del MIME Type hacia extensiones de audio permitidas (`mp3`, `m4a`, `flac`, `wav`, `ogg`, `opus`, `aac`).
   - Desambiguación con sufijos UUID aleatorios (`_a1b2c3`) para evitar sobreescritura accidental de pistas previas.

6. **Prevención de Inyección de Opciones (Flag Injection) en FFmpeg (`FFmpegNativeEngine.kt`)**:
   - Sanitización de rutas canónicas de entrada y salida mediante `sanitizeFilePath()`.
   - Rechazo de rutas que comiencen con `-` o contengan saltos de línea/espacios sospechosos.
   - Inserción del delimitador POSIX `--` antes de argumentos posicionales en invocaciones nativas para neutralizar la interpretación accidental de rutas como banderas de línea de comandos.

7. **Sandbox Estricto en WebView, Guardián Anti-SSRF/DNS Rebinding y Autenticación IPC (`HeadlessWebViewExtractor.kt`, `ChunkedStreamDownloader.kt`, `AuraMediaPlaybackService.kt`, `MainActivity.kt`, `native_ffmpeg_launcher.c`)**:
   - Desactivación explícita de `allowFileAccess`, `allowContentAccess`, `allowFileAccessFromFileURLs` y `allowUniversalAccessFromFileURLs` en `HeadlessWebViewExtractor`, más validación de `videoId` y `shouldOverrideUrlLoading`.
   - Interceptor anti-SSRF / anti-DNS Rebinding en `ChunkedStreamDownloader` y comprobación estricta del host real parseado antes de adjuntar cookies de sesión.
   - Autorización de paquete/UID en `AuraMediaPlaybackService.onGetSession()` y token criptográfico interno (`INTERNAL_IPC_AUTH_TOKEN`) para comandos del Widget y notificaciones en `MainActivity`.
   - Verificación con `stat()` en C (`native_ffmpeg_launcher.c` y `native_python_launcher.c`) rechazando bibliotecas escribibles por terceros (`S_IWOTH`) antes de `dlopen()`.

8. **Escáner Automatizado Manual en GitHub Actions (`.github/workflows/security-vulnerability-audit.yml`)**:
   - Flujo `workflow_dispatch` ("Escáner de Seguridad y Vulnerabilidades (Aura Shield)") con análisis multicapa (Gitleaks, Google OSV-Scanner, Semgrep Kotlin/C++20, Invariantes de Seguridad de Aura Music y Android Lint opcional) y reporte ejecutivo en español en `$GITHUB_STEP_SUMMARY` optimizado para lectura desde teléfono móvil.

