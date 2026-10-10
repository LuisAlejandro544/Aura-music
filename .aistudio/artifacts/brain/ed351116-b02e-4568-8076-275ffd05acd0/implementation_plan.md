# Implementación de Smart AutoMix (Estilo Apple Music) y Auto-Remix / Mashup de 2 Canciones (Música Local)

Este plan implementa las dos funciones solicitadas por la comunidad (por ahora exclusivas para **música local**, dejando preparada la arquitectura para extenderlo más adelante a streaming), manteniendo intacta toda la información actual y actualizando los archivos `.md` y `chanelog-beta.md`.

---

## 1. Smart AutoMix Inteligente Estilo Apple Music al Terminar Canciones

### Qué mejoraremos
- **Motor DSP C++20 (`dsp_filters.h` & `auramusic_dsp.h`)**:
  - Mejoraremos `DjAutomixFilter` para que realice un **cruce de ecualización progresivo en tiempo real (0.0 → 1.0)** sincronizado con el avance exacto de la transición Dual-Deck (`AudioFadeController`), atenuando progresivamente los subgraves (`<120 Hz`) y protegiendo la banda vocal (`1 kHz – 3.5 kHz`) para que las voces de la canción que termina y la que empieza no choquen.
  - Añadiremos un detector de ventana óptima de salida (*Smart Outro Cue Point*) que evalúa la caída de energía vocal/instrumental en los últimos segundos de las pistas locales para disparar el cruce en el compás más limpio.
- **Controlador de Transiciones (`AudioFadeController.kt` & `AuraAudioPlayer.kt` & `TransitionsTabContent.kt`)**:
  - Durante el cruce `startDualDeckCrossfade`, `AudioFadeController` notificará el progreso continuo (`0f..1f`) al motor C++20 para modular dinámicamente la curva en X.
  - En la hoja modal de efectos (**Transiciones**), enriqueceremos la tarjeta de **Smart AutoMix DJ (Estilo Apple Music — Música Local)** indicando claramente que aplica sobre pistas locales y permitiendo activar tanto la detección inteligente de final de pista como la curva de ecualización armónica en X.

---

## 2. Generador de Auto-Remix / Mashup con 2 Canciones Locales (Manual o Auto-Match)

### Nuevo Motor Modular: `SmartAutoRemixEngine.kt` (`data/importer/mixtape/SmartAutoRemixEngine.kt`)
- Trabaja exclusivamente con **canciones locales** (`!track.isStreamingTrack`) utilizando `FFmpegNativeEngine`:
  1. **Selector Inteligente (`findBestMatchingPair`)**:
     - Si el usuario pulsa **"Selección Inteligente (Auto-Match)"**, analiza la biblioteca o playlist local y escoge las 2 canciones con mejor compatibilidad de duración, ritmo estimado y contraste energético, explicando el motivo del emparejamiento (ej. *"Emparejamiento óptimo: Voces nítidas + Base rítmica compatible"*).
  2. **Estilo 1 — Mashup (Voz Canción A + Base Instrumental Canción B)**:
     - Aplica en el grafo `filter_complex` de FFmpeg aislamiento vocal central sobre la **Canción A** (filtro paso-altos `220 Hz` + realce de presencia vocal `1.5 kHz–4.5 kHz` y enfoque Mid) y extracción de base rítmica/instrumental sobre la **Canción B** (subgraves y cuerpo rítmico `30 Hz–180 Hz` + acompañamiento estéreo Side con atenuación de centro vocal), mezclándolas con compresión dinámica y limitador suave sin saturación.
  3. **Estilo 2 — Remix Alternado DJ con Sincronización de Ritmo (Beat-Matched)**:
     - Estructura las 2 canciones en bloques dinámicos estilo DJ Set (Intro/Verso A → Drop cruzado B → Puente/Coro A → Cierre B) ajustando sutilmente el tempo (`atempo` dentro de un margen musical natural `0.94x–1.06x`) y aplicando transiciones con barrido de graves y `acrossfade` triangular.
  4. **Integración Completa con Aura Music**:
     - Genera automáticamente una **portada dividida (Split Collage WebP Lossless)** con las carátulas de ambas canciones, concatena/sincroniza sus **letras `.LRC`** y registra los **capítulos reactivos (`MixtapeMetadata`)** para que `NowPlayingScreen` y `MiniPlayer` muestren la carátula y el Video Canvas de cada canción en tiempo real.

### Interfaz de Usuario y Puntos de Acceso
- **En `CreateMixtapeDialog.kt`**:
  - Añadiremos un selector superior de modo en el diálogo:
    - **Modo 1: Mixtape Continuo (Lista Completa)** (flujo actual intacto).
    - **Modo 2: Auto-Remix / Mashup de 2 Canciones (Local)**:
      - Botón **"🎲 Que el Modo Inteligente escoja las 2 canciones"** (Auto-Match con un toque) o selección manual de **Canción A** y **Canción B**.
      - Selector de estilo de Remix: **🎤 Mashup (Voz A + Base B)** vs **🎧 Remix Alternado DJ (Sincronización de Ritmo)**.
      - Botón para intercambiar rápidamente Canción A ⇄ Canción B.
- **En `LibraryScreen.kt` y `PlaylistDetailScreen.kt`**:
  - En **Tu Biblioteca** (pestañas de *Canciones* y *Playlists*) y dentro de cada **Playlist (`PlaylistDetailScreen`)**, añadiremos el acceso directo **"Auto-Remix / Mashup (2 Pistas)"** junto al creador de Mixtapes para abrir directamente la herramienta con las canciones locales disponibles.

---

## 3. Actualización de Documentación (`.md` y `chanelog-beta.md`)

- Actualizaremos (sin eliminar ningún contenido previo) los archivos:
  - `README.md`
  - `READMEAI.md`
  - `ROADMAP.md`
  - `STRUCTURE.md`
  - `AI_CONTEXT.md`
  - `AGENTS.md`
  - `chanelog-beta.md`
- En todos ellos quedará documentado en español tanto el **Smart AutoMix Estilo Apple Music (Música Local)** como el **Generador de Auto-Remix / Mashup de 2 Canciones Locales (Manual o Auto-Match)**, especificando que en esta fase operan sobre música local y preparando el camino para su futura expansión a streaming.
