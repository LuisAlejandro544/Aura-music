# Estructura del Proyecto Aura Music 🎧

Este documento detalla la arquitectura de software, organización de módulos, responsabilidades y componentes de **Aura Music**.

---

## 🏛️ Visión General de Arquitectura (MVVM + Clean Architecture)

El proyecto sigue una arquitectura reactiva y desacoplada organizada en capas:

1. **Capa Nativa (C++20 / NDK)**: Motores de procesamiento de audio digital DSP en tiempo real (Ecualizador paramétrico de 10 bandas IIR, Audio 8D/16D Multi-Órbita, Clarificador de Voces Mid-Side HD, Reverb Schroeder/Moorer y Crossfeed Chu Moy) + binarios nativos optimizados de FFmpeg y QuickJS/CPython.
2. **Capa de Datos & Almacenamiento (Data & Storage)**:
   - Base de datos SQLite reactiva con **Room**.
   - Acceso al almacenamiento estructurado en `Android/data/com.aistudio.musicplayer.aurasound/files/` (Debug) y `Android/data/com.auramusic.beta/files/` (APK Beta `Aura Beta` • Codename `Nebula`) con subcarpetas (`songs/`, `images/`, `lyrics/`, `metadata/`, `videos/`), además de **Carpeta Pública / Personalizada del Usuario (`UserPublicMediaExporter` & `UserPublicFolderCard`)** mediante SAF (`OpenDocumentTree`) con subcarpeta `Videos/` (y preparada para `Images/`) para exportar vídeos descargados con su audio completo.
   - Motores de importación, sincronización de letras LRCLIB, extracción FFmpeg, descarga directa prioritaria con motor nativo yt-dlp (`YtDlpStreamDownloader`) con métricas de progreso en tiempo real y respaldo resiliente multi-nivel con separación de `audioHeaders`/`videoHeaders` por perfil `c=` (`ChunkedStreamDownloader` -> `InnerTubeClient` -> `InvidiousStreamResolver` -> `HeadlessWebViewExtractor`), arquitectura modular desacoplada en `engines/` (< 500 líneas por módulo), y actualizador automático de versiones APK (`AppReleaseUpdater` con comparador semántico de tags dinámicos `-beta`, selección por arquitectura móvil y verificación SHA-256).
3. **Capa de Control & Reproducción (Playback Layer)**:
   - `AuraAudioPlayer` con ExoPlayer/Media3, modularizado con controladores especializados (`PlayerQueueController`, `AudioFadeController`, `ABLoopController`, `MediaSessionBridge`) y coordinadores de reproducción (`PlaybackProgressTracker`, `StreamingErrorRecoveryHandler`, `PlaybackParametersManager`, `StreamingUrlPolicy`).
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
      - `StreamingModeCoordinator`: Orquestador del Modo Streaming tri-plataforma (YT Music, YouTube & SoundCloud), caché inteligente y Radio Similar.
      - `PlaylistImportCoordinator`: Orquestador del Importador Universal de Playlists de Spotify y YouTube Music / YouTube (Modo Streaming Instantáneo o Descarga Offline en 2do plano).
5. **Capa de Presentación (UI con Jetpack Compose & Material 3)**:
   - Pantallas modulares (`HomeScreen`, `LibraryScreen`, `NowPlayingScreen`, `SettingsScreen`, `ImportMusicScreen`, `OnboardingScreen`, `PlaylistDetailScreen`).
   - Host global modularizado (`AuraMusicAppContent`, `GlobalDialogsHost`).
   - Componentes modulares reutilizables organizados en submódulos temáticos.

---

## 📂 Árbol de Directorios y Módulos

