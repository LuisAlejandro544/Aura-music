# Estructura del Proyecto (Aura Music) 🏗️

Este documento describe la arquitectura modular, la jerarquía de directorios y las responsabilidades de cada componente del código fuente.

---

## 📂 Árbol de Directorios

```
AuraMusic/
├── .github/
│   └── workflows/
│       └── build-debug-apk.yml                 # CI/CD: Workflow manual de compilación APK Debug y firma
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── cpp/                            # Código Nativo C++20 (DSP y Audio Avanzado)
│   │   │   │   ├── CMakeLists.txt              # Configuración CMake integrada en Gradle (C++20)
│   │   │   │   ├── auramusic_dsp.h             # Ecualizador 10 bandas biquad, limiter y buffers
│   │   │   │   └── auramusic_dsp.cpp           # Implementación JNI del motor nativo
│   │   │   ├── java/com/example/
│   │   │   │   ├── AuraApplication.kt         # Clase Application con inicio de AuraDebugManager
│   │   │   │   ├── MainActivity.kt             # Actividad principal, insets, animaciones y navegación
│   │   │   │   ├── debug/                      # Suite Autónoma de Diagnóstico y Telemetría
│   │   │   │   │   ├── DebugSeverity.kt        # Enum de severidades (CRASH, CRÍTICO, ERROR, WARNING, INFO)
│   │   │   │   │   ├── DeviceDiagnosticInfo.kt # Ficha técnica de hardware del teléfono
│   │   │   │   │   ├── DebugLogEntry.kt        # Modelo de eventos con stacktrace en crudo y hora
│   │   │   │   │   ├── AuraDebugManager.kt     # Gestor central de logs, persistencia JSON y UncaughtHandler
│   │   │   │   │   ├── DebugMonitorActivity.kt # Actividad accesible desde el cajón de apps (Aura Monitor)
│   │   │   │   │   └── ui/                     # Componentes modulares de Aura Monitor
│   │   │   │   │       ├── HardwareTelemetryCard.kt   # Ficha técnica de hardware, RAM y CPU ABI
│   │   │   │   │       ├── DebugLogEntryCard.kt       # Tarjeta individual para logs con severidad
│   │   │   │   │       ├── DebugFilterChips.kt        # Filtros por severidad con contadores en vivo
│   │   │   │   │       ├── DebugLogDetailDialog.kt    # Modal de visualización de Stack Trace en crudo
│   │   │   │   │       └── DebugSyntheticTestDialog.kt# Menú para simulación sintética de incidencias
│   │   │   │   ├── data/
│   │   │   │   │   ├── importer/               # Módulos de importación y análisis
│   │   │   │   │   │   ├── AudioMetadataParser.kt # Extractor ID3 y conversor a WebP
│   │   │   │   │   │   ├── LyricsManager.kt        # Analizador de .LRC, descarga de LRCLIB y persistencia local
│   │   │   │   │   │   ├── OnlineVideoAudioImporter.kt # Descargador de audio, carátula y Video Canvas desde TikTok y web
│   │   │   │   │   │   ├── ProceduralArtworkGenerator.kt # Generador procedural de carátulas
│   │   │   │   │   │   ├── SampleMusicGenerator.kt # Sintetizador de audio WAV para demos
│   │   │   │   │   │   ├── VideoAudioExtractor.kt  # Extractor de audio nativo y generador 3 en 1 de Video a Música
│   │   │   │   │   │   ├── InnerTubeClient.kt      # Cliente InnerTube multi-cliente sin fricción (ANDROID_VR, VISIONOS)
│   │   │   │   │   │   ├── InvidiousStreamResolver.kt # Resolvedor de respaldo para canciones con restricción de derechos
│   │   │   │   │   │   ├── HeadlessWebViewExtractor.kt # Extractor móvil en segundo plano sobre m.youtube.com
│   │   │   │   │   │   └── WebStreamExtractor.kt   # Orquestador híbrido de 3 niveles con fallback automático
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
│   │   │   │   │   │   └── MusicRepository.kt  # Repositorio central de datos, SAF y Playlists
│   │   │   │   │   └── storage/
│   │   │   │   │       └── AppStorageManager.kt # Almacenamiento estructurado y carátulas WebP
│   │   │   │   ├── model/                      # Modelos de dominio
│   │   │   │   │   ├── Track.kt                # Modelo de datos de canción
│   │   │   │   │   ├── LyricsState.kt          # Modelo de letras sincronizadas (.LRC) y líneas temporizadas
│   │   │   │   │   ├── Playlist.kt             # Modelo de datos de lista
│   │   │   │   │   ├── RepeatMode.kt           # Enum de modos de repetición
│   │   │   │   │   ├── EqualizerConfig.kt      # Modelo de 10 bandas y presets de EQ
│   │   │   │   │   ├── AudioEffectsConfig.kt   # Modelos para Audio 8D, Temporizador y Gapless/Crossfade
│   │   │   │   │   ├── HeadphoneConfig.kt      # Configuración de Crossfeed C++20, Balance L/R y gestos
│   │   │   │   │   ├── VideoDisplayMode.kt     # Enum de modos de visualización de video (Fondo Completo, Carátula, Off)
│   │   │   │   │   └── ThemePalette.kt         # Enum de temas de color vibrantes
│   │   │   │   ├── playback/                   # Capa de reproducción de audio
│   │   │   │   │   ├── AuraAudioPlayer.kt      # Motor Media3 ExoPlayer, cola, throttling y recuperación
│   │   │   │   │   ├── AuraMediaPlaybackService.kt # Servicio MediaSessionService en primer plano y notificación nativa
│   │   │   │   │   ├── AudioEffectManager.kt   # Gestor de EQ 10 bandas, BassBoost y Audio 8D
│   │   │   │   │   ├── HeadphoneController.kt  # Gestor de auriculares, Becoming Noisy y botones físicos
│   │   │   │   │   ├── NativeAudioEngine.kt    # Puente JNI hacia C++20 (EQ 10 bandas, 8D, Crossfeed y Balance)
│   │   │   │   │   └── NativeAudioProcessor.kt # Procesador Media3 para buffers PCM
│   │   │   │   ├── ui/
│   │   │   │   │   ├── components/             # Componentes visuales reutilizables
│   │   │   │   │   │   ├── ArtworkImage.kt     # Renderizador de carátulas (WebP + Procedural)
│   │   │   │   │   │   ├── AudioEffectsBottomSheet.kt # Modal unificado orquestador de EQ 10 bandas, 8D y efectos
│   │   │   │   │   │   ├── audioeffects/       # Pestañas modulares de efectos acústicos
│   │   │   │   │   │   │   ├── EqualizerTabContent.kt         # Ecualizador 10 bandas ISO, presets y Bass Boost
│   │   │   │   │   │   │   ├── Spatial8DTabContent.kt         # Motor Audio 8D Espacial y controles de órbita
│   │   │   │   │   │   │   ├── ReverbTabContent.kt            # Suite Reverb híbrida (Presets + personalización)
│   │   │   │   │   │   │   ├── SleepTimerTabContent.kt        # Temporizador de apagado con fade-out de 10s
│   │   │   │   │   │   │   ├── PlaybackParametersTabContent.kt# Velocidad y Tono (Pitch Shift) con protección
│   │   │   │   │   │   │   ├── TransitionsTabContent.kt       # Crossfade de 0-12s y conmutador Gapless
│   │   │   │   │   │   │   └── BalanceAndHeadphonesTabContent.kt# Balance L/R y Crossfeed C++20 rápido
│   │   │   │   │   │   ├── AudioVisualizer.kt  # Visualizador de ondas en tiempo real con degradado dinámico
│   │   │   │   │   │   ├── BackgroundVideoPlayer.kt # Renderizador de video de fondo (Loops Canvas y Video Sync)
│   │   │   │   │   │   ├── BottomNavBar.kt     # Barra de navegación limpia (4 pestañas)
│   │   │   │   │   │   ├── EditTrackDialog.kt  # Modal con Photo Picker y edición de carátula/video
│   │   │   │   │   │   ├── MiniPlayer.kt       # Mini reproductor tintado con controles Anterior/Play/Siguiente
│   │   │   │   │   │   ├── ProceduralArtwork.kt # Arte vectorial dinámico en tiempo real
│   │   │   │   │   │   ├── TrackListItem.kt    # Fila de canción con menú contextual
│   │   │   │   │   │   ├── VideoToMusicDialog.kt # Diálogo de conversión y previsualización de Video a Música
│   │   │   │   │   │   └── DownloadFromLinkDialog.kt # Diálogo de descarga desde enlaces de TikTok y videos web
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
│   │   │   │   │   │   │       ├── NowPlayingArtworkCard.kt       # Carátula y Video Canvas con aura, respiración C++ y cápsula ergonómica
│   │   │   │   │   │   │       ├── NowPlayingLyricsCard.kt        # Tarjeta Karaoke interactiva con auto-scroll y resaltado neón
│   │   │   │   │   │   │       ├── NowPlayingPlaybackControls.kt  # Info de pista, seekbar y botonera de control
│   │   │   │   │   │   │       ├── NowPlayingBalanceBar.kt        # Barra de balance estéreo L/R en vivo
│   │   │   │   │   │   │       ├── NowPlayingQueueSheet.kt        # Hoja modal de cola de reproducción ("Up Next")
│   │   │   │   │   │   │       ├── AudioSpecsDialog.kt            # Diálogo con ficha técnica del archivo
│   │   │   │   │   │   │       └── VideoDisplayModeDialog.kt      # Diálogo selector de los 3 modos de video
│   │   │   │   │   │   ├── equalizer/EqualizerScreen.kt # Referencia de ecualizador (integrado en modal)
│   │   │   │   │   │   ├── settings/
│   │   │   │   │   │   │   ├── SettingsScreen.kt # Pantalla de ajustes y selector de pestañas
│   │   │   │   │   │   │   └── components/     # Componentes modulares de configuración
│   │   │   │   │   │   │       ├── AppearanceSettingsTab.kt       # Pestaña de temas OLED, paletas y privacidad
│   │   │   │   │   │   │       ├── HeadphoneStatusCard.kt         # Tarjeta de estado de auriculares en tiempo real
│   │   │   │   │   │   │       ├── HeadphonesAcousticsSection.kt  # Crossfeed C++20 y balance estéreo fino
│   │   │   │   │   │   │       ├── HeadphonesSecuritySection.kt   # Becoming Noisy, Fade-In y memoria volumen
│   │   │   │   │   │   │       ├── HeadphonesGesturesSection.kt   # Configuración de botones físicos de audífonos
│   │   │   │   │   │   │       ├── HeadsetButtonActionDialog.kt   # Diálogo selector de acción por pulsación
│   │   │   │   │   │   │       └── SettingDetailRow.kt            # Fila de datos clave-valor reutilizable
│   │   │   │   │   │   └── playlist/PlaylistDetailScreen.kt # Detalle de lista sincronizada
│   │   │   │   │   └── theme/
│   │   │   │   │       ├── ArtworkColorExtractor.kt # Extractor reactivo de paleta para carátulas y fotogramas de video
│   │   │   │   │       ├── Color.kt            # Paleta de colores Dark Luxury y Neón
│   │   │   │   │       ├── Theme.kt            # Configuración de MaterialTheme M3
│   │   │   │   │       └── Type.kt             # Tipografía M3
│   │   │   │   └── viewmodel/
│   │   │   │       └── MusicViewModel.kt       # ViewModel central reactivo
│   │   │   └── res/                            # Recursos gráficos, iconos y strings
│   │   └── test/                               # Pruebas unitarias y Robolectric
│   └── build.gradle.kts                        # Configuración Gradle con CMake, NDK y LeakCanary
├── scripts/
│   └── generate_keystore_and_build.sh          # Script ejecutable de generación de firma y build
├── gradle/
│   └── libs.versions.toml                      # Catálogo de versiones centralizado (incluye LeakCanary)
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
