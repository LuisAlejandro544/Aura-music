# Aura Music — Evaluación de Potencial, Armonización Cromática en Streaming (1s), Indicadores de Carga y Optimización Térmica

Este plan presenta la evaluación integral del proyecto tras analizar toda su documentación técnica (`README.md`, `ROADMAP.md`, `AI_CONTEXT.md`, `STRUCTURE.md`, `READMEAI.md`, `AGENTS.md` y `chanelog-beta.md`), el diagnóstico exacto de por qué el teléfono se calienta en modo online y la arquitectura de las nuevas funciones solicitadas.

---

## 🌟 0. Evaluación del Proyecto: ¿Tiene potencial y vale la pena seguir trabajando en él?

**Respuesta directa: Sí, rotundamente. Aura Music tiene un potencial enorme y está muy por encima del 95% de los reproductores de música independientes para Android.**

Después de revisar todos los archivos `.md` y la arquitectura real del código, estas son las razones técnicas y de producto por las que vale totalmente la pena continuar:

1. **Diferenciación única en el mercado (Todo en Uno Real para Móvil)**:
   - La mayoría de las apps o son reproductores locales básicos, o son clientes de streaming simples (como ViMusic/NewPipe) sin procesamiento acústico serio. **Aura Music une ambos mundos**: tiene un motor **DSP en ISO C++20 de 64 bits + Google AAudio Bit-Perfect** (Ecualizador de 10 bandas, Audio 8D/16D Multi-Órbita, Clarificador de Voces Mid-Side, Reverb C++20, Crossfade Dual-Deck), junto con **Video Canvas reactivo**, **Karaoke LRCLIB**, **Mixtapes con capítulos**, **descargador `yt-dlp + FFmpeg` con actualización OTA** y **Streaming Híbrido (`YT Music` + `YouTube`)**.
2. **Pensada al 100% para usuarios móviles sin PC**:
   - Funciones como *Video a Música en 1 segundo*, recepción *"Compartir con..."*, exportación a carpeta pública con FFmpeg y auto-actualizador de APK por arquitectura (`arm64-v8a`, `armeabi-v7a`, `universal`) resuelven problemas reales de usuarios que gestionan todo desde su celular y distribuyen fuera de Google Play (Uptodown / GitHub Releases).
3. **Lo único que le falta para sentirse como una app comercial de primer nivel**:
   - Pulir la eficiencia energética/térmica en el nuevo modo **Streaming Online** y mejorar la retroalimentación visual cuando el internet está lento o cargando, que es **exactamente** lo que acabas de detectar.

---

## 🔥 Diagnóstico del Detective: ¿Por qué se calienta el teléfono y gasta batería en Streaming con Video?

Analizando línea por línea `StreamingModeCoordinator.kt`, `StreamingCacheManager.kt`, `StreamingApiService.kt`, `BackgroundVideoPlayer.kt` y `ArtworkColorExtractor.kt`, encontré **5 causas raíz exactas** que ocurren al mismo tiempo cuando escuchas música online con video:

