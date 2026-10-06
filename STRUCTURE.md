# Estructura del Proyecto (Aura Music) 🏗️

Este documento describe la arquitectura modular, la jerarquía de directorios y las responsabilidades de cada componente del código fuente.

---

## 📂 Árbol de Directorios

```
AuraMusic/
├── .github/
│   └── workflows/
│       ├── build-debug-apk.yml                 # CI/CD: Workflow manual de compilación APK Debug con caché y NDK
│       └── purge-native-binaries-history.yml   # Utilidad: Workflow manual para purgar binarios .so del historial Git
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── cpp/                            # Código Nativo C++20 / C17 (DSP y Lanzadores Puros Multi-ABI)
│   │   │   │   ├── CMakeLists.txt              # Configuración CMake integrada en Gradle (C17 / C++20)
│   │   │   │   ├── dsp_filters.h               # Módulo C++20: Filtros Biquad IIR, EQ 10 bandas y limitador con reset()
│   │   │   │   ├── dsp_spatial.h               # Módulo C++20: Motor Audio Espacial 8D Binaural orbital con reset()
│   │   │   │   ├── dsp_crossfeed.h             # Módulo C++20: Filtro acústico Crossfeed Bauer / Chu Moy con reset()
│   │   │   │   ├── dsp_reverb.h                # Módulo C++20: Suite Reverb acústica híbrida con resetBuffers()
│   │   │   │   ├── auramusic_dsp.h             # Orquestador nativo C++20 con limpieza atómica de buffers (flushDspBuffers)
│   │   │   │   ├── auramusic_dsp.cpp           # Implementación JNI del motor nativo con nativeFlushDspBuffers
│   │   │   │   ├── native_python_launcher.c    # Lanzador PIE puro C17 para CPython 3.11 (Py_BytesMain sin sh)
│   │   │   │   ├── native_ffmpeg_launcher.c    # Lanzador PIE puro C17 para FFmpeg CLI nativo (sin sh)
│   │   │   │   ├── native_quickjs_cli.c        # Evaluador CLI nativo C99 de respaldo para QuickJS (libqjs.so)
│   │   │   │   └── aura_ytdlp_fallback.py      # Extractor Python nativo de respaldo offline para yt-dlp
│   │   │   ├── jniLibs/                        # Estructura ABI protegida por .gitkeep e ignorada en Git (*.so en CI/CD)
│   │   │   │   ├── arm64-v8a/.gitkeep          # 64-bit ARM
│   │   │   │   ├── armeabi-v7a/.gitkeep        # 32-bit ARM
│   │   │   │   ├── x86_64/.gitkeep             # 64-bit Intel/AMD
│   │   │   │   └── x86/.gitkeep                # 32-bit Intel
│   │   │   ├── assets/                         # Recursos empaquetados en APK
│   │   │   │   └── bin/.gitkeep                # Directorio para ejecutable yt-dlp aprovisionado en CI/CD o OTA
│   │   │   ├── java/com/example/
│   │   │   │   ├── AuraApplication.kt         # Clase Application con inicio de AuraDebugManager
│   │   │   │   ├── MainActivity.kt             # Actividad principal, insets, animaciones y navegación
│   │   │   │   ├── debug/                      # Suite Autónoma de Diagnóstico y Telemetría
│   │   │   │   │   ├── DebugSeverity.kt        # Enum de severidades (CRASH, CRÍTICO, ERROR, WARNING, INFO)
│   │   │   │   │   ├── DeviceDiagnosticInfo.kt # Ficha técnica de hardware del teléfono
│   │   │   │   │   ├── DebugLogEntry.kt        # Modelo de eventos con stacktrace en crudo y hora
│   │   │   │   │   ├── PerformanceDiagnosticModels.kt # Modelos para RAM segmentada (Java/Native/Gfx), CPU e Hilos
│   │   │   │   │   ├── PerformanceTelemetryManager.kt # Gestor de telemetría de memoria, procesador e inspector de hilos
│   │   │   │   │   ├── AuraDebugManager.kt     # Gestor central de logs, persistencia JSON y UncaughtHandler
│   │   │   │   │   ├── DebugMonitorActivity.kt # Actividad con pestañas (Incidentes & Logs vs Rendimiento & Hilos)
│   │   │   │   │   └── ui/                     # Componentes modulares de Aura Monitor
│   │   │   │   │       ├── HardwareTelemetryCard.kt   # Ficha técnica de hardware, RAM y CPU ABI
│   │   │   │   │       ├── DebugLogEntryCard.kt       # Tarjeta individual para logs con severidad
│   │   │   │   │       ├── DebugFilterChips.kt        # Filtros por severidad con contadores en vivo
│   │   │   │   │       ├── DebugLogDetailDialog.kt    # Modal de visualización de Stack Trace en crudo
│   │   │   │   │       ├── DebugSyntheticTestDialog.kt# Menú para simulación sintética de incidencias
│   │   │   │   │       └── PerformanceTelemetryTabContent.kt # Panel en vivo de RAM segmentada, CPU e Inspector de Hilos
│   │   │   │   ├── data/
│   │   │   │   │   ├── importer/               # Módulos de importación y análisis modularizados
│   │   │   │   │   │   ├── AudioMetadataParser.kt # Extractor ID3 y conversor a WebP
│   │   │   │   │   │   ├── AudioSilenceTrimmer.kt # Detector y recortador inteligente de silencios (-42 dB RMS)
│   │   │   │   │   │   ├── IncomingMediaHandler.kt # Detector y clasificador de Intents externos (Audio, Video, Link)
│   │   │   │   │   │   ├── LyricsManager.kt        # Analizador de .LRC, descarga de LRCLIB y persistencia local
│   │   │   │   │   │   ├── MediaAssetProcessor.kt  # Procesador modular de carátulas WebP y Video Canvas (480p, loops)
│   │   │   │   │   │   ├── OnlineVideoAudioImporter.kt # Orquestador modular de descargas y extracción multimedia
│   │   │   │   │   │   ├── ProceduralArtworkGenerator.kt # Generador procedural de carátulas
│   │   │   │   │   │   ├── SampleMusicGenerator.kt # Sintetizador de audio WAV para demos
│   │   │   │   │   │   ├── VideoAudioExtractor.kt  # Extractor de audio nativo y generador 3 en 1 de Video a Música
│   │   │   │   │   │   ├── FFmpegNativeEngine.kt   # Motor nativo FFmpeg puro (CLI/JNI): Canvas 9:16, xfade, boomerang
│   │   │   │   │   │   ├── YtDlpAutoUpdater.kt     # Gestor de actualización en caliente OTA para yt-dlp
│   │   │   │   │   │   ├── YtDlpNativeEngine.kt    # Extractor nativo local basado en yt-dlp y FFmpeg
│   │   │   │   │   │   ├── InnerTubeClient.kt      # Cliente InnerTube multi-cliente sin fricción
│   │   │   │   │   │   ├── InvidiousStreamResolver.kt # Resolvedor de respaldo para restricciones de derechos
│   │   │   │   │   │   ├── HeadlessWebViewExtractor.kt # Extractor móvil en segundo plano sobre m.youtube.com
│   │   │   │   │   │   ├── WebStreamExtractor.kt   # Orquestador híbrido de 3 niveles con fallback automático
│   │   │   │   │   │   ├── download/
│   │   │   │   │   │   │   └── ChunkedStreamDownloader.kt # Acelerador HTTP Range Chunked (sin límite de 63 KB/s)
│   │   │   │   │   │   └── tiktok/
│   │   │   │   │   │       └── TikTokMediaResolver.kt # Resolvedor especializado de videos sin marca de agua
│   │   │   │   │   ├── local/                  # Capa de persistencia local Room SQLite
│   │   │   │   │   │   ├── AppDatabase.kt      # Base de datos Room
│   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   │   ├── TrackDao.kt     # DAO para pistas de música, carátulas y edición
│   │   │   │   │   │   │   └── PlaylistDao.kt  # DAO para listas de reproducción y relaciones
│   │   │   │   │   │   └── entity/
│   │   │   │   │   │       ├── TrackEntity.kt  # Entidad de pista en Room
│   │   │   │   │   │       ├── PlaylistEntity.kt # Entidad de lista en Room
│   │   │   │   │   │       └── PlaylistTrackCrossRef.kt # Relación muchos a muchos
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── MusicRepository.kt  # Repositorio fachada central modularizado
│   │   │   │   │   │   ├── PlaylistRepository.kt # Repositorio modular de listas de reproducción y relaciones
│   │   │   │   │   │   └── SafTrackImporter.kt # Importador modular SAF (archivos, árbol, videos e intents)
│   │   │   │   │   └── storage/
│   │   │   │   │       └── AppStorageManager.kt # Almacenamiento estructurado y carátulas WebP
│   │   │   │   ├── model/                      # Modelos de dominio
│   │   │   │   │   ├── Track.kt                # Modelo de datos de canción
│   │   │   │   │   ├── LyricsState.kt          # Modelo de letras sincronizadas (.LRC) y líneas temporizadas
│   │   │   │   │   ├── LyricSearchResult.kt    # Modelo para resultados de búsqueda de letras y versión oficial
│   │   │   │   │   ├── Playlist.kt             # Modelo de datos de lista
│   │   │   │   │   ├── RepeatMode.kt           # Enum de modos de repetición
│   │   │   │   │   ├── EqualizerConfig.kt      # Modelo de 10 bandas y presets de EQ
│   │   │   │   │   ├── AudioEffectsConfig.kt   # Modelos para Audio 8D, Temporizador, Bucle A-B y Gapless/Crossfade
│   │   │   │   │   ├── HeadphoneConfig.kt      # Configuración de Crossfeed C++20, Balance L/R y gestos
│   │   │   │   │   ├── VideoDisplayMode.kt     # Enum de modos de visualización de video (Fondo Completo, Carátula, Off)
│   │   │   │   │   └── ThemePalette.kt         # Enum de temas de color vibrantes
│   │   │   │   ├── playback/                   # Capa de reproducción de audio modularizada
│   │   │   │   │   ├── AuraAudioPlayer.kt      # Orquestador Media3 ExoPlayer y Buffer Flushing
│   │   │   │   │   ├── AuraMediaPlaybackService.kt # Servicio MediaSessionService y notificación nativa
│   │   │   │   │   ├── AudioEffectManager.kt   # Gestor de EQ 10 bandas, BassBoost y Audio 8D
│   │   │   │   │   ├── HeadphoneController.kt  # Gestor de auriculares, Becoming Noisy y botones físicos
│   │   │   │   │   ├── NativeAudioEngine.kt    # Puente JNI hacia C++20 (EQ 10 bandas, 8D, Crossfeed y Balance)
│   │   │   │   │   ├── NativeAudioProcessor.kt # Procesador Media3 para buffers PCM con onFlush()
│   │   │   │   │   └── controllers/            # Controladores modulares desacoplados de reproducción
│   │   │   │   │       ├── ABLoopController.kt     # Repetidor A-B con ajuste fino y marcado de puntos
│   │   │   │   │       ├── AudioFadeController.kt  # Control de fundidos suaves (Fade-In y Fade-Out)
│   │   │   │   │       ├── PlayerQueueController.kt# Cola de reproducción, modos Shuffle y Repetición
│   │   │   │   │       └── MediaSessionBridge.kt   # Puente Media3 MediaSession y System Media Controls
│   │   │   │   ├── ui/
│   │   │   │   │   ├── components/             # Componentes visuales reutilizables
│   │   │   │   │   │   ├── ArtworkImage.kt     # Renderizador de carátulas (WebP + Procedural)
│   │   │   │   │   │   ├── PlaylistCoverCollage.kt # Renderizador de portada personalizada o collage dinámico (1 a 4 fotos)
│   │   │   │   │   │   ├── AudioEffectsBottomSheet.kt # Modal unificado orquestador de EQ 10 bandas, 8D, Bucle A-B y efectos
│   │   │   │   │   │   ├── audioeffects/       # Pestañas modulares de efectos acústicos
│   │   │   │   │   │   │   ├── EqualizerTabContent.kt         # Ecualizador 10 bandas ISO, presets y Bass Boost
│   │   │   │   │   │   │   ├── Spatial8DTabContent.kt         # Motor Audio 8D Espacial y controles de órbita
│   │   │   │   │   │   │   ├── ReverbTabContent.kt            # Suite Reverb híbrida (Presets + personalización)
│   │   │   │   │   │   │   ├── SleepTimerTabContent.kt        # Temporizador de apagado con fade-out de 10s
│   │   │   │   │   │   │   ├── PlaybackParametersTabContent.kt# Velocidad y Tono (Pitch Shift) con protección
│   │   │   │   │   │   │   ├── TransitionsTabContent.kt       # Repetidor A-B con ajuste fino, Crossfade 0-12s y Gapless
│   │   │   │   │   │   │   └── BalanceAndHeadphonesTabContent.kt# Balance L/R y Crossfeed C++20 rápido
│   │   │   │   │   │   ├── AudioVisualizer.kt  # Visualizador de ondas en tiempo real con degradado dinámico
│   │   │   │   │   │   ├── BackgroundVideoPlayer.kt # Renderizador de video de fondo sincronizado y 100% despejado
│   │   │   │   │   │   ├── BottomNavBar.kt     # Barra de navegación limpia (4 pestañas)
│   │   │   │   │   │   ├── EditTrackDialog.kt  # Modal con Photo Picker y edición de carátula/video
│   │   │   │   │   │   ├── MiniPlayer.kt       # Mini reproductor tintado con marquesina y Video Canvas miniatura
│   │   │   │   │   │   ├── ProceduralArtwork.kt # Arte vectorial dinámico en tiempo real
│   │   │   │   │   │   ├── TrackListItem.kt    # Fila de canción con marquesina en pista activa y menú contextual
│   │   │   │   │   │   ├── VideoToMusicDialog.kt # Diálogo de conversión Video a Música con interruptor de recorte de silencios
│   │   │   │   │   │   ├── DownloadFromLinkDialog.kt # Diálogo de descarga web/TikTok/YouTube con recorte de silencios
│   │   │   │   │   │   └── SearchLyricsDialog.kt # Diálogo de búsqueda interactiva de letras y recomendación oficial
│   │   │   │   │   ├── navigation/
│   │   │   │   │   │   └── NavScreen.kt        # Destinos de navegación y pestañas
│   │   │   │   │   ├── screens/                # Pantallas principales modulares
│   │   │   │   │   │   ├── home/HomeScreen.kt  # Pantalla de inicio con saludo y accesos
│   │   │   │   │   │   ├── library/
│   │   │   │   │   │   │   ├── LibraryScreen.kt# Biblioteca y orquestador de listas
│   │   │   │   │   │   │   └── components/     # Componentes modulares de biblioteca
│   │   │   │   │   │   │       ├── FavoritesPlaylistBannerCard.kt # Tarjeta destacada de "Tus Me Gusta"
│   │   │   │   │   │   │       ├── PlaylistRowItem.kt             # Elemento de lista con menú contextual
│   │   │   │   │   │   │       ├── AlbumAndArtistCards.kt         # Vistas agrupadas de Álbumes y Artistas
│   │   │   │   │   │   │       ├── PlaylistDialogs.kt             # Diálogos de creación y renombrado
│   │   │   │   │   │   │       └── LibraryEmptyViews.kt           # Estados vacíos y botón de importación SAF
│   │   │   │   │   │   ├── importmusic/ImportMusicScreen.kt # Importación selectiva SAF
│   │   │   │   │   │   ├── nowplaying/
│   │   │   │   │   │   │   ├── NowPlayingScreen.kt # Vista completa de reproducción
│   │   │   │   │   │   │   └── components/     # Componentes modulares de reproducción
│   │   │   │   │   │   │       ├── NowPlayingTopBar.kt            # Barra superior con botones de acción
│   │   │   │   │   │   │       ├── NowPlayingArtworkCard.kt       # Carátula y Video Canvas con aura y respiración C++
│   │   │   │   │   │   │       ├── NowPlayingLyricsCard.kt        # Tarjeta Karaoke interactiva con auto-scroll y resaltado
│   │   │   │   │   │   │       ├── NowPlayingPlaybackControls.kt  # Info de pista, seekbar y botonera de control
│   │   │   │   │   │   │       ├── NowPlayingBalanceBar.kt        # Barra de balance estéreo L/R en vivo
│   │   │   │   │   │   │       ├── NowPlayingQueueSheet.kt        # Hoja modal de cola ("Up Next") con carátulas y marquesina
│   │   │   │   │   │   │       ├── AudioSpecsDialog.kt            # Diálogo con ficha técnica del archivo
│   │   │   │   │   │   │       └── VideoDisplayModeDialog.kt      # Diálogo selector de los 3 modos de video
│   │   │   │   │   │   ├── onboarding/
│   │   │   │   │   │   │   └── OnboardingScreen.kt # Pantallas independientes de bienvenida, inducción y disclaimer de almacenamiento
│   │   │   │   │   │   ├── equalizer/EqualizerScreen.kt # Referencia de ecualizador (integrado en modal)
│   │   │   │   │   │   ├── settings/
│   │   │   │   │   │   │   ├── SettingsScreen.kt # Pantalla de ajustes y selector de pestañas
│   │   │   │   │   │   │   └── components/     # Componentes modulares de configuración
│   │   │   │   │   │   │       ├── AppearanceSettingsTab.kt       # Pestaña de temas OLED, paletas y privacidad
│   │   │   │   │   │   │       ├── StoredMediaSettingsTab.kt      # Pestaña de inspección de carátulas WebP y videos
│   │   │   │   │   │   │       ├── HeadphoneStatusCard.kt         # Tarjeta de estado de auriculares en tiempo real
│   │   │   │   │   │   │       ├── HeadphonesAcousticsSection.kt  # Crossfeed C++20 y balance estéreo fino
│   │   │   │   │   │   │       ├── HeadphonesSecuritySection.kt   # Becoming Noisy, Fade-In y memoria volumen
│   │   │   │   │   │   │       ├── HeadphonesGesturesSection.kt   # Configuración de botones físicos de audífonos
│   │   │   │   │   │   │       ├── HeadsetButtonActionDialog.kt   # Diálogo selector de acción por pulsación
│   │   │   │   │   │   │       └── SettingDetailRow.kt            # Fila de datos clave-valor reutilizable
│   │   │   │   │   │   └── playlist/PlaylistDetailScreen.kt # Detalle de lista sincronizada
│   │   │   │   │   └── theme/
│   │   │   │   │       ├── ArtworkColorExtractor.kt # Extractor reactivo de paleta para carátulas y videos
│   │   │   │   │       ├── Color.kt            # Paleta de colores Dark Luxury y Neón
│   │   │   │   │       ├── Theme.kt            # Configuración de MaterialTheme M3
│   │   │   │   │       └── Type.kt             # Tipografía M3
│   │   │   │   └── viewmodel/
│   │   │   │       ├── MusicViewModel.kt       # ViewModel central reactivo modularizado
│   │   │   │       └── delegates/              # Coordinadores modulares especializados
│   │   │   │           ├── LyricsCoordinator.kt           # Gestión de letras LRC, LRCLIB y búsqueda
│   │   │   │           ├── IncomingMediaCoordinator.kt    # Intents "Abrir con..." y recorte de silencios
│   │   │   │           └── HeadphoneSettingsCoordinator.kt# Parámetros DSP y gestos de auriculares
│   │   │   └── res/                            # Recursos gráficos, iconos y strings
│   │   └── test/                               # Pruebas unitarias y Robolectric
│   └── build.gradle.kts                        # Configuración Gradle con CMake, NDK, LeakCanary y tarea provisionNativeDeps (Cero .sh)
├── gradle/
│   └── libs.versions.toml                      # Catálogo de versiones centralizado (incluye CPython 3.11, FFmpeg y LeakCanary)
├── README.md                                   # Descripción general e instalación
├── ROADMAP.md                                  # Fases de desarrollo y avances
├── STRUCTURE.md                                # Arquitectura y mapa de archivos
├── AI_CONTEXT.md                               # Contexto técnico para inteligencias artificiales
└── AGENTS.md                                   # Manual de operaciones para agentes de desarrollo
```

