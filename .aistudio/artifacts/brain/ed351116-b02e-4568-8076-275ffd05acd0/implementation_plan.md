# Estabilidad de Descarga desde Reproductor Online y Motor `yt-dlp` Exclusivo para YouTube

Este plan corrige de raíz el cierre inesperado (crash) que ocurre al descargar una canción o video desde el reproductor online (modo Streaming) mientras otra pista está en reproducción, y configura el motor nativo **`yt-dlp` exclusivamente para pistas de YouTube** (`YOUTUBE`), manteniendo **YouTube Music** (`YT MUSIC`) con resolución directa ultrarrápida.

## User Review & Critical Decisions

> [!IMPORTANT]
> Se han incorporado todas tus preferencias confirmadas en las preguntas iniciales:

- **Guardar en biblioteca sin interrumpir la reproducción actual**: Al finalizar una descarga iniciada desde el reproductor online (pestaña *Online* o botón de descarga en *Now Playing*), la canción y su Video Canvas se guardarán silenciosamente en la biblioteca local (`songs/`, `images/`, `videos/`, `lyrics/`), retirando el indicador de carga y mostrando la confirmación sin reiniciar ni cortar la canción que estás escuchando.
- **`yt-dlp` exclusivo para pistas de YouTube con respaldo automático**:
  - Cuando reproduzcas o descargues un elemento de **YouTube** (`StreamingSourcePlatform.YOUTUBE`), se utilizará **primero `yt-dlp`** (`YtDlpNativeEngine` con CPython + QuickJS) y, solo si un video presenta bloqueo o restricción, entrará en acción el respaldo automático (`InnerTubeClient` / `WebStreamExtractor`).
  - Cuando reproduzcas una pista de **YouTube Music** (`StreamingSourcePlatform.YT_MUSIC`), **no se invocará `yt-dlp`**, sino directamente el cliente ligero `InnerTubeClient` para una respuesta instantánea (`<300ms`) y menor consumo térmico.
- **Descarga completa con Audio + Video Canvas 480p**: Las descargas desde el reproductor online conservarán su pista de audio en alta calidad (`.m4a`), su carátula oficial en WebP sin pérdida y su **Video Canvas en 480p** optimizado.

---

## 1. Overview & Core Concept

- **Qué soluciona**:
  1. **Causas del crash al descargar desde el reproductor online**:
     - **Acceso a ExoPlayer fuera del hilo principal (`IllegalStateException`)**: Al completarse una descarga en segundo plano desde `AuraDownloadService`, `TrackLibraryCoordinator` recibía el evento en un hilo secundario y llamaba directamente a `audioPlayer.playTrack(event.track)` sin cambiar a `Dispatchers.Main` (cuando la descarga venía del reproductor online sin `pendingDownloadSuccessCallback`). ExoPlayer exige que toda llamada a su estado ocurra en el hilo principal, provocando un crash inmediato al finalizar la descarga.
     - **Excepción `ForegroundServiceStartNotAllowedException` / `SecurityException` en Android 12–14+**: Si `startForeground` o la promoción del servicio falla o se invoca en un estado transitorio, se protege el ciclo de vida del servicio y la decodificación de `Bitmap` para la notificación, evitando cierres por memoria o permisos de servicio en primer plano.
     - **Limpieza reactiva del estado de descarga en botones de la pestaña Online**: El conjunto `downloadingVideoIds` ahora se limpia automáticamente en cuanto `AuraDownloadService` reporta éxito o error, actualizando el icono de descarga en la interfaz.
  2. **Separación estricta de motores (`yt-dlp` solo para YouTube)**:
     - En `StreamingApiService`, la resolución de streams discrimina explícitamente según la plataforma del ítem (`StreamingSourcePlatform.YOUTUBE` vs `StreamingSourcePlatform.YT_MUSIC`).
- **Audiencia / Contexto**: Usuarios de Aura Music que exploran y escuchan música en el reproductor online y desean guardar sus canciones y videos favoritos en su biblioteca local mientras siguen escuchando sin interrupciones.
- **Valor Principal**: Cero cierres inesperados al descargar desde el modo Online, reproducción continua sin cortes al guardar canciones en la biblioteca y cumplimiento estricto del uso de `yt-dlp` únicamente para pistas de YouTube.

