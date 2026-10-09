# Plan de Optimización de Alto Impacto en Rendimiento (Aura Music)

Este plan ataca y elimina los cuellos de botella de mayor consumo de CPU, GPU e hilo principal detectados en el código de **Aura Music**, logrando que la reproducción con Video Canvas, la navegación entre pantallas, el desplazamiento por listas largas y el motor de audio C++20 funcionen a máxima fluidez (60/120 FPS estables) sin alterar el diseño ni la estética visual de la aplicación.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Decisiones confirmadas por el usuario en la Fase 1:**
> - **Prioridad de impacto máximo ("El que esté quitando mucho rendimiento")**: Se abordarán de forma integral los cuellos de botella críticos que más lastran el hilo principal y el hilo de audio: el aislamiento de `currentPositionMs` (que actualmente recompone toda la aplicación 5 veces por segundo), la eliminación de bloqueos de disco y escaneo píxel por píxel durante el scroll de carátulas, y el camino rápido (*Fast-Path*) en el motor C++20 DSP.
> - **Recorte único de franjas negras al guardar en WebP**: El recorte de bandas negras (*Deletterbox*) se ejecutará **una única vez** en segundo plano (`Dispatchers.IO`) en el momento de guardar la carátula `.webp` en `AppStorageManager.saveCoverAsWebp` (además de `MediaAssetProcessor`), eliminando por completo la transformación por píxeles en tiempo real de Coil y las llamadas síncronas a `File.exists()` en el hilo principal al hacer scroll.

---

## 1. Overview & Core Concept

- **Qué hace esta optimización**:
  1. **Aislamiento de `currentPositionMs` (Fin de las recomposiciones globales a 5 Hz)**: Actualmente, `AuraMusicAppContent` recolecta `currentPosition` en la raíz de la aplicación. Como cambia cada 200 ms mientras suena una canción, fuerza a Jetpack Compose a re-evaluar `AuraMusicAppContent`, `MiniPlayer` y `NowPlayingScreen` 5 veces por segundo. Al pasar `currentPositionFlow: StateFlow<Long>` directamente a las barras de progreso y visores de letras sincronizadas, el resto de la interfaz deja de recomponerse en cada tic del reloj.
  2. **Scroll Instantáneo de Carátulas (`ArtworkImage` + WebP Pre-Recortado)**: Se elimina `DeletterboxTransformation` en tiempo real y el chequeo bloqueante `File(artPath).exists()` en el hilo principal dentro de `ArtworkImage`. El recorte de franjas negras se realiza una sola vez al persistir el archivo WebP en `AppStorageManager`, permitiendo que Coil sirva las portadas directamente desde su caché de memoria/disco a 120 FPS.
  3. **Búsqueda Binaria $O(\log n)$ y `derivedStateOf` en Letras Karaoke**: En `NowPlayingLyricsCard` y `FullScreenLyricsScreen`, el cálculo de la frase activa (`activeIndex`) pasará de recalcularse en cada actualización de milisegundos a usar búsqueda binaria y `derivedStateOf`, disparando recomposiciones **únicamente cuando cambia el verso activo** (cada 3–6 segundos en lugar de 5 veces por segundo).
  4. **Fast-Path en el Bucle de Audio Nativo C++20 (`processPcm16`)**: Cuando ningún efecto modificador de señal está activo o cuando el usuario escucha con la pantalla apagada/minimizada, el motor C++20 aplicará un camino rápido evitando conversiones innecesarias de muestras y cálculos trigonométricos por bloque PCM.
  5. **Claves Estables (`key`) en Listas de Inicio**: Se añaden claves únicas (`key = { it.id }`) en los carruseles y listas de `HomeScreen` para que Compose recicle las tarjetas sin reconstruirlas al actualizar favoritos o contadores de reproducción.

- **Valor Clave para el Usuario**:
  - **Cero tirones (Jank-Free)** al usar el reproductor, hacer scroll rápido por la biblioteca o grabar la pantalla con Video Canvas activo.
  - **Menor calentamiento y ahorro de batería** al reducir drásticamente el trabajo innecesario de CPU/GPU en cada segundo de reproducción.

---

## 2. User Experience & Visual Design

- **Identidad Visual Intacta**:
  - Se conserva al **100%** la estética **Dark Luxury Neo-Glass OLED**, los modos de diseño (Clásico, Cinemático y Automático), las animaciones de color y todos los controles actuales. Ningún icono, color ni disposición de pantalla cambia.
- **Mejora Perceptible en la Experiencia**:
  - **Desplazamiento de Biblioteca y Cola ("Up Next")**: Las listas de canciones, álbumes y cola cargan sus miniaturas WebP de inmediato sin micro-pausas de lectura de disco.
  - **Reproductor Now Playing & MiniPlayer**: La barra de progreso ("bolita") y las letras Karaoke avanzan con total suavidad mientras el video de fondo, la carátula y los botones permanecen estáticos en la GPU sin recomponerse innecesariamente.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisión 1: Pasar `StateFlow<Long>` para la posición de reproducción en lugar de un `Long` primitivo en los contenedores padres**
  - *Enfoque elegido*: Recolectar `currentPositionFlow` exclusivamente dentro de los componentes hoja que realmente dependen del tiempo: el indicador de progreso de `MiniPlayer`, el slider de tiempo de `NowPlayingScreen` / `NowPlayingCinematicLayout`, y los visores de letras `NowPlayingLyricsCard` / `FullScreenLyricsScreen`.
  - *Por qué*: En Jetpack Compose, leer un `State<Long>` que cambia cada 200 ms en el Composable raíz invalida todo el ámbito de composición superior. Aislar la lectura en los nodos hoja reduce el costo de composición en más de un **85%** durante la reproducción.