---

## 🗄️ Estructura de Almacenamiento Privado

El almacenamiento estructurado en `Android/data/com.aistudio.musicplayer.aurasound/files/` se organiza en:
- `images/`: Archivos `.webp` de carátulas de álbumes y personalizadas comprimidas sin pérdida.
- `songs/`: Archivos de audio locales generados o importados.
- `lyrics/`: Letras de canciones en formato `.lrc`.
- `metadata/`: Archivos `.json` individuales con metadatos descriptivos de cada canción.
- `videos/`: Videos de fondo vinculados y loops de Canvas (.mp4 / .webm).
- Directorio de aplicación interno (`filesDir`): Almacena `aura_debug_logs.json` con el historial persistente de incidencias capturadas por **Aura Monitor**.

---

## 🧹 Limpieza Atómica de Buffer (Buffer Flushing) en C++20 y Media3

Para erradicar definitivamente chasquidos acústicos ("pops"), clics digitales y colas de eco residuales entre pistas, el sistema implementa una canalización de vaciado atómico:

1. **Gatillos de Ejecución:**
   - **Cambio de Canción (`playTrack`):** Vaciado inmediato antes de decodificar la nueva pista.
   - **Saltos de Progreso (`seekTo`):** Vaciado antes de que el motor de renderizado aplique el nuevo timestamp.
   - **Pausa / Detención (`pause` y `release`):** Vaciado para cortar colas de reverb y acumuladores IIR.
   - **Media3 AudioProcessor (`onFlush`):** Sincronización automática a nivel de tubería interna de ExoPlayer.

