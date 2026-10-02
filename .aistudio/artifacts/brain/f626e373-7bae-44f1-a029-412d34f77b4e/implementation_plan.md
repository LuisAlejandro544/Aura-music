# Temporizador, Crossfade/Gapless, Pitch/Speed y Audio 8D en C++20 🎧⏱️

Implementación integral de cuatro capacidades avanzadas de reproducción y audio en **Aura Music**: un Temporizador de Apagado personalizable con cuenta regresiva y desvanecimiento suave de 10 segundos, Transición Suave entre Canciones (Crossfade configurable) con Reproducción Gapless, Control en tiempo real de Velocidad (0.5x - 2.0x) y Tono (Pitch), y un motor nativo de **Audio 8D Espacial Binaural en C++20** configurable sin modelos 3D invasivos, accesible desde un Bottom Sheet en la pantalla Now Playing.

---

## Decisiones Críticas y Preferencias Confirmadas

> [!IMPORTANT]
> Se han incorporado las preferencias confirmadas por el usuario durante la fase de clarificación:

- **Punto de Acceso UI**: Acceso directo mediante una hoja inferior deslizable (*Modal BottomSheet*) integrada en la pantalla **Now Playing**, manteniendo la interfaz limpia y accesible durante la escucha.
- **Control de Audio 8D**: Efecto espacial binaural que se activa/desactiva con interruptor, sin gráficos 3D sobrecargados, con deslizadores intuitivos para velocidad de órbita, intensidad binaural y profundidad acústica de sala.
- **Comportamiento del Temporizador**: Cuenta regresiva en vivo con atenuación progresiva de volumen (*fade-out* suave de 10 segundos) antes de pausar la reproducción para una transición natural al dormir.

---

## 1. Visión General del Concepto

Aura Music evoluciona su motor de audio para proporcionar herramientas de control de escucha profesional y experiencias inmersivas:
1. **Temporizador de Apagado Personalizable**: Permite introducir cualquier número de minutos deseado (además de presets rápidos de 15, 30, 45, 60 min), mostrando el tiempo restante en vivo y aplicando un *fade-out* lineal de 10 segundos para no interrumpir abruptamente el sueño del usuario.
2. **Crossfade y Reproducción Gapless**: Eliminación de silencios entre pistas consecutivas (Gapless) y fundido cruzado ajustable de 0 a 12 segundos para enlazar el final y principio de canciones como en una sesión de DJ.
3. **Control de Velocidad y Tono (Pitch & Speed)**: Ajuste independiente del tempo (0.5x a 2.0x) y la frecuencia tonal del audio mediante los parámetros nativos de Media3, con botones rápidos de restablecimiento (1.0x).
4. **Motor Nativo de Audio 8D en C++20**: Algoritmo de rotación espacial binaural desarrollado en C++20 con cálculo trigonométrico orbital en tiempo real, modulación de retardo interaural (ITD), filtrado espectral de atenuación posterior y reverberación acústica ambiental en coma flotante de 64 bits.

---

## 2. Experiencia de Usuario y Diseño Visual

### Flujos Principales de Usuario
- **Apertura de la Hoja de Efectos**: El usuario toca el nuevo icono de "Efectos & Temporizador" (mezclador / temporizador) en la barra superior de Now Playing.
- **Configuración del Temporizador**:
  - Toca un chip rápido (15m, 30m, 45m, 60m) o pulsa "Personalizado" para escribir exactamente los minutos deseados (por ejemplo, 23 o 90 minutos).
  - Al iniciar, se muestra un indicador visual animado con la cuenta regresiva en formato `mm:ss` y un botón para cancelar o añadir 5 minutos más.
  - Al llegar a los últimos 10 segundos, el volumen disminuye fluidamente de 1.0 a 0.0 y se pausa la reproducción.
- **Ajuste de Velocidad y Tono**:
  - Deslizador de velocidad de reproducción (0.50x a 2.00x) con incrementos de 0.05x y presets (0.8x, 1.0x, 1.25x, 1.5x).
  - Deslizador de afinación/tono musical con opción de bloqueo a tono estándar o modificación creativa.
- **Experiencia de Audio 8D**:
  - Interruptor maestro de activación 8D.
  - Slider de **Velocidad de Órbita**: Ajusta los segundos por vuelta completa (de 4 a 30 segundos por rotación alrededor de la cabeza).
  - Slider de **Ancho e Intensidad Espacial**: Regula la amplitud del efecto binaural (0% a 100%).
  - Slider de **Profundidad de Sala**: Aplica una leve cola de reflexión espacial para sensación de escucha en sala abierta con audífonos.

### Estilo Visual y Paleta M3 Dark Luxury
- Hoja inferior opaca 100% OLED en superficie `Color(0xFF0F0F16)` con esquinas redondeadas (28.dp).
- Acentos brillantes correspondientes al tema activo (Nebula Violet, Cyber Mint, Sunset Ember u Ocean Abyss).
- Sliders M3 con retroalimentación visual del valor actual y botones táctiles con un área mínima de 48.dp.

---

## 3. Decisiones Clave de Producto y Arquitectura