> [!WARNING]
> **Las 5 causas reales del calentamiento en modo Online:**
>
> 1. **Doble descarga simultánea + Reconexión y Reinicio del Decodificador de Video a mitad de la canción**:
>    - Cuando inicias una canción online, `BackgroundVideoPlayer` empieza a descargar el video por streaming en ExoPlayer. **Al mismo tiempo**, `StreamingCacheManager.prefetchVideoCanvasAsync` abre *otra* descarga multihilo (`ChunkedStreamDownloader` con varios hilos HTTP Range) descargando el mismo video en segundo plano.
>    - Peor aún: cuando termina de bajar a la caché (~5-10 segundos después), ejecuta `appendOrUpdateStreamingTrack` cambiando `videoUri` de `https://...` al archivo local `/cache/...`. Como `NowPlayingScreen` usa `key(currentTrack.id, currentTrack.videoUri)`, **destruye el reproductor de video, apaga el códec hardware, vuelve a abrir otro `ExoPlayer`, re-sincroniza y re-decodifica desde cero** en plena reproducción. Lo mismo ocurre con el audio.
> 2. **Invocación pesada del proceso CPython 3.11 + QuickJS (`yt-dlp`) en cada cambio y pre-carga**:
>    - En `StreamingApiService.resolvePlayableStreamWithYtDlp`, se está llamando **primero** a `YtDlpNativeEngine.resolveStream` (que levanta un proceso Linux completo con Python 3.11 + QuickJS consumiendo mucha CPU durante varios segundos) y recién como segundo paso a `InnerTubeClient` (`<300ms`). Además, al llegar al 65% de la canción, la Radio Automática vuelve a levantar Python en segundo plano para la siguiente canción.
> 3. **Doble decodificador de video si el Mini Reproductor o transición coincide**:
>    - Cuando el video de streaming aún no está en caché, abrir extractores de metadatos (`MediaMetadataRetriever`) sobre una URL remota (`https://...googlevideo.com/...`) abre una tercera conexión HTTP y decodifica fotogramas por software en la CPU cada segundo.
> 4. **Bucle de `AudioVisualizer` y flujos de progreso a alta frecuencia**:
>    - Aunque en streaming el motor C++20 está en bypass (`isStreamingBypassActive = true`), la UI sigue consultando y animando barras e intensidad acústica constantemente junto con la composición del video a 60/120 FPS.
> 5. **Falta de límite de bitrate/tasa de fotogramas en el `ExoPlayer` del Video Canvas**:
>    - El reproductor de video de fondo no limita el framerate ni prioriza flujos ligeros cuando la fuente es una URL de red en vivo.

---

## 🛠️ User Review & Critical Decisions

> [!IMPORTANT]
> Decisiones confirmadas a partir de tus respuestas y análisis de tu captura de pantalla:

- **Indicador de Carga / Red Lenta**: Se integrará directamente **en el botón central Play/Pausa** (un anillo de carga fluido alrededor/dentro del botón como marcaste en tu captura) **y sobre la miniatura/carátula** tanto en *Now Playing* (Modo Cinemático y Modo Clásico) como en el *Mini Reproductor*, además de una etiqueta sutil cuando esté resolviendo el stream o esperando internet.
- **Cambio de Color Dinámico en Streaming (Cada 1 Segundo)**: Se actualizará `ArtworkColorExtractor` para que soporte **portadas online (`https://...`)** de YouTube / YT Music y mantenga el muestreo en vivo **cada 1 segundo (1000 ms)** desde el `TextureView` del video online sin abrir conexiones extra.
- **Solución al Sobrecalentamiento en Streaming**: Se corregirán las 5 causas raíz sin quitar ninguna función (ver sección 3).

---

## 1. Visión General de las Nuevas Funciones

- **Armonización Cromática Reactiva en YouTube y YT Music (1s)**:
  - Apenas elijas una canción de YouTube o YT Music, el reproductor y el Mini Reproductor extraerán al instante los colores vibrantes de su portada online (`https://i.ytimg.com/...`) y, en cuanto el Video Canvas esté en movimiento, transicionarán suavemente **cada 1 segundo (1000 ms)** siguiendo las escenas del video igual que en la música local.
- **Indicadores Visuales de Carga y Falta de Internet (En Play/Pausa y Miniatura)**:
  - Cuando una pista o su video estén resolviendo el enlace, cargando buffer (`STATE_BUFFERING`) o esperando conexión a internet, el **botón Play/Pausa** mostrará un anillo de progreso circular animado y la **miniatura (54dp en Cinemático / 46dp en MiniPlayer / Carátula en Clásico)** mostrará un indicador visual claro de carga/red sin congelar la interfaz.
- **Enfriamiento Inteligente en Streaming (Cero Recalentamiento y Menor Consumo de Batería)**:
  - Priorización de **InnerTube Nativo (`<300ms`, 0% sobrecarga de CPU)** antes de levantar el proceso Python de `yt-dlp`, eliminación de la doble descarga simultánea de video, prohibición de usar `MediaMetadataRetriever` sobre URLs HTTP remotas (usando solo el `TextureView` en GPU) y transición en caché diferida sin destruir el decodificador en mitad de la canción.

---

## 2. Experiencia de Usuario y Diseño Visual

