# Aura Music — Modal Deslizable, Corrección de Reverb C++20 y Control 2.0x en Música y Voz

Esta actualización hace que el modal de descarga de YouTube y TikTok sea completamente deslizable verticalmente para que ningún botón quede cortado en pantallas compactas, corrige de raíz la pérdida de audio al activar el Reverb procesándolo íntegramente en el motor nativo C++20 sin interferencia del hardware LVREV de Android, y amplía tanto la velocidad de reproducción como el tono/velocidad de voz hasta **2.0x** con botones rápidos dedicados.

## User Review & Critical Decisions

> [!IMPORTANT]
> Se han incorporado tus decisiones confirmadas para garantizar que la experiencia sea cómoda y directa en teléfono móvil:

- **Decisión Confirmada 1 (Desplazamiento del Modal de Descarga)**: Todo el contenido del modal de descarga (`TikTok` y `YouTube / Web`) será completamente deslizable de arriba a abajo mediante scroll vertical fluido con altura máxima adaptativa, garantizando acceso total a los interruptores y a los botones **Cambiar** y **Descargar y Reproducir**.
- **Decisión Confirmada 2 (Velocidad y Tono/Voz hasta 2.0x)**: Tanto **Velocidad de Reproducción** como **Tono / Velocidad de Voz (Pitch Shift)** tendrán deslizadores de `0.50x` a `2.00x` y una fila de botones rápidos (`0.8x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`) en ambos controles.
- **Decisión Técnica (Solución definitiva al corte de sonido en Reverb)**: Se desactiva la vinculación de `PresetReverb` y `EnvironmentalReverb` de Android (`android.media.audiofx`) sobre la sesión de audio porque en el driver nativo LVREV de Android silencian la señal directa (*dry*) y retienen el buffer durante el tiempo de decaimiento (*decayTime*) al apagarse. El Reverb funcionará al 100% sobre el procesador estereofónico **C++20 (`ReverbProcessor`)** en tiempo real, con buffers estáticos preasignados libres de bloqueos y preservación de estado al cambiar frecuencia de muestreo.

---

## 1. Overview & Core Concept

- **Qué Hace**:
  1. **Modal de Descarga Deslizable**: Permite desplazarse libremente por todas las opciones del diálogo de descarga (selector de fuente, motor de extracción de YouTube, enlace, vista previa, edición de título/artista, interruptores de Video Canvas y recorte de silencios, y botones de acción) sin cortes visuales.
  2. **Reverb Acústico C++20 sin Pérdida de Audio**: Mantiene la música sonando de forma ininterrumpida al activar o desactivar el Reverb y al cambiar entre *Estudio*, *Sala Mediana*, *Club en Vivo*, *Gran Hall*, *Catedral* o *Eco Espacial*.
  3. **Modulación 2.0x para Música y Voz**: Permite acelerar tanto el tempo musical como el tono vocal hasta el doble (`2.00x`), con chips interactivos de un toque.
- **Valor Clave**: Elimina bloqueos de interfaz en pantallas verticales al descargar de YouTube y garantiza un procesamiento acústico instantáneo y sin silencios indeseados.

---

## 2. User Experience & Visual Design

- **Flujos de Usuario Clave**:
  1. **Descarga desde YouTube / TikTok**: El usuario pega un enlace de YouTube, pulsa *Inspeccionar Video* y desliza suavemente hacia abajo dentro de la tarjeta modal para editar metadatos, activar/desactivar *Video Canvas* o *Eliminar silencios*, y pulsar *Descargar y Reproducir*.
  2. **Activación en Caliente del Reverb**: Desde la hoja modal de efectos (*Reverb*), el usuario activa el interruptor o elige cualquier preset ambiental escuchando de inmediato la mezcla de la canción original (*dry*) con la reverberación espacial (*wet*) sin cortes ni esperas al apagarlo.
  3. **Ajuste Rápido a 2.0x**: En la pestaña *Velocidad* de la hoja de efectos, el usuario puede deslizar hasta `2.00x` o tocar directamente el botón rápido `2.0x` tanto en *Velocidad de Reproducción* como en *Tono / Velocidad de Voz*.
