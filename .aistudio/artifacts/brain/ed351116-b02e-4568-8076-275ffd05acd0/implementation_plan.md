# Actualización a `v0.2.0-beta-1a` + Corrección de Diseño en Ajustes, Visualizador Streaming y Botón Animado de Descarga

Este plan actualiza la versión oficial de **Aura Music** a **`v0.2.0-beta-1a`** (`NEBULA-00200A`, `versionCode = 100200`) en el proyecto, en `chanelog-beta.md` y en toda la documentación `.md`, y resuelve los errores visuales y de comportamiento reportados en las capturas de pantalla.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Decisiones confirmadas por el usuario:**
> - **Salto de Versión Recargada**: Se actualiza a **`v0.2.0-beta-1a`** (`APP_BUILD_CODE = "NEBULA-00200A"`, `versionCode = 100200`), conservando íntegro el contenido de `chanelog-beta.md` y actualizando también los archivos `.md` del proyecto (`README.md`, `READMEAI.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md` y `AGENTS.md`).
> - **Corrección de Textos Desbordados en Ajustes**: Se corrige el desbordamiento y aplastamiento vertical de texto en `SettingDetailRow` (como *"Desactivado (100% bajo control del usuario)"* y *"FFmpeg puro nativo (Activo)"* en *Motores & Actualizaciones APK*) y en botones de dos columnas (*"Foto de Galería"* / *"Video de Fondo"* en *Fondo de Pantalla de Galería*).
> - **Botón de Descarga en Streaming con Animación y Confirmación Real (`✓`)**: Al pulsar el botón de descarga en una canción de Streaming (*Explorar Streaming* y *Now Playing*), en lugar de mostrar el check `✓` inmediatamente al iniciar la tarea, mostrará una **animación fluida de la flecha bajando sucesivamente** mientras la canción se descarga en segundo plano, y **solo cambiará al icono `✓` cuando la descarga haya finalizado realmente** y la pista esté guardada en la biblioteca local.
> - **Reactividad en Tiempo Real de las Bandas Espectrales en Streaming**: Se asegura que las 28 barras del visualizador (`AudioVisualizer`) reaccionen en vivo al ritmo de las canciones de Streaming sin quedarse congeladas ni depender de si el ecualizador está activado o desactivado.

---

### 1. Overview & Core Concept

- **Qué resuelve**:
  1. **Desbordamiento vertical de texto en Ajustes (`SettingDetailRow` y botones en fila)**:
     - En `SettingsSubScreenComponents.kt`, `SettingDetailRow` utilizaba un `Row` con `Arrangement.SpaceBetween` sin pesos (`weight`) entre la etiqueta izquierda y el valor derecho. Cuando el valor derecho era largo (ej. *"Desactivado (100% bajo control del usuario)"*), empujaba el layout hasta dejar apenas 1–2 caracteres por renglón, desbordándose en una columna vertical ilegible.
     - En `AppWallpaperSettingsSection.kt` y `StreamingCacheAndVideoSettingsCard.kt`, los botones en fila horizontal (*"Foto de Galería"* / *"Video de Fondo"*) podían partir el texto en dos líneas apretadas en pantallas compactas.
  2. **Icono `✓` prematuro al descargar canciones de Streaming**:
     - En `StreamingTrackRow` (`StreamingExploreScreen.kt`), el botón de descarga cambiaba un `remember { mutableStateOf(false) }` local (`justTriggered = true`) en el instante del clic, mostrando el check verde `✓` antes de que la canción se descargara.
     - Ahora se conectará al estado real de `AuraDownloadService` y de la biblioteca local (`tracks`): mientras se está descargando mostrará un componente animado con la **flecha bajando sucesivamente**, y únicamente cuando la pista termine de descargarse y guardarse mostrará el check verde **`✓`**.
  3. **Bandas del visualizador (`AudioVisualizer`) que no reaccionan en tiempo real en canciones de Streaming**:
     - Durante la reproducción de pistas de Streaming (especialmente formatos Opus/WebM/M4A servidos por flujo HTTP o caché con muestreo de 48 kHz o multicanal), si `NativeAudioProcessor` recibe un formato PCM distinto o si el flujo de telemetría experimenta latencia de buffer, las bandas podían quedarse estáticas en el valor base (`0.08f` / `0.18f`).
     - Se reforzará `NativeAudioProcessor` para soportar conversión y análisis espectral en cualquier codificación PCM (`ENCODING_PCM_16BIT`, `ENCODING_PCM_FLOAT`, `ENCODING_PCM_24BIT_BIG_ENDIAN` / `32BIT`) que entregue ExoPlayer en pistas de Streaming, garantizando que `NativeAudioEngine` y `AudioVisualizer` actualicen las 28 bandas espectrales a 33 FPS en tiempo real tanto en canciones locales como en Streaming.