2. **Componentes Nativos C++20 purgados a cero:**
   - `BiquadPeakingFilter::reset()`: Variables de estado `x1, x2, y1, y2` en las 10 bandas del ecualizador.
   - `BassBoostFilter::reset()`: Acumuladores del filtro paso-bajos a 60 Hz.
   - `CrossfeedProcessor::reset()`: Líneas de retardo ITD (~2ms) y filtros paso-bajos Bauer / Chu Moy.
   - `ReverbProcessor::resetBuffers()`: Líneas de retardo de peines amortiguados (`mCombL1-4`, `mCombR1-4`) y filtros pasa-todo (`mAllpassL1-2`, `mAllpassR1-2`).
   - `EightDProcessor::reset()`: Paneo orbital y buffer estéreo.

---

## 🧩 Estado de Modularización y Desarrollo Modular Completado

Siguiendo el principio de **desarrollo modular** para evitar el colapso de la aplicación y mantener los archivos por debajo de 500 líneas:

### 1. Archivos Modularizados con Éxito:
- **`AuraAudioPlayer.kt`** (de 664 líneas a 409 líneas):
  - Desacoplado mediante controladores especializados en `playback/controllers/`:
    - `PlayerQueueController.kt` (138 líneas): Gestión atómica de cola de reproducción, modo aleatorio (Shuffle), repetición (OFF/ALL/ONE) y sincronización reactiva de metadatos.
    - `AudioFadeController.kt` (93 líneas): Atenuación progresiva (fade-out), rampa suave de entrada (fade-in) y memoria de volumen original.
    - `ABLoopController.kt` (76 líneas): Repetidor de segmento A-B, ajuste fino interactivo y marcado seguro de timestamps.
    - `MediaSessionBridge.kt` (118 líneas): Puente con Media3 MediaSession, ForwardingPlayer y System Media Controls.