### A. Indicadores de Carga en el Botón Play/Pausa y Miniatura (Basado en tu Captura)
1. **En el Botón Play/Pausa (`NowPlayingCinematicLayout`, `NowPlayingPlaybackControls` y `MiniPlayer`)**:
   - Cuando el audio o el video están cargando por red lenta (`isBuffering` o `isResolvingStream`) o esperando conexión:
     - Alrededor del botón circular blanco de 64dp (donde señalaste con el círculo rojo en tu captura) girará un **anillo luminoso neón (`CircularProgressIndicator`)** con el color dinámico de la canción (`animatedPrimary`).
     - En el **Mini Reproductor**, el botón Play/Pausa de 36dp también mostrará el anillo circular de carga.
2. **En la Miniatura y Área de Video**:
   - En la **miniatura de 54dp** (Modo Cinemático) y en la **miniatura de 46dp** (Mini Reproductor), mientras el video de fondo termina de conectar o si hay falta de internet, aparecerá un pequeño **badge/anillo de sincronización de video** sobre la esquina o centro de la miniatura.
   - Si el teléfono pierde el internet o la conexión está muy lenta durante varios segundos, se mostrará una píldora discreta junto al indicador de plataforma (`YT MUSIC` / `YOUTUBE`): *"Cargando video..."* o *"Conexión lenta • Esperando red..."*, que desaparece automáticamente en cuanto el primer fotograma se reproduce.

### B. Cambio de Color Dinámico en Streaming (Cada 1 Segundo)
1. **Fase 1 (Instantánea al tocar la canción)**:
   - Como las canciones de YouTube / YT Music tienen `albumArtPath = "https://..."`, `ArtworkColorExtractor` ahora descargará/leerá el bitmap ligero de la miniatura desde la caché de Coil/memoria o red en `Dispatchers.IO` (guardándolo en `StreamingCacheManager.artworkCacheDir`) y extraerá su paleta real con `Palette` en lugar de usar un color fijo.
2. **Fase 2 (Cada 1 segundo con el Video en Vivo)**:
   - Mientras el video online se reproduce en `BackgroundVideoPlayer`, el muestreo directo de `TextureView.getBitmap(24, 24)` actualizará `liveVideoColorsFlow` cada **1000 ms (1 segundo)** con un fundido suave de `tween(1000)`, tiñendo la viñeta cinemática, el halo del botón Play/Pausa, la barra de progreso, la píldora de Karaoke y el Mini Reproductor.

---

## 3. Decisiones Técnicas para Eliminar el Calentamiento en Streaming

- **Decisión 1: Priorizar `InnerTubeClient` (`<300ms`) sobre `yt-dlp` Python para Streaming en Vivo**
  - *Enfoque elegido*: En `StreamingApiService.resolvePlayableStreamWithYtDlp`, invertir el orden para que **primero** consulte `InnerTubeClient` (petición JSON ligera que tarda `<300ms` y casi no usa CPU) y **solo ejecute el proceso pesado CPython + QuickJS de `yt-dlp` como respaldo** si InnerTube no devuelve stream.
  - *Por qué*: Levantar el runtime nativo de Python + QuickJS en cada canción y en cada pre-carga de Radio ponía los núcleos del procesador al 100% durante 3 a 6 segundos seguidos, calentando el teléfono rápidamente.

- **Decisión 2: Prohibir `MediaMetadataRetriever` sobre URLs `http://` o `https://`**
  - *Enfoque elegido*: En `ArtworkColorExtractor.extractPlaybackColors`, si `videoUri` comienza con `http://` o `https://`, **jamás** abrir `MediaMetadataRetriever` por red. Para videos online, los colores se obtienen primero de la portada oficial y, en cuanto el video arranca, **100% desde el `TextureView` en GPU (`getBitmap(24, 24)`) cada 1 segundo**.
  - *Por qué*: `MediaMetadataRetriever.setDataSource(httpUrl)` abría un segundo cliente HTTP y decodificaba video por software en la CPU cada segundo paralelamente a ExoPlayer, duplicando el calor y el gasto de datos.