### Decisión 1: Implementación de Audio 8D en C++20 Nativo
- **Enfoque**: Extender la clase `NativeDspEngine` en `auramusic_dsp.h` y `auramusic_dsp.cpp` agregando un módulo `EightDProcessor`.
- **Por qué**: El cálculo de fase angular, retardo de muestras (fracción de milisegundos para ITD - Interaural Time Difference) y paneo de potencia constante $L = \cos(\theta), R = \sin(\theta)$ se ejecuta de forma óptima a 44.1/48 kHz en C++ con `std::span` sin provocar pausas de recolección de basura (*Garbage Collector*) en Android.
- **Alternativas descartadas**: No hacerlo en Java/Kotlin porque procesar 176,400 bytes por segundo en la JVM genera latencia y consumo excesivo de batería.

### Decisión 2: Control de Velocidad y Tono mediante Media3 PlaybackParameters
- **Enfoque**: Utilizar la API oficial de `androidx.media3.common.PlaybackParameters(speed, pitch)` integrada en `ExoPlayer`.
- **Por qué**: Media3 incluye algoritmos Sonic optimizados por hardware para estiramiento temporal de audio de alta calidad sin artefactos robóticos.

### Decisión 3: Manejo del Temporizador con Corrutinas y Fade-Out Gradual
- **Enfoque**: Un `Job` dedicado en `MusicViewModel` que actualiza el estado reactivo `timerRemainingSeconds: StateFlow<Long?>`. En los últimos 10 segundos, interpola linealmente el volumen de `AuraAudioPlayer` cada 250ms hasta cero antes de ejecutar `pause()`.

---

## 4. Arquitectura Técnica y Estrategia de Datos

### Diagrama del Sistema

```
┌────────────────────────────────────────────────────────────────────────┐
│                   NowPlayingScreen / UI (Compose M3)                   │
│  ┌───────────────────────┐  ┌───────────────────────────────────────┐  │
│  │ NowPlayingTopBar      │  │ AudioEffectsBottomSheet               │  │
│  │ [Icono Efectos/Timer] ├─►│ ├─ Sleep Timer (Personalizado/Fade10s)│  │
│  └───────────────────────┘  │ ├─ Crossfade & Gapless Controls       │  │
│                             │ ├─ Speed & Pitch Sliders              │  │
│                             │ └─ 8D Audio Switch & Orbit Controls   │  │
│                             └──────────────────┬────────────────────┘  │
└────────────────────────────────────────────────┼───────────────────────┘
                                                 │
                                                 ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   MusicViewModel (Gestión de Estado)                   │
│  - sleepTimerJob: Job?               - timerSecondsRemaining: Flow     │
│  - crossfadeDurationSeconds: Flow    - isGaplessEnabled: Flow          │
│  - playbackSpeed: Flow               - playbackPitch: Flow             │
│  - is8DEnabled: Flow                 - orbitSpeedSeconds: Flow         │
│  - spatialIntensity: Flow            - roomDepth: Flow                 │
└───────────────────────┬───────────────────────────────┬────────────────┘
                        │                               │
                        ▼                               ▼
       ┌────────────────────────────────┐  ┌─────────────────────────────┐
       │       AuraAudioPlayer          │  │     NativeAudioEngine       │
       │  (Media3 ExoPlayer Wrapper)    │  │        (Puente JNI)         │
       │  - setPlaybackParameters()     │  └──────────────┬──────────────┘
       │  - setVolumeWithFade()         │                 │ JNI Call
       │  - crossfadeTransitionHandler  │                 ▼
       └────────────────┬───────────────┘  ┌─────────────────────────────┐
                        │ PCM Audio Sink   │    auramusic_dsp.cpp/h      │
                        │                  │       (Motor C++20)         │
                        └─────────────────►│  - Biquad 10-Band Filters   │
                                           │  - Soft Limiter             │
                                           │  - 8D Binaural Orbit Engine │
                                           └─────────────────────────────┘
```

### Entidades y Modelos
- `AudioPlaybackConfig`: Estado que agrupa `speed: Float`, `pitch: Float`, `crossfadeSeconds: Int`, `gapless: Boolean`.
- `SpatialAudio8DConfig`: Estado que agrupa `enabled: Boolean`, `orbitSpeedSeconds: Float`, `spatialIntensity: Float`, `roomDepth: Float`.
- `SleepTimerState`: Estado reactivo con `isActive: Boolean`, `totalSeconds: Int`, `remainingSeconds: Int`, `inFadeOut: Boolean`.

### Mapeo de Componentes Interactivos y Manejadores
1. **Sleep Timer**:
   - `startSleepTimer(minutes: Int)`: Cancela temporizador previo si existe, inicia cuenta atrás de 1s con `delay(1000)`. Al restar ≤ 10s, modula `player.setVolume()`. Al llegar a 0s, invoca `pause()`, restaura volumen y finaliza.
   - `cancelSleepTimer()`: Detiene la corrutina y restaura el volumen a 1.0f inmediatamente.
2. **Speed & Pitch**:
   - `setPlaybackSpeed(speed: Float)` y `setPlaybackPitch(pitch: Float)`: Actualizan `exoPlayer.playbackParameters`.
3. **8D Audio en C++20**:
   - Funciones JNI añadidas: `setEightDEnabled(boolean)`, `setEightDOrbitSpeed(float)`, `setEightDSpatialIntensity(float)`, `setEightDRoomDepth(float)`.
   - `EightDProcessor` en C++ calcula para cada muestra estéreo la posición orbital $\theta += \Delta\theta$, atenuación por potencia constante, y mezcla de reflexión con retardo circular.