- **`OnlineVideoAudioImporter.kt`** (de 967 líneas a 345 líneas):
  - Desacoplado en componentes de red, resolución y procesamiento multimedia:
    - `ChunkedStreamDownloader.kt` (190 líneas): Acelerador de descarga por bloques HTTP Range (evita estrangulamiento de 63 KB/s) y descarga lineal con reintentos limpios en HTTP 403.
    - `TikTokMediaResolver.kt` (95 líneas): Resolvedor de endpoints TikWM y Tiklydown sin marcas de agua y limpieza de títulos.
    - `MediaAssetProcessor.kt` (215 líneas): Descarga oficial de carátulas en cascada, extracción de fotogramas clave en WebP sin pérdida y generación de Canvas 480p (Seamless Loop y Boomerang en FFmpeg).
- **`MusicRepository.kt`** (de 548 líneas a 210 líneas):
  - Desacoplado en repositorios especializados e importadores:
    - `PlaylistRepository.kt` (102 líneas): Gestión de listas de reproducción, referencias cruzadas (Room) y carátulas personalizadas/collage dinámico.
    - `SafTrackImporter.kt` (228 líneas): Importación selectiva SAF, escaneo recursivo de directorios, recorte de silencios, conversión de video a música e importación desde Intents externos.
- **`auramusic_dsp.h`** (de 808 líneas a 393 líneas):
  - Dividido en `dsp_filters.h` (91 líneas), `dsp_spatial.h` (121 líneas), `dsp_crossfeed.h` (133 líneas) y `dsp_reverb.h` (171 líneas).
