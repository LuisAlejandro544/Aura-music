# Reparación Integral de Auto-Remix & Smart AutoMix DJ + APK Debug en 3 Arquitecturas

Este plan soluciona de raíz el bloqueo que impide iniciar y procesar mezclas en el **Generador de Auto-Remix & Mashup de 2 Canciones (FFmpeg)** y en el **Smart AutoMix DJ en vivo (Dual-Deck)**, además de actualizar el pipeline de GitHub Actions del **APK Debug** para compilar y entregar en Artifacts los **3 APKs móviles separados (`arm64-v8a`, `armeabi-v7a` y `universal`) junto con su archivo `SHA256SUMS.txt`**, idéntico al esquema de la edición Beta.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Decisiones confirmadas por el usuario en la fase de clarificación:**

- **Alcance de la reparación de mezcla automática**: Se repararán **ambos motores**:
  1. **Generador de Auto-Remix & Mashup de 2 Canciones Locales (`FFmpeg`)**: Disponible desde *Biblioteca* y *Detalle de Playlist*.
  2. **Smart AutoMix DJ en vivo (`Dual-Deck Crossfade + Curva Armónica en X`)**: Disponible en la pestaña *Transiciones* de la hoja de efectos de audio.
- **Formato de entrega en Artifacts para el APK Debug**: El workflow de GitHub Actions generará y publicará los **3 APKs móviles separados** (`arm64-v8a`, `armeabi-v7a` y `universal`) más el archivo de verificación criptográfica **`SHA256SUMS.txt`**.

---

### 1. Overview & Core Concept

- **Qué resuelve**:
  1. **Por qué el Generador Auto-Remix no iniciaba ni producía el remix**:
     - `SmartAutoRemixEngine.prepareSafeLocalAudioFile` utilizaba `IncomingMediaHandler.isSafeExternalUri` sobre rutas internas de `songs/` o URIs `file://` que apuntan al almacenamiento propio de la app, rechazándolas por la regla anti-*File Stealing* diseñada para Intents externos (a diferencia de `MixtapeEngine`, que verifica explícitamente si el archivo pertenece a `storageManager.songsDir`).
     - En `SmartAutoRemixEngine.createAutoRemix` no se invocaba `FFmpegNativeEngine.init(context)` antes de comprobar disponibilidad, y el grafo `filter_complex` de FFmpeg encadenaba filtros `equalizer=...` (cuya sintaxis depende de compilaciones específicas de `libavfilter`), provocando la salida inmediata de FFmpeg con código de error si el filtro o un parámetro `atrim` en pistas cortas no coincidía.
     - En `CreateMixtapeDialog`, cuando fallaba la creación, no se mostraba el mensaje de error ni un aviso visual claro, dando la sensación de que el botón "Crear Auto-Remix" no hacía nada.
  2. **Por qué el Smart AutoMix DJ en vivo no se disparaba consistentemente**:
     - El estado de `isDjAutomixEnabled`, `isDjEqCurveEnabled` y `crossfadeSeconds` no se persistía en preferencias, reiniciándose a `false` / `0s` al cerrar la app.
     - En `AuraAudioPlayer`, al hacer el *handoff* de la canción saliente al segundo reproductor (`outgoingExoPlayer`), si la canción original tenía recorte de silencios (`ClippingConfiguration`), `outDeck.setMediaItem(outgoingItem, outgoingPos)` re-aplicaba el offset sobre un ítem ya recortado o quedaba en `STATE_BUFFERING` más allá del margen de espera, cancelando la superposición Dual-Deck.
  3. **Generación de 3 APKs en el Workflow Debug**:
     - Actualmente `app/build.gradle.kts` solo habilita el bloque `splits.abi` cuando detecta una tarea `Beta`, por lo que `assembleDebug` producía un único APK monolítico (`app-debug.apk`).
- **Valor para el usuario**: Podrá crear Mashups Vocales y Remixes Alternados DJ al instante con cualquier par de canciones de su biblioteca (con fallback resiliente de filtros FFmpeg y diagnóstico claro en pantalla), disfrutar de transiciones Dual-Deck persistentes en vivo y descargar desde GitHub Actions el APK Debug exacto para su arquitectura móvil (`arm64-v8a`, `armeabi-v7a` o `universal`) junto con sus firmas `SHA256SUMS.txt`.

---

### 2. User Experience & Visual Design