---

## 2. User Experience & Visual Design

- **Flujo de Usuario al Descargar desde el Reproductor Online**:
  1. El usuario escucha una pista en el reproductor online (o navega por la pestaña *Online*) y pulsa el botón de **Descargar** en cualquier canción o video.
  2. El botón muestra un indicador de progreso circular mientras `AuraDownloadService` descarga el audio `.m4a`, genera la carátula `.webp` y procesa el Video Canvas a `480p`.
  3. Al terminar la descarga, la pista aparece inmediatamente en la **Biblioteca Local** y se muestra un aviso de confirmación (*"¡Éxito! Se guardó en tu biblioteca"*), **sin cortar ni reemplazar la música que está sonando actualmente**.
  4. El icono del botón en la lista Online pasa de "descargando" a "listo", permitiendo seguir navegando con fluidez.
- **Flujo de Usuario al Escuchar en el Reproductor Online**:
  1. Si el usuario toca un resultado con la insignia **`YOUTUBE`**, el reproductor resuelve el flujo de audio y Video Canvas 480p invocando **primero `yt-dlp`** (y usando respaldo automático si el video tiene bloqueo).
  2. Si el usuario toca un resultado con la insignia **`YT MUSIC`**, el reproductor resuelve directamente por API (`InnerTubeClient`) sin ejecutar `yt-dlp`.
- **Identidad Visual y Tema**:
  - Se conserva intacto el diseño actual de la aplicación, iconos, colores dinámicos y animaciones, sin alterar ningún elemento visual no solicitado.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisión 1: Diferenciar descargas silenciosas del Reproductor Online frente a descargas interactivas por enlace**
  - *Enfoque elegido*: Añadir la bandera explícita `autoPlayOnComplete: Boolean` en `PendingDownloadTask` y `AuraDownloadService.startDownload(...)`. Para las descargas iniciadas desde el reproductor online (`StreamingModeCoordinator`), `autoPlayOnComplete` se envía en `false` para que solo se guarde en la base de datos Room y notifique sin interrumpir la cola de reproducción activa. Además, cualquier invocación a `audioPlayer` en `TrackLibraryCoordinator` se envuelve obligatoriamente en `withContext(Dispatchers.Main)`.
  - *Por qué*: Evita el crash por hilo incorrecto en ExoPlayer (`Player is accessed on the wrong thread`) y respeta la experiencia de seguir escuchando la sesión de streaming sin cortes.
- **Decisión 2: Enrutamiento exclusivo de `yt-dlp` por plataforma (`YOUTUBE` vs `YT_MUSIC`)**
  - *Enfoque elegido*:
    - Para `StreamingSourcePlatform.YOUTUBE`: Prioridad #1 `YtDlpNativeEngine.resolveStream(...)` -> Respaldo #2 `InnerTubeClient.resolve(...)` -> Respaldo #3 `WebStreamExtractor.resolveStream(...)`.
    - Para `StreamingSourcePlatform.YT_MUSIC`: Prioridad #1 `InnerTubeClient.resolve(...)` -> Respaldo #2 `InvidiousStreamResolver.resolve(...)` (sin invocar `YtDlpNativeEngine`).
  - *Por qué*: Cumple exactamente la regla de usar `yt-dlp` solo para YouTube y ofrece un respaldo transparente si un video tiene restricción regional o de firma.
- **Decisión 3: Blindaje de hilos y recursos en `AuraDownloadService` y `MediaAssetProcessor`**
  - *Enfoque elegido*: Capturar `Throwable` (incluyendo `OutOfMemoryError` al decodificar miniaturas o fotogramas de video y `ForegroundServiceStartNotAllowedException` en Android 12+) dentro del servicio de descarga y en el procesador de Video Canvas, asegurando también la limpieza de `downloadingVideoIds` al terminar cada tarea.
  - *Por qué*: Garantiza que ningún fallo de red, memoria gráfica o restricción del sistema operativo en segundo plano pueda tumbar el proceso principal de la app.