- **`MusicViewModel.kt`** (de 1,185 líneas a 935 líneas):
  - Desacoplado mediante el patrón de Coordinadores en `viewmodel/delegates/`:
    - `LyricsCoordinator.kt` (160 líneas): Carga, sincronización, LRCLIB y búsqueda de letras.
    - `IncomingMediaCoordinator.kt` (120 líneas): Gestión de Intents externos ("Abrir con..."), discriminación y recorte de silencios.
    - `HeadphoneSettingsCoordinator.kt` (75 líneas): Configuración de acústica, botones y gestos de audífonos.

---

## 🔒 Blindaje de Seguridad y Protección Criptográfica

1. **Permisos de Red y Conectividad (`AndroidManifest.xml`)**:
   - Inclusión obligatoria de `<uses-permission android:name="android.permission.INTERNET" />` y `ACCESS_NETWORK_STATE`, garantizando la resolución segura de letras LRCLIB, descargas multimedia y telemetría de red.

2. **Mitigación de Ejecución Remota de Código (RCE en `YtDlpAutoUpdater`)**:
   - Descarga atómica con verificación estricta de hash criptográfico `SHA-256` contra el archivo oficial `SHA2-256SUMS` publicado en GitHub Releases.
   - Validación de host de descarga (`github.com` / `objects.githubusercontent.com`).

