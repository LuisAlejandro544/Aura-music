# Estructura del Proyecto Aura Music 🎧

Este documento detalla la arquitectura de software, organización de módulos, responsabilidades y componentes de **Aura Music**.

---

## 🏛️ Visión General de Arquitectura (MVVM + Clean Architecture)

El proyecto sigue una arquitectura reactiva y desacoplada organizada en capas:

1. **Capa Nativa (C++20 / NDK)**: Motores de procesamiento de audio digital DSP en tiempo real (Ecualizador paramétrico de 10 bandas IIR, Audio 8D/16D Multi-Órbita, Clarificador de Voces Mid-Side HD, Reverb Schroeder/Moorer y Crossfeed Chu Moy) + binarios nativos optimizados de FFmpeg y QuickJS/CPython.
2. **Capa de Datos & Almacenamiento (Data & Storage)**:
   - Base de datos SQLite reactiva con **Room**.
   - Acceso al almacenamiento estructurado en `Android/data/com.aistudio.musicplayer.aurasound/files/` (`songs/`, `images/`, `lyrics/`, `metadata/`, `videos/`).
   - Motores de importación, sincronización de letras LRCLIB, extracción FFmpeg y descarga resiliente (Chunked Range Download, InnerTube, Invidious y fallback `yt-dlp` blindado).
3. **Capa de Control & Reproducción (Playback Layer)**:
   - `AuraAudioPlayer` con ExoPlayer/Media3, modularizado con controladores especializados (`PlayerQueueController`, `AudioFadeController`, `ABLoopController`, `MediaSessionBridge`).
   - `NativeAudioEngine` conectando buffers de audio PCM en C++20 vía JNI.