- **Flujo del usuario en Auto-Remix & Mashup (2 Canciones)**:
  1. El usuario abre *Tu Biblioteca* o cualquier *Playlist* con al menos 2 canciones locales y pulsa **Auto-Remix (2)**.
  2. En el modal `CreateMixtapeDialog`, elige entre **Mashup (Voz A + Base B)** o **Remix Alternado DJ**, usa **Elección Inteligente (Auto-Match)** o selecciona manualmente las pistas A y B, y pulsa **Crear Auto-Remix**.
  3. El indicador circular y la barra de progreso avanzan en vivo mostrando cada etapa real (`Preparando pistas locales`, `Renderizando mezcla en estudio`, `Generando carátula dividida WebP`, `Sincronizando letras .LRC`).
  4. Si alguna pista presenta un formato incompatible, se muestra una tarjeta de aviso visible dentro del diálogo con el motivo exacto y botón de reintento, sin cierres silenciosos.
  5. Al finalizar al 100%, aparece la tarjeta de confirmación con el botón **Reproducir Ahora**.
- **Flujo del usuario en Smart AutoMix DJ en Vivo**:
  1. Desde *Now Playing* -> *EQ FX* -> pestaña *Transiciones*, el usuario activa **Smart AutoMix DJ** (y/o ajusta el deslizador de **Superposición Dual-Deck**).
  2. La configuración queda guardada permanentemente en preferencias.
  3. Al cambiar de pista o llegar al tramo final (outro acústico), el reproductor saliente baja progresivamente de 100% a 0% mientras la pista entrante sube de 0% a 100% al unísono con la curva armónica en X activa.
- **Identidad Visual**:
  - Se conserva intacto el diseño oscuro de estudio de Aura Music (`SurfaceCard`, bordes `CardBorder`, acentos dinámicos y tipografía con `fontScale = 1.0f`), añadiendo únicamente el banner de estado/error en `CreateMixtapeDialog` cuando `statusMessage` reporta una incidencia.

---

### 3. Key Product Decisions & Trade-Offs

- **Decisión 1: Validación de Archivos Locales y Grafo FFmpeg Resiliente de Doble Nivel en Auto-Remix**
  - *Enfoque elegido*: Unificar la validación de rutas de `SmartAutoRemixEngine` con la de `MixtapeEngine` (permitiendo explícitamente archivos en `storageManager.songsDir`, rutas locales legibles y URIs `content://` copiadas a caché temporal) e implementar un grafo `filter_complex` principal con filtros universales (`highpass`, `lowpass`, ` treble`/`bass`/`anequalizer` o `aformat` + `atempo` + `amix`/`acrossfade`) más un **fallback automático instantáneo** si algún filtro paramétrico específico falla en un códec particular.
  - *Por qué*: Garantiza que el Auto-Remix inicie y culmine con éxito en el 100% de las canciones importadas, descargadas o generadas en la biblioteca local.
- **Decisión 2: Handoff Dual-Deck Limpio y Persistencia de Smart AutoMix DJ**
  - *Enfoque elegido*: Guardar `isDjAutomixEnabled`, `isDjEqCurveEnabled`, `crossfadeSeconds` e `isGaplessEnabled` en `SharedPreferences`, y en `AuraAudioPlayer` construir el `MediaItem` del `outgoingExoPlayer` preservando la URI resuelta y posición exacta sin conflictos de estado, aumentando el margen de sincronización inicial del Deck entrante.
  - *Por qué*: Asegura que el cruce Dual-Deck funcione tanto al llegar al final de la pista como al cambiar manualmente de canción, y que la preferencia del usuario nunca se pierda al reiniciar la app.
- **Decisión 3: Activación de Splits ABI Móviles (`arm64-v8a`, `armeabi-v7a`, `universal`) en Compilaciones Debug de CI**
  - *Enfoque elegido*: Permitir que `app/build.gradle.kts` active el bloque `splits.abi` (`arm64-v8a`, `armeabi-v7a` y `isUniversalApk = true`) y `mobileOnlyAbis` tanto en tareas `Beta` como cuando el workflow de GitHub Actions Debug define `AURA_SPLIT_APKS=true` (o `AURA_MOBILE_ONLY_ABIS=true`), manteniendo intacta la compilación rápida en el entorno local del emulador cuando la variable no está presente.
  - *Por qué*: Entrega exactamente los 3 APKs móviles (`AuraDebug-...-arm64-v8a.apk`, `AuraDebug-...-armeabi-v7a.apk`, `AuraDebug-...-universal.apk`) y `SHA256SUMS.txt` en los Artifacts de GitHub Actions, reduciendo además el tiempo de compilación y el peso de cada artefacto.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   AURA MUSIC - FLUJO AUTO-REMIX & AUTOMIX                  │
└────────────────────────────────────────────────────────────────────────────┘
                                      │
        ┌─────────────────────────────┴─────────────────────────────┐
        ▼                                                           ▼
┌───────────────────────────────┐                   ┌───────────────────────────────┐
│   CreateMixtapeDialog (UI)    │                   │  TransitionsTabContent (UI)   │
│  • Selección Manual / Match   │                   │  • Switch Smart AutoMix DJ    │
│  • Banner de Progreso / Error │                   │  • Slider Crossfade Dual-Deck │
└───────────────┬───────────────┘                   └───────────────┬───────────────┘
                │                                                   │
                ▼                                                   ▼