```text
zips/                                           # Carpeta receptora de paquetes .zip para sincronización automática de ramas
├── README.md                                   # Guía rápida de uso desde dispositivos móviles
└── .gitkeep                                    # Mantiene la carpeta visible tras auto-eliminar los .zip procesados

app/
├── build.gradle.kts                            # Configuración Gradle, NDK C++20 y tarea provisionNativeDeps
├── proguard-rules.pro                          # Reglas de optimización R8 Full Mode y ofuscación Beta/Release
└── src/
    ├── beta/                                   # Recursos y Manifiesto exclusivos del canal APK Beta (Aura Beta • Nebula)
    │   ├── AndroidManifest.xml                 # Remueve DebugMonitorActivity en compilaciones Beta
    │   └── res/values/strings.xml              # Nombre de app "Aura Beta"
    │
    ├── main/
    │   ├── AndroidManifest.xml                 # Manifiesto principal, servicios Media3/Descargas, Widgets y Android Auto
    │   ├── cpp/                                # Núcleo C++20 Nativo DSP & Launchers PIE (QuickJS C99 puro compilado en Gradle)
    │   │   ├── CMakeLists.txt                  # Configuración CMake C17 / ISO C++20 con optimización LTO y AAudio
    │   │   ├── auramusic_dsp.h                 # Fachada pública del motor DSP de 64 bits
    │   │   ├── auramusic_dsp.cpp               # Implementación JNI thread-safe (std::mutex) y ruteo de buffers std::span
    │   │   ├── dsp_filters.h                   # Filtros Biquad IIR, EQ 10 bandas, Vocal Clarity Mid-Side, EBU R128 y DJ Automix
    │   │   ├── dsp_spatial.h                   # Paneo 8D Clásico y Audio Espacial 16D Multi-Órbita Binaural
    │   │   ├── dsp_crossfeed.h                 # Filtro Crossfeed acústico binaural Chu Moy / Bauer para auriculares
    │   │   ├── dsp_reverb.h                    # Reverb digital Schroeder/Moorer en C++20 con líneas de retardo libres de bloqueo
    │   │   ├── dsp_bitperfect.h                # Motor Bit-Perfect 1:1 y salida Google AAudio Ultra-Baja Latencia
    │   │   ├── native_ffmpeg_launcher.c        # Lanzador PIE POSIX con validación stat() y dlopen hacia FFmpeg nativo
    │   │   ├── native_python_launcher.c        # Lanzador PIE POSIX embebedor de CPython 3.11 nativo
    │   │   └── aura_ytdlp_fallback.py          # Script Python de respaldo para extracción directa si falla la red en build
    │   │
    │   └── java/com/example/
    │       ├── AuraApplication.kt              # Inicialización global, ciclo de vida Modo Juego, OTA yt-dlp y limpieza OEM
    │       ├── MainActivity.kt                 # Actividad principal modularizada (< 140 líneas), permisos e Intents
    │       │
    │       ├── core/                           # Núcleo de Datos, Modelos de Dominio y Motor de Reproducción
    │       │   ├── data/
    │       │   │   ├── local/                  # Capa de Persistencia Local (Room SQLite)
    │       │   │   │   ├── AppDatabase.kt      # Base de datos Room con migraciones
    │       │   │   │   ├── dao/
    │       │   │   │   │   ├── TrackDao.kt
    │       │   │   │   │   └── PlaylistDao.kt
    │       │   │   │   └── entity/
    │       │   │   │       ├── TrackEntity.kt
    │       │   │   │       ├── PlaylistEntity.kt
    │       │   │   │       └── PlaylistTrackCrossRef.kt
    │       │   │   │
    │       │   │   ├── repository/             # Repositorios y Operaciones de Datos
    │       │   │   │   ├── MusicRepository.kt  # Fachada principal de repositorio
    │       │   │   │   ├── PlaylistRepository.kt # Gestión de listas y cross-references
    │       │   │   │   └── SafTrackImporter.kt # Importador SAF blindado contra colisiones de extensiones
    │       │   │   │
    │       │   │   ├── storage/                # Almacenamiento Interno y Exportación Pública del Usuario
    │       │   │   │   ├── AppStorageManager.kt # Manejo de disco estructurado en Android/data/.../files/
    │       │   │   │   └── UserPublicMediaExporter.kt # Exportador SAF a carpeta pública del usuario con fusión FFmpeg (.mp4 con audio)
    │       │   │   │
    │       │   │   ├── updater/                # Actualizador Automático de Versiones APK
    │       │   │   │   └── AppReleaseUpdater.kt # Fachada que delega en ApkVersionComparator, UpdatePreferences, ApkDownloadManager y UpdateCheckOrchestrator
    │       │   │   │
    │       │   │   ├── importer/               # Motores de Descarga, Conversión y Medios Organizados
    │       │   │   │   ├── IncomingMediaHandler.kt # Triaje seguro de Intents ("Abrir con..." y "Compartir con...")
    │       │   │   │   ├── engines/            # Motores Nativos CLI (FFmpeg, CPython + QuickJS C99 y OTA yt-dlp)
    │       │   │   │   │   ├── FFmpegNativeEngine.kt     # Fachada de audio/video que delega en FFmpegAudioOperations y FFmpegCanvasOperations
   │       │   │   │   ├── FFmpegCommandExecutor.kt      # Ejecutor interno de procesos nativos y extracción ZIP segura
   │       │   │   │   ├── FFmpegAudioOperations.kt      # Extracción, fusión y recorte de audio
   │       │   │   │   ├── FFmpegCanvasOperations.kt     # Loops xfade/Boomerang, lienzo 9:16, GOP corto y probe de dimensiones
    │       │   │   │   │   ├── YtDlpNativeEngine.kt      # Fachada modular nativa yt-dlp, inspección y flat-playlists (< 500 líneas)
    │       │   │   │   │   ├── YtDlpEnvironmentSetup.kt  # Detección de binarios, symlinks CPython 0 bytes y certificados TLS Android 14+
    │       │   │   │   │   ├── YtDlpStreamDownloader.kt  # Descarga directa nativa con captura y parseo de progreso en vivo (DownloadProgress)
    │       │   │   │   │   ├── YtDlpProcessRunner.kt       # Ejecutor común cancelable con entorno unificado y timeout
    │       │   │   │   │   ├── YtDlpAutoUpdater.kt       # Fachada OTA pública (API estable, 99 líneas)
    │       │   │   │   │   ├── YtDlpUpdaterState.kt      # Alcance, mutex, flujo reactivo y clientes HTTP
    │       │   │   │   │   ├── YtDlpPackagePaths.kt      # Rutas files/bin y aplicación de staged + reinicio
    │       │   │   │   │   ├── YtDlpUpdateCheck.kt       # Verificación contra GitHub Releases
    │       │   │   │   │   ├── YtDlpPackageDownload.kt   # Descarga con progreso y blindaje SHA-256
    │       │   │   │   │   ├── YtDlpCliDiagnostics.kt    # Diagnóstico CLI (yt-dlp --version)
    │       │   │   │   │   ├── YtDlpCookieManager.kt     # Gestor opcional de cookies (cookies.txt formato Netscape estilo YTDLnis)
    │       │   │   │   │   └── PoTokenGenerator.kt       # Generador ligero de PO Token Web vinculado a Visitor Data para YouTube/GVS
    │       │   │   │   ├── extractors/         # Extractores de Video y Audio Web
    │       │   │   │   │   ├── OnlineVideoAudioImporter.kt # Orquestador de importación web con cascada anti-HTTP 403 de 5 niveles
    │       │   │   │   │   ├── InnerTubeClient.kt          # Cliente directo YouTube InnerTube multi-perfil (<300ms)
    │       │   │   │   │   ├── InvidiousStreamResolver.kt  # Bypass de streams mediante instancias Invidious
    │       │   │   │   │   ├── HeadlessWebViewExtractor.kt # Extractor WebView móvil en sandbox con cascada de portadas
    │       │   │   │   │   └── WebStreamExtractor.kt       # Extractor web universal y detector de subdominios YouTube
    │       │   │   │   ├── audio/              # Procesamiento Acústico, Formatos Especiales y Metadatos
    │       │   │   │   │   ├── AudioMetadataParser.kt      # Extracción de tags ID3, Vorbis y WebP
    │       │   │   │   │   ├── AudioSilenceTrimmer.kt      # Supresión inteligente de silencios al inicio y fin (-42 dB RMS)
    │       │   │   │   │   ├── SpecialAudioFormatDecoder.kt # Decodificador nativo DSD, APE, WavPack, ALAC, AIFF, MPC y Chiptune
    │       │   │   │   │   ├── VideoAudioExtractor.kt      # Conversor Video a Música 3 en 1 (Demuxing directo en ~1s)
    │       │   │   │   │   └── SampleMusicGenerator.kt     # Generador procedural de pistas sintetizadas demo
    │       │   │   │   ├── artwork/            # Procesamiento de Portadas WebP y Arte Procedural
    │       │   │   │   │   ├── MediaAssetProcessor.kt        # Compresión WebP Lossless, recorte letterbox 4:3 y Video Canvas
    │       │   │   │   │   └── ProceduralArtworkGenerator.kt # Generador matemático de portadas procedurales
    │       │   │   │   ├── lyrics/             # Motor de Letras Sincronizadas (.LRC / .TXT)
    │       │   │   │   │   └── LyricsManager.kt # Búsqueda y descarga de letras sincronizadas LRCLIB con versión oficial #1
    │       │   │   │   ├── mixtape/            # Fusión Continua y Auto-Remix / Mashup de 2 Canciones Locales
    │       │   │   │   │   ├── MixtapeEngine.kt        # Fusión FFmpeg acrossfade, collage WebP y capítulos reactivos
    │       │   │   │   │   ├── SmartAutoRemixEngine.kt # Fachada que delega en AutoRemixMatcher, AutoRemixPlanner y AutoRemixPostProcessor
    │       │   │   │   ├── AutoRemixMatcher.kt       # Selección del mejor par y validación de archivos
    │       │   │   │   ├── AutoRemixPlanner.kt       # Grafos FFmpeg Mashup/DJ y ejecución con progreso
    │       │   │   │   └── AutoRemixPostProcessor.kt # Collage WebP, LRC continuo y metadatos
    │       │   │   │   ├── playlist/           # Importador Universal de Playlists Públicas
    │       │   │   │   │   └── ExternalPlaylistImporter.kt # Fachada que delega en SpotifyPlaylistExtractor y YouTubePlaylistExtractor
   │       │   │   │   ├── PlaylistHttpClient.kt         # Cliente HTTP/JSON interno (GET/POST, redirects, unescape)
   │       │   │   │   ├── SpotifyPlaylistExtractor.kt   # Embed/oEmbed + WebView sin login ni API keys
   │       │   │   │   └── YouTubePlaylistExtractor.kt   # InnerTube browse + respaldo yt-dlp --flat-playlist
    │       │   │   │   ├── download/           # Acelerador de Descargas HTTP Range
    │       │   │   │   │   └── ChunkedStreamDownloader.kt # Descarga acelerada HTTP Range multi-bloque con guardia anti-SSRF
    │       │   │   │   └── tiktok/             # Resolución Directa de TikTok
    │       │   │   │       └── TikTokMediaResolver.kt # Extracción libre de audio/video de TikTok sin marcas de agua
    │       │   │   │
    │       │   │   └── streaming/              # Modo Streaming Híbrido Tri-Plataforma (YT Music + YouTube + SoundCloud)
    │       │   │       ├── StreamingApiService.kt           # Fachada que delega en los motores de búsqueda y utils
    │       │   │       ├── YouTubeMusicSearchEngine.kt      # Búsqueda YT Music (WEB_REMIX) y parseo
    │       │   │       ├── YouTubeSearchEngine.kt           # Búsqueda YouTube (WEB), Watch Next y recomendaciones
    │       │   │       ├── StreamingSearchUtils.kt          # Parseo JSON, intercalado tri-plataforma y POST HTTP
    │       │   │       ├── SoundCloudApiClient.kt           # Cliente modular de SoundCloud (hidratación client_id v2 y streams)
    │       │   │       ├── StreamingCacheManager.kt         # Caché inteligente (50-500 MB, 30 min TTL, LRU) y Pre-descarga No Lineal
    │       │   │       └── StreamingHttpDataSourceConfig.kt # Alineación de cabeceras User-Agent/Origin por perfil firmado c=
    │       │   │
    │       │   ├── model/                      # Modelos de Dominio y Estado
    │       │   │   ├── Track.kt
    │       │   │   ├── Playlist.kt
    │       │   │   ├── StreamingModels.kt      # Modelos de búsqueda streaming, importador de playlists y políticas de red
    │       │   │   ├── WidgetModels.kt         # Modelos de configuración y estado persistido de los 2 Widgets
    │       │   │   ├── AppUpdateModels.kt      # Modelos de estado y metadatos del actualizador APK
    │       │   │   ├── MixtapeModels.kt        # Modelos de capítulos, metadatos de Mixtape y configuración de Auto-Remix
    │       │   │   ├── LyricsState.kt
    │       │   │   ├── LyricSearchResult.kt
    │       │   │   ├── EqualizerConfig.kt
    │       │   │   ├── AudioEffectsConfig.kt
    │       │   │   ├── HeadphoneConfig.kt
    │       │   │   ├── DownloadProgress.kt
    │       │   │   ├── PackageUpdateState.kt
    │       │   │   ├── RepeatMode.kt
    │       │   │   ├── ThemePalette.kt
    │       │   │   ├── AppWallpaperConfig.kt   # Configuración de fondo de pantalla personalizado de galería (WebP/MP4)
    │       │   │   ├── VideoDisplayMode.kt
    │       │   │   └── NowPlayingDesignMode.kt # Modos de diseño del reproductor: Clásico, Cinemático Canvas y Auto
    │       │   │
    │       │   └── playback/                   # Capa de Audio y Reproducción ExoPlayer / Media3
    │       │       ├── AuraAudioPlayer.kt          # Fachada del reproductor (estado + delegados, 297 líneas)
    │       │       ├── PlayerEngineSetup.kt        # Construcción ExoPlayer Deck A/B, listeners y ajustes persistidos
    │       │       ├── PlayerTrackStarter.kt       # Arranque de pistas con handoff dual-deck y DSP
    │       │       ├── PlayerTransportControls.kt  # Transporte, saltos y fin de pista con repetición
    │       │       ├── PlayerTransitionSettings.kt # Persistencia de crossfade, automix, EQ y gapless
    │       │       ├── PlayerQueueOperations.kt    # Operaciones de cola con espejo en widgets
    │       │       ├── PlayerReleaseHandler.kt     # Liberación ordenada de reproductores y scopes
   │       │       ├── DualDeckTransitionManager.kt  # Handoff al Deck B y entrada con crossfade/fade-in
   │       │       ├── TrackMediaFactory.kt          # URIs, metadatos con carátula y recortes de silencio
   │       │       ├── PlayerStateListener.kt        # Listener de estado ExoPlayer hacia los flujos
   │       │       ├── PlaybackProgressTracker.kt    # Sondeo de progreso, bucle A-B, prefetch y disparo de crossfade
   │       │       ├── StreamingErrorRecoveryHandler.kt # Reintento ante HTTP 403/416 y cortes de red
   │       │       ├── PlaybackParametersManager.kt  # Velocidad/tono con throttling de 40ms en Main
   │       │       ├── StreamingUrlPolicy.kt         # Detección de URLs de streaming sin resolver
    │       │       ├── AuraMediaPlaybackService.kt # MediaSessionService para System Media Controls y Android Auto
    │       │       ├── AuraDownloadService.kt      # Servicio en primer plano para descargas web y lotes de Playlists
    │       │       ├── AudioEffectManager.kt       # Gestión de ecualizador y efectos nativos C++20
    │       │       ├── HeadphoneController.kt      # BroadcastReceiver para audífonos, Becoming Noisy y botones físicos
    │       │       ├── NativeAudioEngine.kt        # Puente JNI hacia el motor DSP en ISO C++20 y AAudio
    │       │       ├── NativeAudioProcessor.kt     # AudioProcessor de ExoPlayer hacia el motor C++20 con Buffer Flushing
    │       │       └── controllers/
    │       │           ├── PlayerQueueController.kt # Gestión de cola contextual y modos de repetición/aleatorio
    │       │           ├── AudioFadeController.kt   # Crossfade Dual-Deck (Equal-Power sin/cos), Smart AutoMix, Fade-In/Out y Sleep Timer
    │       │           ├── ABLoopController.kt      # Bucle de repetición de segmento A-B
    │       │           └── MediaSessionBridge.kt    # Puente de notificación nativa System Media Controls
    │       │
    │       ├── feature/                        # Pantallas Modulares por Funcionalidad (Feature Layer)
    │       │   ├── home/
    │       │   │   └── HomeScreen.kt           # Pantalla de Inicio con acceso rápido, populares y recientes
    │       │   ├── streaming/                    # Pestaña Explorar / Streaming Tri-Plataforma
    │       │   │   ├── StreamingExploreScreen.kt     # Fachada (169 líneas, API intacta)
    │       │   │   ├── StreamingExploreHeader.kt     # Título y estado de caché
    │       │   │   ├── StreamingSearchBar.kt         # Entrada de consultas
    │       │   │   ├── StreamingFilterRow.kt         # Chips de origen
    │       │   │   ├── StreamingRadioSection.kt      # Guard + banner de similar
    │       │   │   ├── SimilarTrackRadioBanner.kt    # Tarjeta de recomendación
    │       │   │   ├── StreamingResultsList.kt       # Lista con estados por tarjeta
    │       │   │   ├── StreamingResultCard.kt        # Fila de catálogo con acciones
    │       │   │   └── StreamingPlatformBadge.kt     # Insignia YT Music / YouTube / SoundCloud
    │       │   ├── library/
    │       │   │   ├── LibraryScreen.kt        # Pantalla de Biblioteca (Canciones, Favoritos, Playlists, Artistas y Álbumes)
    │       │   │   └── components/
    │       │   │       ├── AlbumAndArtistCards.kt        # Tarjetas de Álbumes y Artistas con apertura de detalle de canciones
    │       │   │       ├── FavoritesPlaylistBannerCard.kt # Banner destacado de la lista sincronizada "Tus Me Gusta"
    │       │   │       ├── LibraryEmptyViews.kt          # Vistas de estado vacío para pestañas de la Biblioteca
    │       │   │       ├── PlaylistDialogs.kt            # Diálogos de creación, edición y adición de pistas a Playlists
    │       │   │       └── PlaylistRowItem.kt            # Fila interactiva de Playlist con collage dinámico 1-4 fotos o WebP
    │       │   ├── nowplaying/
    │       │   │   ├── NowPlayingScreen.kt     # Fachada delgada (estado visual, atajos, diálogos y ramas)
    │       │   │   ├── NowPlayingVisualState.kt  # Colores dinámicos cada 1s y banderas de video
    │       │   │   ├── NowPlayingShortcutRow.kt  # Atajos EQ FX, Letras/Carátula y Cola
    │       │   │   └── NowPlayingDialogsHost.kt  # Los 8 diálogos y hojas modales
    │       │   │   └── components/
    │       │   │       ├── FullScreenLyricsScreen.kt       # Modo Karaoke Inmersivo a Pantalla Completa estilo Apple Music Sing
    │       │   │       ├── NowPlayingCinematicLayout.kt    # Fachada que delega en secciones Top/Bottom
    │       │   │       ├── CinematicTopSection.kt          # Barra superior y fila de pista
    │       │   │       ├── CinematicBottomSection.kt       # Controles, utilidades y vista previa karaoke
    │       │   │       ├── NowPlayingClassicLayout.kt      # Rama clásica completa
    │       │   │       ├── NowPlayingFullscreenBackground.kt # Video fullscreen con velo
    │       │   │       └── NowPlayingCinematicHost.kt      # Envoltorio de la rama cinemática
    │       │   │       ├── NowPlayingDesignSelectorDialog.kt # Selector modal de modo de diseño del reproductor
    │       │   │       ├── NowPlayingArtworkCard.kt        # Tarjeta de carátula 1:1 y lienzo de video despejado
    │       │   │       ├── NowPlayingLyricsCard.kt         # Tarjeta sincronizada de letras Karaoke 1:1 con auto-scroll
    │       │   │       ├── NowPlayingPlaybackControls.kt   # Controles de transporte y barra continua con selector circular ("bolita")
    │       │   │       ├── NowPlayingQueueSheet.kt         # Hoja modal de cola de reproducción con miniaturas HD
    │       │   │       ├── NowPlayingTopBar.kt             # Barra superior con accesos a especificaciones, Karaoke, diseño y efectos
    │       │   │       ├── NowPlayingBalanceBar.kt         # Barra interactiva de Balance Estéreo Fino L/R
    │       │   │       ├── AudioSpecsDialog.kt             # Ficha técnica de pista, telemetría AAudio y estado Bit-Perfect
    │       │   │       └── VideoDisplayModeDialog.kt       # Selector modal de encuadre de Video Canvas (4 modos)
    │       │   ├── playlist/
    │       │   │   └── PlaylistDetailScreen.kt # Vista detallada de canciones para Playlists, Artistas y Álbumes
    │       │   ├── importmusic/                   # Centro de importación (fachada + 6 piezas)
    │       │   │   ├── ImportMusicScreen.kt        # Fachada: orquesta lanzadores SAF y diálogos
    │       │   │   ├── ImportStatusCards.kt        # Tarjetas de aviso, carga y privacidad
    │       │   │   ├── ImportWebOptions.kt         # Descargas TikTok, YouTube y playlists externas
    │       │   │   ├── ImportLocalOptions.kt       # Video a música, archivos, carpeta y demos
    │       │   │   ├── ImportLibrarySummaryCard.kt # Resumen de colección con reinicio
    │       │   │   ├── ImportPendingDialogs.kt     # Overlays de archivos, video y descarga web
    │       │   │   └── ImportClearConfirmDialog.kt # Confirmación destructiva de limpieza
    │       │   ├── onboarding/
    │       │   │   ├── OnboardingScreen.kt         # Flujo de bienvenida e inducción en 3 etapas
    │       │   │   └── OnboardingStepComponents.kt # Tarjetas modulares de propuesta de valor y disclaimer de almacenamiento
    │       │   └── settings/
    │       │       ├── SettingsScreen.kt              # Fachada: firma, enum y navegación (386 líneas)
    │       │       ├── SettingsMainMenuScreen.kt        # Menú principal con cabecera
    │       │       ├── SettingsPlayerScreens.kt         # Diseño del reproductor y paletas
    │       │       ├── SettingsVisualScreens.kt         # Menú visual + 4 sub-pantallas y galería
    │       │       ├── SettingsWidgetScreen.kt          # Pantalla Widget
    │       │       ├── SettingsHeadphoneScreens.kt      # Menú + DSP, seguridad, gestos y diálogo
    │       │       └── SettingsSystemScreens.kt         # Medios, motores, descargas y proyecto
    │       │       └── components/
    │       │           ├── SettingDetailRow.kt            # Fila reutilizable de información clave-valor
    │       │           ├── SettingsSubScreenComponents.kt # Tarjetas navegables y contenedor SettingsSubMenuScreen
    │       │           ├── widgets/                       # Personalización y Vista Previa de los 2 Widgets
    │       │           │   ├── WidgetSettingsContent.kt       # Fachada de personalización (87 líneas)
    │       │           │   ├── WidgetLivePreviewSection.kt    # Vista previa en vivo con colores dinámicos
    │       │           │   ├── WidgetGridModeSection.kt       # Selector Top 4 Canciones/Playlists
    │       │           │   ├── WidgetDynamicColorSection.kt  # Color de imagen e intensidad
    │       │           │   └── WidgetProgressSection.kt       # Barra de progreso y sincronización
    │       │           ├── about/                         # Detrás del Proyecto (Creador & Contribuidores)
    │       │           │   ├── BehindTheProjectSection.kt      # Fachada del apartado (111 líneas)
    │       │           │   ├── ProjectMemberProfiles.kt         # Datos de creador y contribuidores
    │       │           │   ├── GithubAvatarStore.kt             # Caché permanente de avatares en WebP
    │       │           │   ├── ProjectHeaderCards.kt            # Cabeceras e identidad del proyecto
    │       │           │   ├── DeveloperProfileCard.kt          # Tarjeta de perfil con avatar y acciones
    │       │           │   └── ProjectLicenseCard.kt            # Licencia y acciones externas
    │       │           ├── appearance/                    # Ajustes de Apariencia, Streaming, Modo Juego, Fondo y Motores
    │       │           │   ├── AppearanceMainMenu.kt              # Menú principal de categorías de Ajustes
    │       │           │   ├── AppearanceBehaviorSection.kt       # Comportamiento y descubrimiento de ajustes
    │       │           │   ├── AppearanceAuraMiniPlayerSection.kt # Aura Dinámica (1s) y Mini Reproductor
    │       │           │   ├── AppearanceGameModeSection.kt       # Modo Juego / Ahorro en 2do plano
    │       │           │   ├── AppearancePaletteSection.kt        # Paleta dinámica y colores del tema
    │       │           │   ├── AppWallpaperSettingsSection.kt       # Controles de fondo de pantalla de galería (WebP/Video, Opacidad y Blur)
    │       │           │   ├── StreamingCacheAndVideoSettingsCard.kt # Ajustes de caché inteligente (50-500 MB), política de red y Modo Juego
    │       │           │   ├── EnginesAndDiagnosticsSection.kt      # Estado de FFmpeg, QuickJS C99 puro, yt-dlp OTA y Aura Monitor
    │       │           │   └── PlayerDesignSettingsContent.kt       # Pantalla dedicada de selección de Diseño del Reproductor
    │       │           ├── headphones/                    # Ajustes de Auriculares, Acústica DSP, Bit-Perfect y Gestos
    │       │           │   ├── BitPerfectSettingsSection.kt   # Configuración Bit-Perfect 1:1 y Google AAudio Hi-Res
    │       │           │   ├── HeadphoneStatusCard.kt         # Tarjeta de estado de conexión de audífonos en vivo
    │       │           │   ├── HeadphonesAcousticsSection.kt  # Crossfeed Chu Moy y Balance Estéreo Fino L/R
    │       │           │   ├── HeadphonesSecuritySection.kt   # Becoming Noisy Guard, Fade-In y Memoria de Volumen
    │       │           │   ├── HeadphonesGesturesSection.kt   # Mapeo de 1, 2, 3 pulsaciones y mantener presionado
    │       │           │   └── HeadsetButtonActionDialog.kt   # Selector modal de acción para botones de auriculares
    │       │           ├── updater/                       # Actualizador Automático de Versiones APK
    │       │           │   └── AppReleaseUpdateSettingsCard.kt # Tarjeta de consulta y descarga de nuevas versiones APK
    │       │           └── media/                         # Gestión de Medios Almacenados y Carpeta Pública del Usuario
    │       │               ├── StoredMediaSettingsTab.kt   # Pantalla de transparencia de medios guardados
    │       │               ├── UserPublicFolderCard.kt     # Selector de carpeta pública del usuario y exportación .mp4 con audio
    │       │               ├── SavedMediaStorageSection.kt # Resumen de rutas estructuradas y espacio físico
    │       │               ├── StoredMediaFormatUtils.kt   # Utilidades de formato de pesos KB/MB y relaciones de aspecto
    │       │               ├── StoredMediaSummaryHeader.kt # Cabecera estadística de portadas WebP y videos MP4
    │       │               ├── StoredMediaTrackCard.kt     # Tarjeta individual de pista con inspección y borrado selectivo
    │       │               └── StoredMediaDeleteDialogs.kt # Diálogos de confirmación para liberar almacenamiento
    │       │
    │       ├── ui/                             # Orquestador UI Global, Componentes Compartidos, Navegación y Tema M3
    │       │   ├── AuraMusicAppContent.kt      # Fachada que delega en 4 hosts
    │       │   ├── MiniPlayerColorsState.kt    # Colores dinámicos del MiniPlayer cada 1s
    │       │   ├── AppBottomBarHost.kt         # MiniPlayer flotante y navegación inferior
    │       │   ├── AppScreensHost.kt           # Las 7 pantallas con transiciones
    │       │   └── AppNowPlayingHost.kt        # Overlay de Now Playing cableado
    │       │   ├── GlobalDialogsHost.kt        # Contenedor centralizado de diálogos y hojas modales globales
    │       │   ├── navigation/
    │       │   │   └── NavScreen.kt            # Definición de rutas principales y pestañas de biblioteca
    │       │   ├── theme/                      # Sistema de Diseño Dark Luxury Neo-Glass OLED y Extracción Cromática
    │       │   │   ├── ArtworkColorExtractor.kt # Fachada (135 líneas, API intacta)
    │       │   │   ├── ArtworkSamplingState.kt   # Intervalo por batería y color en vivo
    │       │   │   ├── VideoFrameColorExtractor.kt # Fotogramas, retriever y arte remoto
    │       │   │   └── ArtworkPaletteExtractor.kt  # Carátula/video, fallback y paleta armónica
    │       │   │   ├── Color.kt                 # Paletas de color neón y superficies OLED 100% opacas
    │       │   │   ├── Theme.kt                 # Configuración MaterialTheme, Material You y blindaje fontScale = 1.0f
    │       │   │   └── Type.kt                  #Jerarquía tipográfica de Aura Music
    │       │   └── components/                 # Componentes Modulares Reutilizables
    │       │       ├── MiniPlayer.kt           # Mini reproductor flotante tintado con Video Canvas e indicador de red
    │       │       ├── BottomNavBar.kt         # Barra de navegación inferior con pestaña Explorar e insignia de Ajustes
    │       │       ├── TrackListItem.kt        # Elemento modular de canción con marquesina y menú contextual
    │       │       ├── AnimatedDownloadingArrowIcon.kt # Icono animado de descarga activa en barra superior
    │       │       ├── AudioEffectsBottomSheet.kt      # Hoja modal unificada del estudio acústico C++20
    │       │       ├── artwork/                # Renderizadores de Portadas, Collages, Fondo, Arte Procedural y Video
    │       │       │   ├── ArtworkImage.kt           # Cargador Coil WebP con recorte de franjas negras 4:3 (Deletterbox)
    │       │       │   ├── AppWallpaperBackground.kt # Renderizador global de fondo personalizado (Imagen WebP o Video corto)
    │       │       │   ├── PlaylistCoverCollage.kt   # Collage adaptativo automático de 1, 2, 3 o 4 portadas
    │       │       │   ├── ProceduralArtwork.kt      # Generador Canvas vectorial para canciones sin carátula
    │       │       │   ├── BackgroundVideoPlayer.kt  # Reproductor Video Canvas sin pantallas negras y muestreo GPU a 1s
    │       │       │   └── AudioVisualizer.kt        # Visualizador espectral de 28 bandas impulsado por C++20
    │       │       ├── audioeffects/           # Pestañas Modulares de la Hoja de Efectos de Audio
    │       │       │   ├── EqualizerTabContent.kt          # Pestaña del Ecualizador ISO de 10 bandas, Bass Boost y Voces HD
    │       │       │   ├── Spatial8DTabContent.kt          # Pestaña de Audio Espacial 8D Clásico y 16D Multi-Órbita
    │       │       │   ├── ReverbTabContent.kt             # Pestaña de Reverb C++20 y espacios acústicos
    │       │       │   ├── PlaybackParametersTabContent.kt # Pestaña de Velocidad/Tono (0.5x-2.0x), Voces HD y Normalizador LUFS
    │       │       │   ├── BalanceAndHeadphonesTabContent.kt # Pestaña de Balance Estéreo L/R y Crossfeed Chu Moy
    │       │       │   ├── TransitionsTabContent.kt        # Fachada de transiciones (94 líneas)
    │       │       │   ├── TransitionABLoopCard.kt         # Repetidor de segmento A-B
    │       │       │   ├── TransitionAutomixCard.kt        # AutoMix DJ, crossfade y gapless
    │       │       │   └── TransitionNormalizationCard.kt  # Normalización EBU R128
    │       │       │   └── SleepTimerTabContent.kt         # Pestaña del Temporizador de Apagado con Fade-Out de 10s
    │       │       ├── banners/                # Banners Superiores No Intrusivos
    │       │       │   ├── PackageUpdateBanner.kt     # Banner de verificación y actualización OTA de yt-dlp
    │       │       │   └── SettingsDiscoveryBanner.kt # Aviso interactivo para descubrir Ajustes en el primer uso
    │       │       ├── dialogs/                # Diálogos Modales Globales
    │       │       │   ├── AppUpdateDialog.kt              # Modal de descarga e instalación de actualización APK
    │       │       │   ├── CreateMixtapeDialog.kt          # Fachada del modal (241 líneas, estado + llamadas)
    │       │       │   ├── MixtapeDialogHeader.kt          # Encabezado con icono según modo
    │       │       │   ├── MixtapeModeSelector.kt          # Pestañas Mixtape Lista vs Auto-Remix
    │       │       │   ├── MixtapeStatusViews.kt           # Alerta, completado y progreso en vivo
    │       │       │   ├── MixtapeListConfig.kt            # Formulario de mezcla de lista completa
    │       │       │   ├── MixtapeTrackPickerDialog.kt     # Elector A/B con búsqueda local
    │       │       │   ├── AutoRemixConfigSection.kt       # Configurador de Mashup (Voz A + Base B), Remix DJ y Auto-Match
    │       │       │   ├── DownloadFromLinkDialog.kt       # Modal deslizable de descarga desde YouTube, TikTok y web
    │       │       │   ├── ImportExternalPlaylistDialog.kt # Fachada del modal (195 líneas)
    │       │       │   ├── PlaylistImportHeader.kt         # Encabezado con acento por plataforma
    │       │       │   ├── PlaylistImportUrlField.kt       # Campo de enlace
    │       │       │   ├── PlaylistImportAnalyzeButton.kt  # Botón de análisis
    │       │       │   ├── PlaylistImportError.kt          # Alerta de error
    │       │       │   ├── PlaylistImportPreviewCard.kt    # Previsualización con nombre y pistas
    │       │       │   ├── PlaylistImportSaveModeSelector.kt # Modo de guardado y Video Canvas
    │       │       │   ├── PlaylistImportProgress.kt       # Barra de progreso
    │       │       │   └── PlaylistImportActions.kt        # Botones finales
    │       │       │   ├── EditTrackDialog.kt              # Editor de metadatos, portada WebP y Video Canvas
    │       │       │   ├── SearchLyricsDialog.kt           # Buscador interactivo de letras LRCLIB con recomendación #1
    │       │       │   └── VideoToMusicDialog.kt           # Conversor 3 en 1 de Video a Música (.m4a + WebP + Canvas)
    │       │       └── download/               # Subcomponentes del Modal de Descargas Web
    │       │           ├── DownloadProgressStatusCard.kt      # Tarjeta de progreso en vivo (MB/s, porcentaje y 2do plano)
    │       │           ├── DownloadSourceAndEngineSelector.kt # Selector interactivo de motor (yt-dlp, InnerTube, WebView)
    │       │           ├── DownloadResolvedMediaCard.kt       # Previsualización de portada, título y edición rápida
    │       │           └── DownloadCanvasOptionsSection.kt    # Opciones de Video Canvas 480p, Boomerang/Crossfade y silencios
    │       │
    │       ├── viewmodel/                      # Capa ViewModel y Coordinadores MVVM
    │       │   ├── MusicViewModel.kt           # ViewModel principal desacoplado
    │       │   └── delegates/
    │       │       ├── AudioEffectsCoordinator.kt      # Coordinador de efectos C++20, Normalizador, AutoMix y Sleep Timer
    │       │       ├── NavigationCoordinator.kt        # Coordinador de navegación, temas, fondo de galería y modos visuales
    │       │       ├── TrackLibraryCoordinator.kt      # Operaciones de biblioteca, playlists, SAF y descargas
    │       │       ├── LyricsCoordinator.kt            # Coordinación de letras sincronizadas LRCLIB y archivos .lrc/.txt
    │       │       ├── IncomingMediaCoordinator.kt     # Recepción de Intents externos (audios, videos, URLs y Playlists)
    │       │       ├── HeadphoneSettingsCoordinator.kt # Ajustes de auriculares, Crossfeed, Bit-Perfect y AAudio
    │       │       ├── MixtapeCoordinator.kt           # Coordinador de Mixtapes, Auto-Remix de 2 pistas y capítulos en vivo
    │       │       ├── StreamingModeCoordinator.kt     # Fachada del Modo Streaming (156 líneas, API intacta)
    │       │       ├── StreamingSearchOps.kt           # Búsqueda y tendencias con cancelación de carrera
    │       │       ├── StreamingPlaybackResolver.kt    # Resolución, transición, recuperación 403 y radio similar
    │       │       ├── StreamingDownloadOps.kt         # Descarga permanente y ajustes de caché
    │       │       ├── StreamingTrackBuilders.kt       # Tracks sintéticos y colas desde resultados
    │       │       └── PlaylistImportCoordinator.kt    # Orquestador del Importador de Playlists de Spotify y YouTube Music
    │       │
    │       ├── widget/                         # Sistema de 2 Widgets Interactivos para Pantalla de Inicio
    │       │   ├── AuraMusicWidgetProvider.kt        # Widget 1: Reproductor adaptativo (4x1, 4x2, 4x3) con color de carátula
    │       │   ├── AuraLibraryWidgetProvider.kt      # Widget 2: Cuadrícula 2x2 de Top 4 Canciones o 4 Playlists principales
    │       │   ├── WidgetStateStore.kt               # Persistencia de estado, extracción Palette y caché JSON de Widgets
    │       │   ├── WidgetArtworkRenderer.kt          # Renderizador Canvas de fondos Neo-Glass, barras y collages 2x2
    │       │   └── WidgetPlaybackHeadlessController.kt # Controlador para reanudar y cambiar pistas en 2do plano con app cerrada
    │       │
    │       └── debug/                          # Suite Autónoma de Diagnóstico y Telemetría "Aura Monitor" (Solo Debug)
    │           ├── AuraDebugManager.kt               # Manejador global de excepciones (UncaughtExceptionHandler) y logs
    │           ├── DebugMonitorActivity.kt           # Actividad independiente en el launcher con pestañas de Logs y Rendimiento
    │           ├── DebugLogEntry.kt                  # Modelo de entrada de incidente o evento técnico
    │           ├── DebugSeverity.kt                  # Niveles de severidad (CRASH, CRITICAL, ERROR, WARNING, INFO)
    │           ├── DeviceDiagnosticInfo.kt           # Recolector de hardware, ABI, Android SDK, RAM y almacenamiento
    │           ├── PerformanceDiagnosticModels.kt    # Modelos de memoria segmentada (Java, Native C++, Gráficos, PSS) e hilos
    │           ├── PerformanceTelemetryManager.kt    # Monitor en vivo de RAM, CPU (%), FPS e inspector de hilos con stacktraces
    │           └── ui/
    │               ├── DebugFilterChips.kt               # Chips de filtrado por severidad en Aura Monitor
    │               ├── DebugLogEntryCard.kt              # Tarjeta resumida de incidente registrado
    │               ├── DebugLogDetailDialog.kt           # Visor detallado de stacktrace y copia al portapapeles
    │               ├── DebugSyntheticTestDialog.kt       # Generador de pruebas sintéticas de diagnóstico
    │               ├── HardwareTelemetryCard.kt          # Tarjeta de especificaciones técnicas del dispositivo
    │               └── PerformanceTelemetryTabContent.kt # Pestaña interactiva de RAM segmentada, CPU e Inspector de Hilos
    │
    ├── test/                                   # Pruebas Unitarias Locales y Robolectric (JVM)
    │   └── java/com/example/
    │       ├── ExampleUnitTest.kt              # Pruebas unitarias base
    │       └── ExampleRobolectricTest.kt       # Pruebas de integración local con Robolectric
    │
    └── androidTest/                            # Pruebas Instrumentadas Base de Plantilla Android
        └── java/com/example/
            └── ExampleInstrumentedTest.kt
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

---

## ⚡ Arquitectura Modular del Motor yt-dlp y Descargas Directas

Para garantizar estabilidad, mantenibilidad y cumplir con el estándar de desarrollo modular (< 500 líneas por archivo), el motor nativo de `yt-dlp` en `app/src/main/java/com/example/core/data/importer/engines/` se estructura en 3 módulos especializados:

### 1. `YtDlpEnvironmentSetup.kt` (Entorno y Runtime Nativo)
- **Rol**: Configuración y aprovisionamiento del entorno de ejecución de Python 3.11 y QuickJS nativo.
- **Responsabilidades clave**:
  - Detección de ejecutables en `nativeLibraryDir` (`libpythonlauncher.so`, `libnativeffmpeg.so`) y fallbacks en `files/bin/` o `files/env/bin/`.
  - Creación de enlaces simbólicos (symlinks) hacia `.so` de tamaño 0 bytes o requeridas por CPython (`libpython3.11.so`, etc.).
  - Consolidación del almacén de certificados CA (`cacerts`) para Android 14+ (`/apex/com.android.conscrypt/cacerts`, `/system/etc/security/cacerts` y `cert.pem` embebido).
  - Configuración del mapa de variables de entorno (`PYTHONHOME`, `PYTHONPATH`, `LD_LIBRARY_PATH`, `SSL_CERT_FILE`, `TMPDIR`, `HOME`).
  - Limpieza y sanitización de URLs de entrada (eliminación de tracking `si=`, listas de reproducción no deseadas, normalización `youtu.be` / `youtube.com`).

### 2. `YtDlpStreamDownloader.kt` (Descarga Directa y Telemetría en Vivo)
- **Rol**: Ejecución del proceso CLI de `yt-dlp` para descargas directas sin intermediación de clientes HTTP externos.
- **Responsabilidades clave**:
  - Descarga nativa de pistas de audio en formato de alta fidelidad (`.m4a` / AAC / Opus) y de Video Canvas sincronizado en resolución 480p (`[height<=480]`).
  - Prevención de bloqueos `HTTP 403 Forbidden` de Google Video al reutilizar la sesión nativa y tokens criptográficos (PO Token y VisitorData) directamente en el proceso CLI de `yt-dlp`.
  - Captura y análisis sintáctico (*parsing*) en tiempo real de la salida estándar (`stdout`) formateada con `--newline`:
    - Porcentaje exacto completado (`45.2%`).
    - Tamaño descargado y total estimado en bytes (`10.5MiB`).
    - Velocidad de transferencia instantánea (`2.4MiB/s`).
    - Tiempo estimado de finalización (`ETA 00:03`).
  - Emisión continua de métricas hacia el callback `(DownloadProgress) -> Unit` para refrescar la notificación en la barra de estado y la tarjeta de progreso en pantalla.
  - Verificación estricta de archivo resultante (existencia en disco y tamaño > 0 bytes).

### 3. `YtDlpNativeEngine.kt` (Fachada Principal y Orquestador)
- **Rol**: Fachada pública unificada de alto nivel (< 450 líneas) que preserva el 100% de la API pública para el resto del proyecto.
- **Responsabilidades clave**:
  - Inspección ligera y rápida de streams (`--dump-single-json`) para extraer títulos, artistas, duraciones, miniaturas y metadatos sin descargar el video completo.
  - Resolución de listas de reproducción públicas mediante modo plano (`--flat-playlist`).
  - Delegación transparente de la inicialización hacia `YtDlpEnvironmentSetup` y de la descarga directa hacia `YtDlpStreamDownloader`.
  - Compatibilidad total con `OnlineVideoAudioImporter`, `YtDlpAutoUpdater` y `StreamingApiService`.

### 4. Orquestación Híbrida en `OnlineVideoAudioImporter.kt`
- **Flujo Resiliente**:
  1. Si el motor activo es `yt-dlp`, se invoca prioritariamente la descarga directa vía `YtDlpStreamDownloader` para audio y Video Canvas (480p).
  2. Si `yt-dlp` no está disponible o falla por alguna razón de red o de proceso, el sistema desciende fluidamente al descargador fragmentado por bloques HTTP Range (`ChunkedStreamDownloader`) con reintento lineal en OkHttp.
  3. Si la URL directa arroja `HTTP 403`, se activan los niveles de respaldo alternativos (InnerTube multi-cliente, Invidious Bypass y Headless WebView en sandbox).