---

### 2. User Experience & Visual Design

- **Filas de Información Técnica en Ajustes (`SettingDetailRow`)**:
  - Diseño de dos columnas balanceadas (`weight(0.42f)` para la etiqueta y `weight(0.58f)` alineado al final con `TextAlign.End` para el valor), garantizando que textos largos se acomoden limpiamente en 1 o 2 líneas horizontales sin jamás partirse letra por letra.
- **Animación de Flecha Descendente en Descargas de Streaming**:
  - **Estado 1 — Disponible para descargar**: Icono de descarga (`Icons.Default.Download` / `CloudDownload`) con tinte primario.
  - **Estado 2 — Descargando en curso**: Animación continua (`rememberInfiniteTransition`) donde la flecha desciende suavemente dentro de una bandeja/halo luminoso de forma sucesiva, indicando visualmente que el archivo se está bajando.
  - **Estado 3 — Descarga completada (`✓`)**: Una vez que la descarga culmina al 100% (o si la canción ya fue descargada previamente en la biblioteca), el botón muestra el signo **`✓`** (`Icons.Default.Check`) en verde esmeralda (`#10B981`).

---

### 3. Technical Architecture & File Changes

```
┌────────────────────────────────────────────────────────────────────────────┐
│                FLUJO DE ESTADOS DE DESCARGA Y VISUALIZACIÓN                │
└────────────────────────────────────────────────────────────────────────────┘
  [Click en Descargar Pista Streaming]
                 │
                 ▼
  ┌──────────────────────────────────────────────────────────┐
  │ StreamingModeCoordinator / MusicViewModel                │
  │ • Registra ID/URL en downloadingStreamingIds             │
  │ • Observa AuraDownloadService.activeProgress & tracks    │
  └──────────────┬───────────────────────────────────────────┘
                 │
       ┌─────────┴──────────┐
       ▼                    ▼
  [Descargando]        [Completado / En Biblioteca]
  Flecha animada       Muestra check verde (✓)
  bajando en bucle     confirmando descarga real
```

1. **Versión `v0.2.0-beta-1a` y Documentación**:
   - `app/build.gradle.kts`: Actualizar `versionCode = 100200`, `versionName = "0.2.0-beta-1a"` y `APP_BUILD_CODE = "NEBULA-00200A"`.
   - `chanelog-beta.md`: Actualizar el encabezado y metadatos de versión a `v0.2.0-beta-1a` (`NEBULA-00200A` / `100200`) sin borrar ninguna sección existente.
   - `README.md`, `READMEAI.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md` y `AGENTS.md`: Sincronizar las referencias de versión a `v0.2.0-beta-1a` (`NEBULA-00200A` / `100200`).
2. **Corrección de Desbordamiento de Texto en Ajustes**:
   - `app/src/main/java/com/example/ui/screens/settings/components/SettingsSubScreenComponents.kt`: Rediseñar `SettingDetailRow` con pesos proporcionales y alineación `TextAlign.End` para evitar texto aplastado verticalmente.
   - `app/src/main/java/com/example/ui/screens/settings/components/appearance/AppWallpaperSettingsSection.kt`: Ajustar `maxLines = 1`, `TextOverflow.Ellipsis` y `contentPadding` en los botones *"Foto de Galería"* y *"Video de Fondo"* para que nunca se desborden.
3. **Animación de Descarga y Estado Real (`✓`) en Streaming**:
   - `app/src/main/java/com/example/viewmodel/delegates/StreamingModeCoordinator.kt` y `MusicViewModel.kt`: Mantener el seguimiento reactivo de las canciones de Streaming en proceso de descarga (`downloadingStreamingKeys`) y completadas (`downloadedStreamingKeys` + cruce con `tracks` locales cuando `AuraDownloadService` emite `isCompleted`).
   - `app/src/main/java/com/example/ui/screens/streaming/StreamingExploreScreen.kt`, `NowPlayingTopBar.kt` y `NowPlayingCinematicLayout.kt`: Incorporar el botón con animación continua de flecha bajando mientras descarga y check `✓` únicamente tras completarse la descarga.
4. **Reactividad en Tiempo Real de las Bandas (`AudioVisualizer`) en Streaming**:
   - `app/src/main/java/com/example/playback/NativeAudioProcessor.kt` y `AuraAudioPlayer.kt`: Asegurar que `NativeAudioProcessor` acepte y procese flujos `PCM_16BIT` y `PCM_FLOAT` (habituales en el decodificador Opus/AAC de Media3 durante Streaming), alimentando el analizador espectral C++20 en tiempo real y garantizando el bombeo continuo de `visualizerBands` mientras la pista de Streaming está en reproducción.
