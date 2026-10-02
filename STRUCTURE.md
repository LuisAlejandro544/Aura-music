# Estructura del Proyecto (Aura Music) 🏗️

Este documento describe la arquitectura modular, la jerarquía de directorios y las responsabilidades de cada componente del código fuente.

---

## 📂 Árbol de Directorios

```
AuraMusic/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── cpp/                                # Código Nativo C++20 (DSP y Audio Avanzado)
│   │   │   │   ├── CMakeLists.txt                  # Configuración de compilación CMake (C++20)
│   │   │   │   ├── auramusic_dsp.h                 # Ecualizador 10 bandas biquad, limiter y buffers
│   │   │   │   └── auramusic_dsp.cpp               # Implementación JNI del motor nativo
│   │   │   ├── java/com/example/
│   │   │   │   ├── MainActivity.kt                 # Actividad principal, insets y navegación global
│   │   │   │   ├── data/
│   │   │   │   │   ├── importer/                   # Módulos de importación y análisis
│   │   │   │   │   │   ├── AudioMetadataParser.kt  # Extractor de metadatos ID3 y conversor a WebP
│   │   │   │   │   │   ├── ProceduralArtworkGenerator.kt # Generador procedural de carátulas
│   │   │   │   │   │   └── SampleMusicGenerator.kt # Sintetizador de audio WAV para demos
│   │   │   │   │   ├── local/                      # Capa de persistencia local Room SQLite
│   │   │   │   │   │   ├── AppDatabase.kt          # Base de datos Room
│   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   │   ├── TrackDao.kt         # DAO para pistas de música y edición
│   │   │   │   │   │   │   └── PlaylistDao.kt      # DAO para listas de reproducción
│   │   │   │   │   │   └── entity/
│   │   │   │   │   │       ├── TrackEntity.kt      # Entidad de pista en Room
│   │   │   │   │   │       ├── PlaylistEntity.kt   # Entidad de lista en Room
│   │   │   │   │   │       └── PlaylistTrackCrossRef.kt # Relación muchos a muchos
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   └── MusicRepository.kt      # Repositorio central de datos y SAF
│   │   │   │   │   └── storage/
│   │   │   │   │       └── AppStorageManager.kt    # Almacenamiento estructurado (images, songs, lyrics, metadata)
│   │   │   │   ├── model/                          # Modelos de dominio
│   │   │   │   │   ├── Track.kt                    # Modelo de datos de canción
│   │   │   │   │   ├── Playlist.kt                 # Modelo de datos de lista
│   │   │   │   │   ├── RepeatMode.kt               # Enum de modos de repetición
│   │   │   │   │   ├── EqualizerConfig.kt          # Modelo de 10 bandas y presets de EQ
│   │   │   │   │   └── ThemePalette.kt             # Enum de temas de color vibrantes
│   │   │   │   ├── playback/                       # Capa de reproducción de audio
│   │   │   │   │   ├── AuraAudioPlayer.kt          # Motor Media3 ExoPlayer y cola
│   │   │   │   │   ├── AudioEffectManager.kt       # Gestor de Equalizer 10 bandas y BassBoost
│   │   │   │   │   ├── NativeAudioEngine.kt        # Puente JNI hacia C++20 con respaldo biquad
│   │   │   │   │   └── NativeAudioProcessor.kt     # Procesador Media3 para buffers PCM
│   │   │   │   ├── ui/
│   │   │   │   │   ├── components/                 # Componentes visuales reutilizables
│   │   │   │   │   │   ├── ArtworkImage.kt         # Renderizador de carátulas (WebP + Procedural)
│   │   │   │   │   │   ├── AudioVisualizer.kt      # Visualizador de ondas en tiempo real
│   │   │   │   │   │   ├── BottomNavBar.kt         # Barra de navegación limpia (4 pestañas)
│   │   │   │   │   │   ├── EditTrackDialog.kt      # Modal para editar título, artista y álbum
│   │   │   │   │   │   ├── MiniPlayer.kt           # Mini reproductor opaco con acceso a EQ
│   │   │   │   │   │   ├── ProceduralArtwork.kt    # Arte vectorial dinámico en tiempo real
│   │   │   │   │   │   └── TrackListItem.kt        # Fila de canción con menú de edición y acciones
│   │   │   │   │   ├── navigation/
│   │   │   │   │   │   └── NavScreen.kt            # Destinos de navegación y pestañas
│   │   │   │   │   ├── screens/                    # Pantallas principales modulares
│   │   │   │   │   │   ├── home/HomeScreen.kt      # Pantalla de inicio con saludo y accesos
│   │   │   │   │   │   ├── library/LibraryScreen.kt # Tu Biblioteca con pestañas y búsqueda
│   │   │   │   │   │   ├── importmusic/ImportMusicScreen.kt # Importación selectiva SAF
│   │   │   │   │   │   ├── nowplaying/NowPlayingScreen.kt # Vista completa de reproducción opaca
│   │   │   │   │   │   ├── equalizer/EqualizerScreen.kt # Control de ecualizador 10 bandas C++20
│   │   │   │   │   │   ├── settings/SettingsScreen.kt # Selector de temas y privacidad
│   │   │   │   │   │   └── playlist/PlaylistDetailScreen.kt # Vista de lista específica
│   │   │   │   │   └── theme/
│   │   │   │   │       ├── Color.kt                # Paleta de colores Dark Luxury y Neón
│   │   │   │   │       ├── Theme.kt                # Configuración de MaterialTheme M3
│   │   │   │   │       └── Type.kt                 # Tipografía M3
│   │   │   │   └── viewmodel/
│   │   │   │       └── MusicViewModel.kt           # ViewModel central reactivo
│   │   │   └── res/                                # Recursos gráficos, iconos y strings
│   │   └── test/                                   # Pruebas unitarias y Robolectric
│   └── build.gradle.kts                            # Configuración Gradle del módulo
├── gradle/
│   └── libs.versions.toml                          # Catálogo de versiones centralizado
├── README.md                                       # Descripción general e instalación
├── ROADMAP.md                                      # Fases de desarrollo futuro
├── STRUCTURE.md                                    # Arquitectura y mapa de archivos
├── AI_CONTEXT.md                                   # Contexto técnico para inteligencias artificiales
└── AGENTS.md                                       # Manual de operaciones para agentes de desarrollo
```

---

## 🗄️ Estructura de Almacenamiento Privado

El almacenamiento estructurado en `Android/data/com.aistudio.musicplayer.aurasound/files/` se organiza en:
- `images/`: Archivos `.webp` de carátulas comprimidas sin pérdida.
- `songs/`: Archivos de audio locales generados o importados.
- `lyrics/`: Letras de canciones en formato `.lrc`.
- `metadata/`: Archivos `.json` individuales con metadatos descriptivos de cada canción.