---

## 4. Technical Architecture & Data Strategy

### Diagrama de Arquitectura y Flujo de Resolución / Descarga

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   REPRODUCTOR ONLINE (Modo Streaming)                      │
└───────────────────┬────────────────────────────────────┬───────────────────┘
                    │                                    │
       [Escuchar Canción / Video]             [Descargar Canción / Video]
                    │                                    │
                    ▼                                    ▼
┌───────────────────────────────────────┐    ┌───────────────────────────────┐
│     StreamingApiService.resolve       │    │   StreamingModeCoordinator    │
├───────────────────────────────────────┤    │  autoPlayOnComplete = false   │
│ ¿Plataforma == YOUTUBE?               │    │  attachAsCanvas = true (480p) │
│  ├─ SÍ (Solo YouTube):                │    └───────────────┬───────────────┘
│  │   1º YtDlpNativeEngine (Primario)  │                    │
│  │   2º InnerTube / Web (Respaldo)    │                    ▼
│  │                                    │    ┌───────────────────────────────┐
│  └─ NO (YT_MUSIC):                    │    │      AuraDownloadService      │
│      1º InnerTubeClient (Directo)     │    │  (Hilo IO + Protección OOM)   │
│      2º InvidiousResolver (Respaldo)  │    ├───────────────────────────────┤
│         (Sin usar yt-dlp)             │    │ 1. Audio alta calidad (.m4a)  │
└───────────────────────────────────────┘    │ 2. Carátula WebP sin pérdida  │
                                             │ 3. Video Canvas 480p (.mp4)   │
                                             │ 4. Guarda en Room Database    │
                                             └───────────────┬───────────────┘
                                                             │
                                                             ▼
                                             ┌───────────────────────────────┐
                                             │    TrackLibraryCoordinator    │
                                             │   (Hilo Main Seguro / UI)     │
                                             ├───────────────────────────────┤
                                             │ • Limpia downloadingVideoIds  │
                                             │ • No interrumpe reproducción  │
                                             │ • Muestra aviso de éxito      │
                                             └───────────────────────────────┘
```

### Mapeo de Estado y Componentes Interactivos

1. **Enrutador de Streaming (`StreamingApiService`)**:
   - Inspecciona `item.platform`.
   - Si es `StreamingSourcePlatform.YOUTUBE`: ejecuta `YtDlpNativeEngine.resolveStream(context, normalizedUrl)` en primer lugar. Si falla o está bloqueado temporalmente por actualización OTA, recurre a `InnerTubeClient.resolve(...)` y `WebStreamExtractor`.
   - Si es `StreamingSourcePlatform.YT_MUSIC`: resuelve directamente con `InnerTubeClient.resolve(...)` y `InvidiousStreamResolver`, omitiendo `YtDlpNativeEngine`.
2. **Coordinador de Streaming (`StreamingModeCoordinator`)**:
   - Al llamar a `downloadStreamingItemPermanently`, resuelve el flujo según las reglas anteriores (incluyendo URL de Video Canvas 480p) e invoca `AuraDownloadService.startDownload(..., attachAsCanvas = true, autoPlayOnComplete = false, sourceVideoId = item.videoId)`.
   - Escucha los eventos de finalización y error de `AuraDownloadService` para retirar el `videoId` de `_downloadingVideoIds`, restaurando el botón de descarga en la UI.
3. **Servicio y Coordinador de Biblioteca (`AuraDownloadService` & `TrackLibraryCoordinator`)**:
   - `AuraDownloadService` propaga `autoPlayImmediately = task.autoPlayOnComplete && isUserInsideApp` y protege `startForeground`, notificaciones y decodificación de imágenes con bloques `runCatching` sobre `Throwable`.
   - `TrackLibraryCoordinator` ejecuta cualquier callback de reproducción exclusivamente dentro de `withContext(Dispatchers.Main)` y solo cuando `event.autoPlayImmediately` es `true`.