3. **Neutralización de Zip Slip / Path Traversal**:
   - Validación canónica de rutas (`canonicalFile.toPath().startsWith(...)`) en la descompresión de paquetes nativos dentro de `FFmpegNativeEngine` y `YtDlpNativeEngine`.

4. **Blindaje de Procesos CLI e Intents**:
   - Uso de delimitador de argumentos (`--`) en `YtDlpNativeEngine` previniendo inyecciones de flags CLI.
   - Aislamiento y sanitización de intents externos en `DebugMonitorActivity`.

---

## 🛠️ Aprovisionamiento y Compilación Nativa 100% Pura en Gradle y CMake (Cero `.sh`)

- **`app/build.gradle.kts` (`provisionNativeDeps` y `ensureDebugKeystore`)**: Orquestador nativo en Kotlin DSL que reemplaza y elimina por completo los antiguos scripts `.sh`:
  - **Motor `yt-dlp` Puro**: Descarga oficial con verificación criptográfica SHA-256 (`SHA2-256SUMS`) en `src/main/assets/bin/yt-dlp`.
  - **Motor QuickJS C99 Puro**: Descarga las fuentes originales de Fabrice Bellard y las compila con NDK Clang como ejecutable PIE (`libqjs.so`) para las 4 ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`).
  - **Motor CPython 3.11 Puro Multi-ABI**: Resuelve los artefactos nativos de `com.chaquo.python:target:3.11.6-0`, extrae `libpython3.11.so`, OpenSSL (`libcrypto`, `libssl`), `libsqlite3`, la librería estándar (`stdlib`) y módulos C nativos en `libpython.zip.so`, y compila `native_python_launcher.c` en `libpython.so` con `-Wl,-rpath,$ORIGIN` y cero llamadas a `/system/bin/sh`.
  - **Motor FFmpeg Puro Multi-ABI**: Resuelve el paquete nativo multi-ABI de FFmpeg (`libavcodec`, `libavfilter`, `libavformat`, `libswscale`, `libavutil`, `libffmpegkit`), empaqueta `libffmpeg.zip.so` y compila `native_ffmpeg_launcher.c` en `libffmpeg.so` ejecutando `llvm-strip`.
- **`.github/workflows/build-debug-apk.yml`**: Flujo de trabajo CI/CD sincronizado con `:app:provisionNativeDeps` y `:app:assembleDebug`, con caché inteligente (`actions/cache@v4`), instalación de NDK, preservación de firma permanente (`debug.keystore.base64`) y compilación APK Debug lista para dispositivos móviles.