┌───────────────────────────────┐                   ┌───────────────────────────────┐
│      MixtapeCoordinator       │                   │    AudioEffectsCoordinator    │
│  • Estado reactivo de avance  │                   │  • Persistencia SharedPrefs   │
└───────────────┬───────────────┘                   └───────────────┬───────────────┘
                │                                                   │
                ▼                                                   ▼
┌───────────────────────────────┐                   ┌───────────────────────────────┐
│     SmartAutoRemixEngine      │                   │       AuraAudioPlayer &       │
│  1. FFmpegNativeEngine.init() │                   │      AudioFadeController      │
│  2. Lectura segura songsDir   │                   │  • Handoff limpio Deck A -> B │
│  3. Grafo Mashup / DJ Remix   │                   │  • Rampa simultánea 100% ⇄ 0% │
│     + Fallback Resiliente     │                   │  • Curva EQ en X (C++20 DSP)  │
│  4. Portada Split WebP + LRC  │                   └───────────────────────────────┘
└───────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────┐
│             PIPELINE GITHUB ACTIONS: COMPILAR APK DEBUG (3 ABIs)           │
├────────────────────────────────────────────────────────────────────────────┤
│  provisionNativeDeps (AURA_MOBILE_ONLY_ABIS=true)                          │
│         │                                                                  │
│         ▼                                                                  │
│  assembleDebug (AURA_SPLIT_APKS=true -> splits.abi habilitado)             │
│         │                                                                  │
│         ├──► AuraMusic-Debug-arm64-v8a.apk     (Artifact 1)                │
│         ├──► AuraMusic-Debug-armeabi-v7a.apk   (Artifact 2)                │
│         ├──► AuraMusic-Debug-universal.apk     (Artifact 3)                │
│         └──► AuraMusic-Debug-SHA256SUMS.txt    (Artifact 4)                │
└────────────────────────────────────────────────────────────────────────────┘
```

- **Mapeo de Componentes y Estado**:
  - **Motor Auto-Remix (`SmartAutoRemixEngine`)**:
    - Inicializa `FFmpegNativeEngine.init(context)` al arrancar `createAutoRemix`.
    - Valida archivos en `storageManager.songsDir` y rutas locales sin bloquearlos con la regla de Intents externos; si la pista proviene de `content://` o requiere extracción limpia, la vuelca a un archivo temporal seguro en `cacheDir`.
    - Obtiene la duración real del archivo de audio con `MediaMetadataRetriever` cuando `track.durationMs` sea `0L` o impreciso, evitando recortes `atrim` inválidos.
    - Ejecuta el grafo principal de `VOCAL_MASHUP` o `DJ_ALTERNATING_REMIX` usando rutas sanitizadas (`FFmpegNativeEngine.sanitizeFilePath`) y, si FFmpeg reporta error en algún filtro paramétrico, reintenta automáticamente con el grafo compatible estándar (`highpass`/`lowpass` + `atempo` + `amix`/`acrossfade`) registrando el log completo en `AuraDebugManager`.
  - **Interfaz y Coordinador (`CreateMixtapeDialog` y `MixtapeCoordinator`)**:
    - Garantiza que `selectedTrackA` y `selectedTrackB` sean dos pistas distintas por defecto cuando hay $\ge 2$ pistas locales y sincroniza su selección si `localTracks` se carga asíncronamente desde Room.
    - Muestra en vivo cualquier mensaje de error o advertencia en `CreateMixtapeDialog` para que el usuario siempre tenga feedback visual inmediato.
  - **Smart AutoMix DJ en Vivo (`AuraAudioPlayer`, `AudioFadeController`, `AudioEffectsCoordinator`)**:
    - Persiste `isDjAutomixEnabled`, `isDjEqCurveEnabled`, `crossfadeSeconds` e `isGaplessEnabled` en preferencias y los restaura al iniciar `MusicViewModel`.
    - Corrige la transferencia de la pista saliente a `outgoingExoPlayer` y ajusta el detector de outro acústico para que la superposición Dual-Deck se active con fiabilidad tanto en transiciones automáticas de fin de pista como en cambios de canción.
  - **Configuración Gradle y Workflow Debug (`app/build.gradle.kts` y `build-debug-apk.yml`)**:
    - En `app/build.gradle.kts`, habilita `splits.abi` (`arm64-v8a`, `armeabi-v7a` y `universal`) cuando se ejecuta una tarea Beta o cuando la variable de entorno `AURA_SPLIT_APKS` es `"true"`.
    - En `.github/workflows/build-debug-apk.yml`, aprovisiona las arquitecturas móviles (`arm64-v8a` y `armeabi-v7a`), compila los 3 APKs Debug, calcula sus sumas `SHA256SUMS.txt` en `dist-debug/` y publica los 4 artefactos descargables por separado en GitHub Actions.
