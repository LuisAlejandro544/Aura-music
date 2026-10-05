# Limpieza de Buffer (Buffer Flushing) en C++20 y Modularización Arquitectónica Urgente 🧹⚡

Este plan aborda la implementación de la limpieza de buffer (*Buffer Flushing*) de alta fidelidad en el motor nativo C++20 y en el reproductor de audio, así como la modularización y partición urgente de los archivos gigantes del proyecto (liderados por `MusicViewModel.kt` con 1,173 líneas y `auramusic_dsp.h` con 808 líneas), actualizando la documentación de `STRUCTURE.md`.

---

## Decisiones Críticas y Preferencias Confirmadas

> [!IMPORTANT]
> **Decisiones confirmadas por el usuario:**
> - **Momentos de ejecución de la limpieza de buffer:** Se ejecutará automáticamente en **cambios de pista** (`playTrack`), **saltos de tiempo** (`seekTo`) y **paradas/pausas** (`pause`/`release`).
> - **Prioridad de modularización urgente:** Atacar directamente los archivos que han crecido demasiado (superando las 500 líneas recomendadas), comenzando por los más grandes: `MusicViewModel.kt` (1,173 líneas) y el núcleo C++20 `auramusic_dsp.h` (808 líneas).
> - **Sincronización de documentación:** Reflejar la nueva distribución modular de archivos en `STRUCTURE.md`.

---

## 1. Visión General y Concepto Central

### A. Limpieza de Buffer (Buffer Flushing)
La limpieza de buffer purga a cero los acumuladores de los filtros IIR Biquad (10 bandas del ecualizador), las líneas de retardo del filtro Crossfeed de audífonos y las líneas de eco de la suite Reverb en C++20. Esto erradica definitivamente:
1. Cualquier "pop" o chasquido digital residual en auriculares al pausar o cambiar de pista.
2. Cualquier eco residual ("cola de reverb") de la canción previa mezclándose en los primeros milisegundos de la nueva canción.
3. El desfase sonoro transitorio al arrastrar la barra de progreso (*SeekTo*).

### B. Modularización Urgente de Archivos Gigantes
1. **`MusicViewModel.kt` (1,173 líneas ➔ submódulos delegados <400 líneas):**
   - Extraer la gestión de letras y búsqueda comunitaria a `LyricsCoordinator.kt`.
   - Extraer la gestión de intents multimedia externos ('Abrir con...') a `IncomingMediaCoordinator.kt`.
   - Extraer la lógica de configuración y preferencias de audífonos a `HeadphoneSettingsCoordinator.kt`.
   - `MusicViewModel` mantendrá el 100% de sus métodos y propiedades públicas idénticas, delegando internamente en estos componentes sin romper ninguna pantalla ni llamada existente.
2. **`auramusic_dsp.h` (808 líneas ➔ módulos C++20 específicos <200 líneas):**
   - `dsp_filters.h`: Filtro Biquad, ecualizador paramétrico y Bass Boost.
   - `dsp_spatial.h`: Motor de Audio 8D orbital.
   - `dsp_reverb.h`: Procesador de Reverb acústico con filtros peine y pasa-todo.
   - `dsp_crossfeed.h`: Filtro Crossfeed Bauer/Chu Moy.
   - `auramusic_dsp.h`: Orquestador principal `NativeDspEngine` con método de vaciado atómico `flushDspBuffers()`.

---

## 2. Experiencia de Usuario y Rendimiento Acústico

- **Transición Silenciosa Absoluta:** Al pulsar "Siguiente", al cambiar de pista en la cola o al adelantar una canción con el seekbar, el audio transiciona con pureza cristalina sin chasquidos ni interferencias acústicas de la pista anterior.
- **Rendimiento Instantáneo:** El reseteo de variables de buffer en C++20 se ejecuta en nanosegundos (apenas una docena de asignaciones en memoria contigua en la CPU).
- **Mantenibilidad y Robustez:** Reducir archivos gigantes evita colapsos de compilación, fallos de recomposición en Compose y facilita la incorporación de nuevas funciones en la Fase 7.

---

## 3. Decisiones Técnicas y de Arquitectura

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Arquitectura de Buffer Flush                    │
│                                                                        │
│   AuraAudioPlayer (playTrack / seekTo / pause / trackEnded)            │
│                       │                                                │
│                       ▼                                                │
│            AudioEffectManager.flushBuffers()                           │
│                       │                                                │
│                       ▼                                                │
│            NativeAudioEngine.flushBuffers()                            │
│                       │  (JNI / C++20)                                 │
│                       ▼                                                │
│            sDspEngine.flushDspBuffers()                                │
│       ┌───────────────┼───────────────┬───────────────┐                │
│       ▼               ▼               ▼               ▼                │
│   Equalizer        Reverb         Crossfeed       Spatial8D            │
│  (biquads=0)    (delayLines=0)   (delayLine=0)   (phase/filter=0)      │
└────────────────────────────────────────────────────────────────────────┘
```

### Plan de Cambios por Archivo:

1. **C++20 (Módulos DSP y Flush):**
   - Crear `app/src/main/cpp/dsp_filters.h` (Biquad, Equalizer, BassBoost, Limiter con `reset()`).
   - Crear `app/src/main/cpp/dsp_reverb.h` (CombFilter, AllPassFilter, ReverbProcessor con `reset()`).
   - Crear `app/src/main/cpp/dsp_crossfeed.h` (CrossfeedFilter con `reset()`).
   - Crear `app/src/main/cpp/dsp_spatial.h` (Spatial8DEngine con `reset()`).
   - Actualizar `auramusic_dsp.h` para incluir estos módulos y exponer `flushDspBuffers()`.
   - En `auramusic_dsp.cpp`, exponer `Java_com_example_playback_NativeAudioEngine_nativeFlushDspBuffers`.

2. **Capa Kotlin de Audio:**
   - En `NativeAudioEngine.kt`: exponer `nativeFlushDspBuffers()` y `flushBuffers()`.
   - En `AudioEffectManager.kt`: método `flushBuffers()`.
   - En `AuraAudioPlayer.kt`: invocar `effectManager.flushBuffers()` en `playTrack()`, `seekTo()`, `handleTrackEnded()`, `pause()` y `release()`.
   - En `NativeAudioProcessor.kt`: invocar limpieza en `onFlush()`.

3. **Modularización de `MusicViewModel.kt`:**
   - Crear `com.example.viewmodel.delegates.LyricsCoordinator.kt`.
   - Crear `com.example.viewmodel.delegates.IncomingMediaCoordinator.kt`.
   - Crear `com.example.viewmodel.delegates.HeadphoneSettingsCoordinator.kt`.
   - Reducir `MusicViewModel.kt` delegando responsabilidades en estos coordinadores.

4. **Actualización de Documentación:**
   - Actualizar `STRUCTURE.md` con los nuevos archivos de `cpp/` y `viewmodel/delegates/`.
   - Actualizar `README.md`, `ROADMAP.md`, `AI_CONTEXT.md` y `AGENTS.md`.