- **Decisión 2: Recorte de bandas negras (*Deletterbox*) en el guardado WebP (`AppStorageManager`) en vez de `Coil Transformation` en cada renderizado**
  - *Enfoque elegido*: Aplicar `MediaAssetProcessor.removeHorizontalLetterboxBars(bitmap)` dentro de `AppStorageManager.saveCoverAsWebp` (en `Dispatchers.IO`) y eliminar `DeletterboxTransformation` y `File(artPath).exists()` de `ArtworkImage`.
  - *Por qué*: Recortar una sola vez al importar o descargar la carátula cuesta 0 ms durante el uso diario de la app y permite que Coil utilice su caché de bitmaps sin ejecutar `Bitmap.getPixel` ni bloqueos de sistema de archivos (`stat`) en el hilo principal.

- **Decisión 3: Optimización del Bucle PCM en C++20 (`NativeDspEngine::processPcm16`)**
  - *Enfoque elegido*: Evaluar antes del bucle `for` si existe algún procesador acústico activo (`anyDspActive`) y precalcular los pesos trigonométricos de las 28 bandas espectrales en una tabla constante (`constexpr` / precalculada) en lugar de llamar a `std::sin` 28 veces en cada buffer de audio.
  - *Por qué*: El hilo de audio de Media3/AAudio es de tiempo real estricto; ahorrar operaciones en coma flotante y ramificaciones dentro del bucle de muestras evita *underruns* y reduce el uso de CPU en segundo plano.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Diagrama de Arquitectura Reactiva Sin Recomposiciones Globales

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         MusicViewModel (StateFlows)                       │
│  • currentPosition: StateFlow<Long> (200ms)                               │
│  • visualizerBands: StateFlow<FloatArray> (30fps solo en Modo Clásico)    │
│  • audioIntensity:  StateFlow<Float>                                      │
└───────────────────┬───────────────────────────────────┬───────────────────┘
                    │ (Se pasan como Flow sin recolectar)│
                    ▼                                   ▼
┌───────────────────────────────────────┐ ┌─────────────────────────────────┐
│         AuraMusicAppContent           │ │         NowPlayingScreen        │
│  (0 recomposiciones por segundo)      │ │ (0 recomposiciones de layout/s) │
└───────────────────┬───────────────────┘ └──────┬──────────────┬───────────┘
                    │                            │              │
                    ▼                            ▼              ▼
       ┌─────────────────────────┐   ┌────────────────┐ ┌───────────────────┐
       │ MiniPlayerProgressBar   │   │ SeekBar / Time │ │ Lyrics ActiveLine │
       │ (Solo recompone 2dp bar)│   │ (Aislado)      │ │ (derivedStateOf)  │
       └─────────────────────────┘   └────────────────┘ └───────────────────┘
```

### Flujo de Guardado y Renderizado de Carátulas WebP

```
[Importación SAF / Video a Música / Descarga Web / Edición de Carátula]
                                  │
                                  ▼ (Dispatchers.IO)
        ┌───────────────────────────────────────────────────┐
        │         AppStorageManager.saveCoverAsWebp         │
        │  1. removeHorizontalLetterboxBars(bitmap)         │
        │  2. Compresión WebP Lossless -> disco (images/)   │
        └─────────────────────────┬─────────────────────────┘
                                  │
                                  ▼ (UI Thread - 0 bloqueos I/O)
        ┌───────────────────────────────────────────────────┐
        │                   ArtworkImage                    │
        │  • Sin File.exists() síncrono en Main Thread      │
        │  • Sin DeletterboxTransformation por frame        │
        │  • Caché directa de Coil en RAM y Disco           │
        └───────────────────────────────────────────────────┘
```

### Mapeo de Componentes e Interacciones a Optimizar

1. **Aislamiento de `currentPositionFlow`**:
   - `AuraMusicAppContent`: Deja de recolectar `currentPosition` en la raíz; pasa `currentPositionFlow = viewModel.currentPosition` a `MiniPlayer` y `NowPlayingScreen`.
   - `MiniPlayer`: Extrae la barra de progreso lineal inferior a un micro-componente `MiniPlayerLinearProgress` que recolecta `currentPositionFlow` localmente sin recomponer la miniatura, el video ni el texto con marquesina.
   - `NowPlayingScreen` y `NowPlayingCinematicLayout`: Aíslan la lectura de `currentPositionFlow` en la barra de progreso/tiempo y en los visores de letras (`NowPlayingLyricsCard`, `FullScreenLyricsScreen`, y la línea flotante de Karaoke cinemático).
2. **Optimización de Letras Sincronizadas**:
   - `NowPlayingLyricsCard` y `FullScreenLyricsScreen`: Utilizan búsqueda binaria sobre `lyricsState.lines` envuelta en `remember { derivedStateOf { ... } }` para que la lista `LazyColumn` solo se recomponga y haga auto-scroll cuando cambie el índice de la línea activa (`activeIndex`).
3. **Carátulas WebP y Listas**:
   - `AppStorageManager`: Aplica `MediaAssetProcessor.removeHorizontalLetterboxBars` antes de comprimir cualquier carátula a WebP.
   - `ArtworkImage`: Elimina `DeletterboxTransformation` y `File.exists()` en el hilo principal, usando `remember(artPath)` en el `ImageRequest` con fallback automático a `ProceduralArtwork` si Coil reporta error de carga.
   - `HomeScreen`: Añade `key = { it.id }` en `LazyRow` ("Recientes para ti") y en `LazyColumn` ("Populares en tu biblioteca").
4. **Motor Nativo C++20 (`auramusic_dsp.h`)**:
   - Precalcula la tabla de pesos senoidales de las 28 bandas espectrales y añade un *fast-path* limpio en `processPcm16` cuando todos los filtros modificadores están desactivados (`!anyDspActive`).
