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
│   │   │   │   │   └── DebugMonitorActivity.kt # Actividad accesible desde el cajón de apps (Aura Monitor)
│   │   │   │   ├── data/
│   │   │   │   │   ├── importer/               # Módulos de importación y análisis
│   │   │   │   │   │   ├── AudioMetadataParser.kt # Extractor ID3 y conversor a WebP
│   │   │   │   │   │   ├── ProceduralArtworkGenerator.kt # Generador procedural de carátulas
│   │   │   │   │   │   └── SampleMusicGenerator.kt # Sintetizador de audio WAV para demos
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
│   │   │   │   │   ├── Playlist.kt             # Modelo de datos de lista
│   │   │   │   │   ├── RepeatMode.kt           # Enum de modos de repetición
│   │   │   │   │   ├── EqualizerConfig.kt      # Modelo de 10 bandas y presets de EQ
│   │   │   │   │   ├── AudioEffectsConfig.kt   # Modelos para Audio 8D, Temporizador y Gapless/Crossfade
│   │   │   │   │   └── ThemePalette.kt         # Enum de temas de color vibrantes
│   │   │   │   ├── playback/                   # Capa de reproducción de audio
│   │   │   │   │   ├── AuraAudioPlayer.kt      # Motor Media3 ExoPlayer, cola, throttling y recuperación
│   │   │   │   │   ├── AudioEffectManager.kt   # Gestor de EQ 10 bandas, BassBoost y Audio 8D
│   │   │   │   │   ├── NativeAudioEngine.kt    # Puente JNI hacia C++20 (EQ 10 bandas y Audio 8D)
│   │   │   │   │   └── NativeAudioProcessor.kt # Procesador Media3 para buffers PCM
│   │   │   │   ├── ui/
│   │   │   │   │   ├── components/             # Componentes visuales reutilizables
│   │   │   │   │   │   ├── ArtworkImage.kt     # Renderizador de carátulas (WebP + Procedural)
│   │   │   │   │   │   ├── AudioEffectsBottomSheet.kt # Modal unificado de EQ 10 bandas, Temporizador, 8D y Speed
│   │   │   │   │   │   ├── AudioVisualizer.kt  # Visualizador de ondas en tiempo real
│   │   │   │   │   │   ├── BackgroundVideoPlayer.kt # Renderizador de video de fondo (Loops Canvas y Video Sync)
│   │   │   │   │   │   ├── BottomNavBar.kt     # Barra de navegación limpia (4 pestañas)
│   │   │   │   │   │   ├── EditTrackDialog.kt  # Modal con Photo Picker y edición de carátula/video
│   │   │   │   │   │   ├── MiniPlayer.kt       # Mini reproductor opaco con acceso directo a EQ modal
│   │   │   │   │   │   ├── ProceduralArtwork.kt # Arte vectorial dinámico en tiempo real
│   │   │   │   │   │   └── TrackListItem.kt    # Fila de canción con menú contextual
│   │   │   │   │   ├── navigation/
│   │   │   │   │   │   └── NavScreen.kt        # Destinos de navegación y pestañas
│   │   │   │   │   ├── screens/                # Pantallas principales modulares
│   │   │   │   │   │   ├── home/HomeScreen.kt  # Pantalla de inicio con saludo y accesos
│   │   │   │   │   │   ├── library/LibraryScreen.kt # Biblioteca, Playlists y Tus Me Gusta
│   │   │   │   │   │   ├── importmusic/ImportMusicScreen.kt # Importación selectiva SAF
│   │   │   │   │   │   ├── nowplaying/NowPlayingScreen.kt # Vista completa de reproducción con cierre fluido
│   │   │   │   │   │   ├── equalizer/EqualizerScreen.kt # Referencia de ecualizador (integrado en modal)
│   │   │   │   │   │   ├── settings/SettingsScreen.kt # Selector de temas y privacidad
│   │   │   │   │   │   └── playlist/PlaylistDetailScreen.kt # Detalle de lista sincronizada
│   │   │   │   │   └── theme/
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
