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
│   │   │   │   ├── MainActivity.kt             # Actividad principal, insets, animaciones y navegación
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
│   │   │   │   │   ├── AuraAudioPlayer.kt      # Motor Media3 ExoPlayer, cola, volumen y crossfade
│   │   │   │   │   ├── AudioEffectManager.kt   # Gestor de EQ 10 bandas, BassBoost y Audio 8D
│   │   │   │   │   ├── NativeAudioEngine.kt    # Puente JNI hacia C++20 (EQ 10 bandas y Audio 8D)
│   │   │   │   │   └── NativeAudioProcessor.kt # Procesador Media3 para buffers PCM
│   │   │   │   ├── ui/
│   │   │   │   │   ├── components/             # Componentes visuales reutilizables
│   │   │   │   │   │   ├── ArtworkImage.kt     # Renderizador de carátulas (WebP + Procedural)
│   │   │   │   │   │   ├── AudioEffectsBottomSheet.kt # Hoja modal de Temporizador, 8D, Speed/Pitch y Crossfade
│   │   │   │   │   │   ├── AudioVisualizer.kt  # Visualizador de ondas en tiempo real
│   │   │   │   │   │   ├── BottomNavBar.kt     # Barra de navegación limpia (4 pestañas)
│   │   │   │   │   │   ├── EditTrackDialog.kt  # Modal con Photo Picker y edición de carátula
│   │   │   │   │   │   ├── MiniPlayer.kt       # Mini reproductor opaco con acceso a EQ
│   │   │   │   │   │   ├── ProceduralArtwork.kt # Arte vectorial dinámico en tiempo real
│   │   │   │   │   │   └── TrackListItem.kt    # Fila de canción con menú contextual
│   │   │   │   │   ├── navigation/
│   │   │   │   │   │   └── NavScreen.kt        # Destinos de navegación y pestañas
│   │   │   │   │   ├── screens/                # Pantallas principales modulares
│   │   │   │   │   │   ├── home/HomeScreen.kt  # Pantalla de inicio con saludo y accesos
│   │   │   │   │   │   ├── library/LibraryScreen.kt # Biblioteca, Playlists y Tus Me Gusta
│   │   │   │   │   │   ├── importmusic/ImportMusicScreen.kt # Importación selectiva SAF
│   │   │   │   │   │   ├── nowplaying/NowPlayingScreen.kt # Vista completa de reproducción opaca
│   │   │   │   │   │   ├── equalizer/EqualizerScreen.kt # Control de ecualizador 10 bandas C++20
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
│   └── build.gradle.kts                        # Configuración Gradle con CMake y NDK
├── scripts/
│   └── generate_keystore_and_build.sh          # Script ejecutable de generación de firma y build
├── gradle/
│   └── libs.versions.toml                      # Catálogo de versiones centralizado
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