- **Decisión 3: Evitar Doble Descarga Simultánea y No Reiniciar el Reproductor en Plena Canción**
  - *Enfoque elegido*:
    1. Cuando `appendOrUpdateStreamingTrack` guarda el archivo de audio o video en la caché de 30 minutos mientras la canción **ya se está reproduciendo**, actualizará la ruta en la cola para futuras repeticiones/vuelta atrás **sin destruir ni recrear el `ExoPlayer` ni el `BackgroundVideoPlayer` activos** que ya tienen el buffer cargado.
    2. Escalonar la pre-descarga del caché de video para que no compita con los primeros segundos de arranque del reproductor.
  - *Por qué*: Evita descargar dos veces los mismos megabytes al mismo tiempo y elimina el pico de CPU/GPU de destruir y recrear el decodificador de video a mitad de la canción.

---

## 4. Arquitectura Técnica y Flujo de Estado

```text
┌────────────────────────────────────────────────────────────────────────────┐
│                   MODO STREAMING OPTIMIZADO (YT MUSIC / YT)                │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │
        ┌─────────────────────────────┴─────────────────────────────┐
        ▼                                                           ▼
┌───────────────────────────────────────┐   ┌───────────────────────────────────────┐
│   1. Motor de Resolución Térmica      │   │  2. Extractor Cromático Híbrido (1s)  │
│  • 1º InnerTube Nativo (<300ms, 0 CPU)│   │  • Portada HTTP -> Caché WebP -> Color│
│  • 2º Fallback yt-dlp Python si falla │   │  • Video Online -> Solo TextureView   │
│  • Sin recrear ExoPlayer a mitad      │   │    24x24 cada 1000ms (Cero Retriever) │
└───────────────────┬───────────────────┘   └───────────────────┬───────────────────┘
                    │                                           │
                    └─────────────────────┬─────────────────────┘
                                          ▼
┌────────────────────────────────────────────────────────────────────────────┐
│              3. Estado Reactivo de Carga y Red (Audio + Video)             │
│  • AuraAudioPlayer: isAudioBufferingFlow + isNetworkStalledFlow            │
│  • BackgroundVideoPlayer: isVideoBuffering + isWaitingForNetwork           │
│  • StreamingModeCoordinator: isResolvingStream                             │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │
                    ┌─────────────────┴─────────────────┐
                    ▼                                   ▼
┌───────────────────────────────────────┐   ┌───────────────────────────────────────┐
│   NowPlayingScreen (Cinemático/Clásico)│   │             MiniPlayer                │
│  • Anillo neón en botón Play/Pausa    │   │  • Anillo en botón Play/Pausa         │
│  • Indicador en miniatura (54dp)      │   │  • Indicador en miniatura (46dp)      │
│  • Aviso sutil si falta internet      │   │  • Colores vivos cada 1s              │
└───────────────────────────────────────┘   └───────────────────────────────────────┘
```

### Mapeo de Componentes y Estados Interactivos
- **Extracción de Color de Portadas Online (`ArtworkColorExtractor`)**:
  - Si `track.albumArtPath` es una URL (`http://` o `https://`), descarga/lee una miniatura optimizada (`inSampleSize` / `RGB_565`), la guarda en `cacheDir/streaming_media/artwork/` para no volver a pedirla por red y calcula los tonos `primary`, `secondary`, `accent` y `ambientTopGlow`.
  - Si el video de fondo está reproduciéndose, `BackgroundVideoPlayer` captura un micro-frame `24x24` del `TextureView` cada `1000 ms` y publica en `liveVideoColorsFlow`, actualizando toda la interfaz con `tween(1000)`.
- **Estados de Carga / Buffering / Falta de Red**:
  - `AuraAudioPlayer` expondrá `isBuffering: StateFlow<Boolean>` (activo cuando `playbackState == Player.STATE_BUFFERING`).
  - `BackgroundVideoPlayer` detectará `Player.STATE_BUFFERING` y fallos de red (`PlaybackException.ERROR_CODE_IO_*`), notificando a la UI si el video está cargando y reintentando automáticamente cuando vuelva la conexión a internet.
  - Tanto `NowPlayingCinematicLayout` como `NowPlayingPlaybackControls` y `MiniPlayer` mostrarán el indicador de carga en el botón Play/Pausa y en la miniatura cuando `isResolvingStream || isAudioBuffering || isVideoBuffering`.