4. **Capa de Lógica de Negocio & Estado (ViewModel Layer)**:
   - `MusicViewModel` modularizado y ultra liviano (< 400 líneas), orquestado por coordinadores y delegados especializados:
     - `TrackLibraryCoordinator`: CRUD de pistas y playlists, importación SAF de archivos/carpetas y canciones de muestra.
     - `LyricsCoordinator`: Gestión de letras locales y remotas LRCLIB.
     - `IncomingMediaCoordinator`: Recepción "Abrir con..." y "Compartir con...".
     - `HeadphoneSettingsCoordinator`: Detección acústica de auriculares, guardado y balance estéreo.
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
│   │       │   └── importer/                   # Motores de Descarga, Conversión y Medios
│   │       │       ├── mixtape/                    # Motor de Fusión y Capítulos de Mixtape
│   │       │       │   └── MixtapeEngine.kt        # Fusión FFmpeg acrossfade, collage WebP y capítulos
│   │       │       ├── FFmpegNativeEngine.kt   # Extracción y Canvas con prevención de Flag Injection
│   │       │       ├── YtDlpNativeEngine.kt    # Entorno nativo de ejecución con verificación TLS/SSL
│   │       │       ├── YtDlpAutoUpdater.kt     # Actualizador OTA con blindaje SHA-256 verificado
│   │       │       ├── OnlineVideoAudioImporter.kt # Orquestador de importación web
│   │       │       ├── InnerTubeClient.kt      # Cliente directo YouTube InnerTube
│   │       │       ├── InvidiousStreamResolver.kt # Bypass de streams Invidious
│   │       │       ├── HeadlessWebViewExtractor.kt # Extractor headless de respaldo
│   │       │       ├── AudioMetadataParser.kt  # Extracción de tags ID3, Vorbis y WebP
│   │       │       ├── AudioSilenceTrimmer.kt  # Supresión de silencios al inicio y fin
│   │       │       ├── LyricsManager.kt        # Descarga de letras sincronizadas LRCLIB y caché
│   │       │       ├── MediaAssetProcessor.kt  # Procesamiento de carátulas WebP y Canvas
│   │       │       ├── ProceduralArtworkGenerator.kt # Carátulas matemáticas procedurales
│   │       │       ├── SampleMusicGenerator.kt # Generador procedural de pistas demo
│   │       │       ├── download/
│   │       │       │   └── ChunkedStreamDownloader.kt # Descarga acelerada HTTP Range multi-bloque
│   │       │       └── tiktok/
│   │       │           └── TikTokMediaResolver.kt # Extracción libre de audio/video TikTok
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
│   │       │   ├── MusicViewModel.kt           # ViewModel principal desacoplado (< 400 líneas)
│   │       │   └── delegates/
│   │       │       ├── TrackLibraryCoordinator.kt       # Operaciones de pistas y playlists
│   │       │       ├── LyricsCoordinator.kt             # Coordinación de letras sincronizadas
│   │       │       ├── IncomingMediaCoordinator.kt      # Recepción de Intents externos
│   │       │       ├── HeadphoneSettingsCoordinator.kt  # Ajustes de acústica y auriculares
│   │       │       └── MixtapeCoordinator.kt            # Coordinador reactivo de Mixtapes y capítulos
│   │       │
│   │       ├── model/                          # Modelos de Dominio y Datos
│   │       │   ├── Track.kt
│   │       │   ├── Playlist.kt
│   │       │   ├── MixtapeModels.kt                 # Modelos de capítulos y metadatos de Mixtape
│   │       │   ├── LyricsState.kt
│   │       │   ├── EqualizerConfig.kt
│   │       │   ├── AudioEffectsConfig.kt
│   │       │   ├── HeadphoneConfig.kt
│   │       │   ├── DownloadProgress.kt
│   │       │   ├── PackageUpdateState.kt
│   │       │   ├── ThemePalette.kt
│   │       │   └── VideoDisplayMode.kt
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
│   │       │   │   ├── settings/
│   │       │   │   │   ├── SettingsScreen.kt
│   │       │   │   │   └── components/
│   │       │   │   │       ├── StoredMediaSettingsTab.kt # Pestaña Medios modularizada
│   │       │   │   │       └── media/
│   │       │   │   │           ├── StoredMediaFormatUtils.kt
│   │       │   │   │           ├── StoredMediaSummaryHeader.kt
│   │       │   │   │           ├── StoredMediaTrackCard.kt
│   │       │   │   │           └── StoredMediaDeleteDialogs.kt
│   │       │   │   ├── playlist/
│   │       │   │   │   └── PlaylistDetailScreen.kt
│   │       │   │   ├── importmusic/
│   │       │   │   │   └── ImportMusicScreen.kt
│   │       │   │   └── onboarding/
│   │       │   │       ├── OnboardingScreen.kt
│   │       │   │       └── OnboardingStepComponents.kt
│   │       │   │
│   │       │   └── components/                 # Componentes y Widgets Modulares
│   │       │       ├── MiniPlayer.kt
│   │       │       ├── BottomNavBar.kt
│   │       │       ├── TrackListItem.kt
│   │       │       ├── ArtworkImage.kt
│   │       │       ├── ProceduralArtwork.kt
│   │       │       ├── AudioVisualizer.kt
│   │       │       ├── BackgroundVideoPlayer.kt
│   │       │       ├── EditTrackDialog.kt
│   │       │       ├── CreateMixtapeDialog.kt  # Diálogo de creación de Mixtape continuo
│   │       │       ├── SearchLyricsDialog.kt
│   │       │       ├── VideoToMusicDialog.kt
│   │       │       ├── PackageUpdateBanner.kt
│   │       │       ├── AudioEffectsBottomSheet.kt
│   │       │       ├── audioeffects/           # Pestañas de efectos de audio
│   │       │       ├── DownloadFromLinkDialog.kt # Diálogo de descarga modularizado
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
   - Eliminado el argumento permisivo `--no-check-certificates`.
   - Inyección rigurosa del almacén de certificados CA oficial (`cert.pem` embebido local en `/usr/etc/tls/cert.pem` o los certificados del sistema Android en `/system/etc/security/cacerts`).

3. **Riesgo de Colisión de Nombres y Extensiones en SAF (`SafTrackImporter.kt`)**:
   - Normalización de nombres de archivo y sanitización contra secuencias de escape y caracteres peligrosos (`..`, `/`, `\`).
   - Mapeo estricto del MIME Type hacia extensiones de audio permitidas (`mp3`, `m4a`, `flac`, `wav`, `ogg`, `opus`, `aac`).
   - Desambiguación con sufijos UUID aleatorios (`_a1b2c3`) para evitar sobreescritura accidental de pistas previas.

4. **Prevención de Inyección de Opciones (Flag Injection) en FFmpeg (`FFmpegNativeEngine.kt`)**:
   - Sanitización de rutas canónicas de entrada y salida mediante `sanitizeFilePath()`.
   - Rechazo de rutas que comiencen con `-` o contengan saltos de línea/espacios sospechosos.
   - Inserción del delimitador POSIX `--` antes de argumentos posicionales en invocaciones nativas para neutralizar la interpretación accidental de rutas como banderas de línea de comandos.