- **Identidad Visual y Maquetación**:
  - **Estética**: Oscura de alto contraste (*Luxury Dark*), conservando intactos los colores, bordes translúcidos y degradados de Aura Music.
  - **Ergonomía Táctil**: Todos los chips rápidos (`0.8x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`) y botones de acción respetan áreas táctiles cómodas y legibilidad con escala tipográfica controlada.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisión 1: Procesamiento Reverb 100% en C++20 NDK en lugar de `android.media.audiofx` Reverb**
  - *Enfoque Elegido*: Ejecutar la red acústica *Freeverb* (8 filtros peine amortiguados + 4 filtros pasa-todo Schroeder en doble precisión) exclusivamente en nuestro motor C++20 integrado en el pipeline PCM de ExoPlayer.
  - *Por Qué*: Las clases `EnvironmentalReverb` y `PresetReverb` del sistema Android están diseñadas como efectos auxiliares de hardware con nivel húmedo inicial en `-9000 mB` (silencio total) cuando se insertan directamente sobre un `audioSessionId`, y al desactivarse mantienen ocupado el canal durante varios segundos mientras vacían su cola de decaimiento. Nuestro motor C++20 mezcla en tiempo real la señal original (`dryGain` ≥ 65%) con la señal reverberada sin latencia ni silencios.
- **Decisión 2: Preservación de Parámetros DSP en Reconfiguración de Pista**
  - *Enfoque Elegido*: Conservar las ganancias de ecualización, Bass Boost y parámetros de Reverb cuando `NativeAudioProcessor` notifica la frecuencia de muestreo de una nueva pista a C++20, y asegurar que funcione tanto en pistas estéreo como mono.
  - *Por Qué*: Evita que al cambiar de canción o reiniciar el flujo PCM se pierdan o reinicien abruptamente los efectos activos.

---

## 4. Technical Architecture & Data Strategy

### Arquitectura y Flujo de Componentes

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        INTERFAZ JETPACK COMPOSE                         │
├───────────────────────────┬──────────────────────────┬──────────────────┤
│   Modal Descarga Enlace   │   Pestaña Velocidad/Voz  │  Pestaña Reverb  │
│  (Scroll Vertical Total)  │  (Sliders + Chips 2.0x)  │ (Switch/Presets) │
└─────────────┬─────────────┴────────────┬─────────────┴────────┬─────────┘
              │                          │                      │
              ▼                          ▼                      ▼
┌───────────────────────────┐ ┌──────────────────────┐ ┌──────────────────┐
│  OnlineVideoAudioImporter │ │   AuraAudioPlayer    │ │AudioEffectManager│
│ (YouTube / TikTok / Web)  │ │ (Speed & Pitch 2.0x) │ │ (Sin HW LVREV)   │
└───────────────────────────┘ └──────────┬───────────┘ └────────┬─────────┘
                                         │                      │
                                         ▼                      ▼
                              ┌───────────────────────────────────────────┐
                              │     MOTOR NATIVO C++20 (auramusic_dsp)    │
                              │  • Red Reverb Freeverb (Dry + Wet Mix)    │
                              │  • Soporte Mono/Estéreo sin silencios     │
                              │  • EQ 10 Bandas + 8D + Crossfeed          │
                              └───────────────────────────────────────────┘
```

### Mapeo de Estado e Interacciones

1. **Contenedor Deslizable del Modal de Descarga**:
   - El contenedor `Column` principal del `Card` dentro del `Dialog` incorpora `rememberScrollState()` y `Modifier.verticalScroll(...)` junto con `heightIn(max = ...)` para adaptarse a cualquier pantalla móvil sin recortar el bloque inferior de botones.
2. **Motor Reverb C++20 (`ReverbProcessor` y `AudioEffectManager`)**:
   - Se elimina la instanciación y activación de `PresetReverb` y `EnvironmentalReverb` de hardware en `AudioEffectManager` para impedir que AudioFlinger silencie la pista.
   - En el motor C++20 (`auramusic_dsp.h`), `ReverbProcessor` procesa tanto pistas estéreo como mono, mantiene buffers preasignados seguros durante el cambio de parámetros en caliente y conserva la configuración activa cuando `init(sampleRate, channels)` ajusta la frecuencia de muestreo.
3. **Controles de Velocidad y Tono/Voz hasta 2.0x**:
   - `PlaybackParametersTabContent` amplía el rango del deslizador de Tono/Voz a `0.5f..2.0f` (`steps = 29`, incrementos de `0.05x`) e incorpora la fila de botones rápidos `0.8x`, `1.0x`, `1.25x`, `1.5x` y `2.0x` tanto en Velocidad de Reproducción como en Tono/Velocidad de Voz.
