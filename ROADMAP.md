# Aura Music - Roadmap de Desarrollo 🚀

Este documento traza las fases de evolución técnica y funcional para convertir a **Aura Music** en el reproductor de audio local más avanzado, visualmente atractivo y de mayor fidelidad en Android.

---

## 📌 Fase 1: Arquitectura Base y Motor Media3 (Completada ✅)

- [x] Interfaz de usuario completa con Jetpack Compose y Material Design 3.
- [x] Diseño estético Dark Luxury Neo-Glass con 5 paletas de colores vibrantes y acentos de neón.
- [x] Motor de reproducción con **Jetpack Media3 (ExoPlayer)**.
- [x] Persistencia local reactiva con **Room Database**.
- [x] Importación selectiva basada en Storage Access Framework (SAF) respetando la privacidad.
- [x] Generador de pistas sintetizadas WAV para pruebas automáticas y de primer uso.
- [x] Eliminación de dependencias de `.env` para compilación limpia y autónoma.
- [x] Elevación de requisitos mínimos a **Android 8.0 (API 26)**.

---

## ⚡ Fase 2: Motor DSP Nativo en C++20, Carátulas y Playlists (Completada ✅)

- [x] **Integración de C++20 con CMake en el APK Final**:
  - Enlace con `externalNativeBuild` y filtros ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`).
  - Filtros IIR Bi-cuadráticos (*Peaking Biquads*) en C++20 con `std::span` y limitador anti-clipping.
  - Sincronización completa de llamadas JNI con `NativeAudioEngine`.
- [x] **Carátulas Personalizadas desde Galería**:
  - Integración del Android Photo Picker (`ActivityResultContracts.PickVisualMedia`).
  - Conversión a WebP sin pérdida en segundo plano (`Dispatchers.IO`).
  - Eliminación física de la carátula anterior para evitar basura en almacenamiento.
  - Opción de restaurar a arte procedural en cualquier momento.
- [x] **Sistema Completo de Playlists en la Biblioteca**:
  - Pestaña de Playlists completamente funcional.
  - Tarjeta especial sincronizada "Tus Me Gusta" / Favoritos.
  - Creación, nombrado y renombrado libre de playlists.
  - Diálogo modal para añadir canciones rápidamente desde la biblioteca.
- [x] **Pulido de Animaciones del Sistema**:
  - Transiciones de pantalla fluidas con `AnimatedContent` (desvanecimiento y deslizamiento).
  - Expansión y repliegue elástico de la pantalla completa Now Playing.
  - Micro-interacciones y feedback táctil enriquecido.
- [x] **Pipeline de CI/CD GitHub Actions con Caché Nativa y Orquestación Pura en Gradle (Cero `.sh`)**:
  - Workflow manual (`workflow_dispatch`) con tarea integrada `ensureDebugKeystore` en Gradle.
  - **Caché Inteligente de Binarios Nativos (`actions/cache@v4`)**: Sistema en GitHub Actions que almacena y restaura los `.so` de FFmpeg, CPython 3.11, QuickJS y el binario de `yt-dlp` en segundos, con compilación y ensamblaje autónomo mediante la tarea `:app:provisionNativeDeps` en `app/build.gradle.kts` ante cache-miss o actualización de parámetros.

---

## 🎧 Fase 3: Audio 8D Espacial en C++20, Temporizador y Control de Reproducción (Completada ✅)

- [x] **Motor Nativo de Audio 8D Binaural para Auriculares (ISO C++20)**:
  - Algoritmo de rotación orbital continua de potencia constante con control de velocidad (4s a 30s por vuelta).
  - Simulación de sombra acústica de la cabeza (*Head Shadow Effect*) al transitar el sonido detrás del oyente.
  - Reverberación acústica binaural y ajuste de profundidad de sala sin modelos 3D invasivos.
- [x] **Temporizador de Apagado (Sleep Timer) con Fade-Out Suave**:
  - Configuración libre de minutos personalizados y chips rápidos (15m, 30m, 45m, 60m).
  - Contador regresivo en tiempo real (`mm:ss`) con opción de añadir +5 minutos.
  - Atenuación progresiva lineal del volumen en los últimos 10 segundos antes de pausar la reproducción.
- [x] **Control de Velocidad y Tono (Playback Parameters)**:
  - Modulación en tiempo real de velocidad (0.50x a 2.00x) y tono musical (Pitch Shift).
  - Botón de restablecimiento rápido a 1.0x.
- [x] **Reproducción Continua Automática, Transiciones y Fade-In de Volumen**:
  - Salto automático a la siguiente canción al terminar la pista actual sin pausas no deseadas.
  - Fundido cruzado ajustable con rampa de entrada suave (*fade-in*) que restaura fielmente el volumen original al pasar de pista.
  - Conmutador de reproducción Gapless continua sin silencios intermedios.
- [x] **Hoja Modal de Efectos en Now Playing (`AudioEffectsBottomSheet`)**:
  - Componente accesible desde la barra superior de Now Playing con indicador de insignia activa.

---

## 🛠️ Fase 4: Estabilidad Crítica, Diagnóstico Autónomo y UX Fluida (Completada ✅)

- [x] **Suite de Diagnóstico en Caliente "Aura Monitor"**:
  - Aplicación de depuración conectada pero con icono propio en el cajón de apps del teléfono móvil.
  - Captura y persistencia de excepciones fatales no controladas (`UncaughtExceptionHandler` -> CRASH).
  - Registro de incidentes clasificados por severidad (`CRASH`, `CRÍTICO`, `ERROR`, `WARNING`, `INFO`).
  - Telemetría técnica del teléfono: Fabricante, Modelo, Versión de Android / SDK API, CPU ABI, RAM y almacenamiento libre.
  - Visualizador de Stack Trace en crudo y exportación de informe completo al portapapeles.
- [x] **Integración de LeakCanary**:
  - Detección automática en tiempo real de fugas de memoria en builds de depuración.
- [x] **Ecualizador C++20 Integrado en Modal (Cero Apartados Aislados)**:
  - Eliminación de la pantalla completa separada para el ecualizador; integración nativa en pestaña modal accesible instantáneamente desde el Mini Reproductor y Now Playing.
- [x] **Estabilidad de Velocidad y Tono (Anti-Pausa Accidental)**:
  - Implementación de *throttling* y *debouncing* en `AuraAudioPlayer` para evitar saturación de `SonicAudioProcessor` en ExoPlayer.
  - Mecanismo de recuperación automática que reanuda la reproducción si el procesador de audio sufre una perturbación transitoria.
- [x] **Transición Fluida de Salida en Now Playing**:
  - Eliminación de capas negras residuales y parpadeos al cerrar o deslizar hacia abajo el reproductor mediante deslizamiento suave instantáneo.
- [x] **Video Canvas y Fondo Completo (3 Modos Seleccionables)**:
  - Soporte para asociar videos desde la galería a canciones individuales con Photo/Media Picker.
  - **3 Modos de Visualización**: Fondo Completo (video detrás de toda la pantalla con velo oscuro y carátula flotando al frente), Lienzo en Carátula (recuadro central 1:1) y Solo Carátula.
  - Selector modal interactivo accesible desde la barra superior y badge dinámico en la carátula.
  - Loops automáticos de Canvas (≤ 10s - 20s) en bucle continuo y videos sincronizados (> 20s) con selector de modo en edición.
  - **Armonización Cromática Reactiva**: Extracción dinámica de color desde fotogramas clave de video en tiempo real, evitando que el color de la carátula estática interfiera con el video.
  - Sincronización temporal reactiva con la música (`currentPositionMs` y `seekTo`) para videos largos.
  - Almacenamiento organizado en subcarpeta `videos/` con eliminación de archivos obsoletos.
- [x] **Sincronización Reactiva de Favoritos y Estabilidad en Inicio**:
  - Reflejo instantáneo del estado de favorito en el reproductor (corazón lleno en rojo `Color(0xFFEF4444)` al activar).
  - Estabilidad permanente en la sección "Populares en tu biblioteca", evitando que al pulsar el corazón se cambie la lista o se oculten otras canciones.
- [x] **Conversor Nativo "Video a Música" (Extracción 3 en 1)**:
  - Extracción directa de audio (`.m4a`) desde videos de la galería mediante demuxing sin recodificación en Android.
  - Captura y compresión automática de fotograma clave en WebP sin pérdida como carátula oficial.
  - Vinculación automática opcional como Video Canvas sincronizado o loop con inicio inmediato de reproducción.
- [x] **Descarga Directa desde TikTok y Enlaces Web (Música + Carátula + Video Canvas)**:
  - Descarga sin límites de duración para canciones completas, parodias y audios virales pegando el enlace.
  - Resolución inteligente sin marcas de agua con descarga de audio en alta fidelidad a `songs/`.
  - Captura y conversión de carátula oficial en WebP sin pérdida a `images/`.
  - Vinculación de Video Canvas continuo o sincronizado en `videos/` y reproducción instantánea en Now Playing.
- [x] **Soporte de Colores Dinámicos Material You (Android 12+)**:
  - Incorporación del tema Material You a las 5 opciones de personalización, adaptando los acentos primarios y secundarios a los colores del fondo de pantalla del sistema operativo.
- [x] **Blindaje Tipográfico Fijo contra Desbordamientos**:
  - Escala tipográfica estabilizada a valor cómodo (`fontScale = 1.0f`) garantizando que los ajustes globales de tamaño de letra de Android no desborden la interfaz.

---

## 🎧 Fase 5: Suite Acústica de Auriculares, Crossfeed C++20 y Ergonomía (Completada ✅)

- [x] **Filtro Crossfeed Acústico Nativo (ISO C++20)**:
  - Algoritmo acústico Bauer / Chu Moy para auriculares con filtro paso-bajos (~700 Hz) y retardo interaural (ITD).
  - Activación exclusiva cuando se detecta un auricular conectado (`isHeadphoneConnected`), en reposo para altavoces.
  - 3 niveles acústicos: Sutil (Bauer 4.5 dB), Moderado (Chu Moy Classic) e Intenso (Monitores de Estudio).
- [x] **Balance Estéreo Fino (Control Izquierda / Derecha L/R en Tiempo Real)**:
  - Compensación milimétrica de balance L/R con paneo suave y limitador suave anti-clipping en C++20.
  - Barra de ajuste en tiempo real en la pantalla completa Now Playing y en la hoja modal de efectos con botón de centrado.
- [x] **Protección contra Desconexiones ("Becoming Noisy" Guard)**:
  - Intercepción inmediata de `ACTION_AUDIO_BECOMING_NOISY` para pausar la música al desenchufar audífonos.
- [x] **Reanudación con Fade-In Suave (Volumen Progresivo)**:
  - Rampa suave de volumen de ~1 segundo al reanudar la reproducción con audífonos puestos para proteger los oídos.
- [x] **Memoria de Volumen Dedicada para Audífonos**:
  - Almacenamiento independiente de nivel de volumen para auriculares vs altavoz del dispositivo.
- [x] **Control Avanzado de Botones y Gestos de Auriculares (Headset Controls)**:
  - Detección de 1 pulsación, 2 pulsaciones, 3 pulsaciones y pulsación prolongada (hold) con mapeo libre personalizable.
- [x] **Pestaña Dedicada de "Auriculares" en Configuración**:
  - Navegación segmentada en 3 apartados: 1. Acústica & DSP, 2. Seguridad & Conexión, 3. Botones y Gestos.
- [x] **Notificación Nativa de Android y Servicio en Primer Plano (Media3 MediaSessionService)**:
  - Controlador multimedia nativo del sistema (System Media Controls) en Android 13, 14, 15+ con arte de carátula en alta definición y seekbar interactiva.
  - Compatibilidad retroactiva completa para Android 11/12 (Quick Settings) y Android 8/9/10 (`MediaStyle`).
  - Canal de notificación silencioso (`IMPORTANCE_LOW`) para transiciones limpias y libres de interrupciones sonoras.
  - Reproducción continua e indestructible en segundo plano con pantalla apagada.
- [x] **Descarga Directa desde YouTube y Video Web con Arquitectura Resiliente de 3 Niveles**:
  - **Motor Nativo InnerTube (`InnerTubeClient`)**: Clientes de baja fricción `ANDROID_VR` y `VISIONOS` para extracción ultrarrápida (<300ms) libre de `LOGIN_REQUIRED` o cifrado de firma `n-sig`.
  - **Bypass de Respaldo Invidious (`InvidiousStreamResolver`)**: Consulta en tiempo real a espejos de alta disponibilidad para resolver pistas protegidas por derechos o VEVO.
  - **Motor Headless WebView Reparado (`HeadlessWebViewExtractor`)**: Navegador efímero móvil (`m.youtube.com`) con bypass de gestos, timeout de 22s y captura en memoria.
  - **Selector Interactivo de Motor**: Permite al usuario alternar entre InnerTube y WebView en el diálogo con auto-fallback cruzado de 3 capas.
  - Adaptación visual dinámica al enlace pegado (Cyan/Magenta para TikTok y Rojo/Naranja para YouTube/Web).
  - Extracción automática de audio `.m4a`/`.mp3`, carátula WebP y Video Canvas sincronizado para Now Playing.

---

## 🎤 Fase 6: Visualizador Espectral, Intensidad Acústica C++20 y Letras Karaoke (Completada ✅)

- [x] **Visualizador Espectral y Armonización Dinámica de Video Canvas**:
  - Medición RMS en tiempo real y cálculo de envolvente espectral de 28 bandas directamente en el motor nativo (`getAudioIntensity()` y `getVisualizerBands()`).
  - Carátula central y Video Canvas despejados al 100% (eliminación de cápsulas flotantes superpuestas) con escala fija 1.0f para máxima nitidez sin movimientos no deseados.
  - Sombra y halo lumínico ambiental superior modulando brillo y color en intervalos de 2.5s según las escenas del video con fundido suave (`tween(1200)`).
  - Visualizador de ondas sonoras `AudioVisualizer` alimentado por las bandas calculadas en C++.
- [x] **Mini Reproductor Tintado y Atajos Equilibrados**:
  - Fondo del mini reproductor completamente tintado y degradado con los colores extraídos de la pista o video actual.
  - Botonera inferior de Now Playing rediseñada en 3 módulos simétricos con área táctil superior a 48dp, resolviendo el corte vertical en el botón de cola.
  - Desbloqueo total de duración en TikTok mediante demuxing directo de audio sin recodificación para pistas de hasta 1 hora.
- [x] **Letras Sincronizadas (.LRC) Estilo Karaoke con Descarga Automática**:
  - Descarga automática libre desde el servicio público **LRCLIB** al iniciar canciones sin letra, sin registro ni claves externas.
  - Persistencia local en la subcarpeta estructurada `lyrics/track_{id}.lrc`.
  - Tarjeta interactiva `NowPlayingLyricsCard` con desplazamiento suave automático (*auto-scroll*) para centrar la frase activa.
  - Resaltado neón de alta visibilidad con tipografía ampliada (`19.sp`, negrita extra) para el verso actual y atenuación de líneas pasadas.
  - Salto interactivo a la marca de tiempo (*Seek*) al tocar cualquier verso.
  - Diálogo para carga o edición manual de letras en formato `.lrc` o texto plano.

- [x] **Integración Inteligente 'Abrir con...' y 'Compartir con...' (Puente con SnapTube, Gestores de Descarga y Navegadores)**:
  - Detección automática y triaje certero de medios (`IncomingMediaHandler`) combinando MIME type, extensión de archivo y análisis de cabeceras en `MediaMetadataRetriever`.
  - Para audios (`audio/*`): copia segura a `songs/` para evitar pérdidas si la app externa borra caché o revoca permisos, inserción en Room y reproducción inmediata con Now Playing expandido y búsqueda de letras LRCLIB.
  - Para videos (`video/*`): apertura inmediata de `VideoToMusicDialog` para conversión 3 en 1 (.m4a, carátula WebP y Video Canvas sincronizado).
  - Para enlaces web (`text/plain` / URL): apertura directa de `DownloadFromLinkDialog` con auto-resolución del título, autor y carátula.
  - Soporte `singleTop` en `MainActivity` y manejo fluido tanto en arranque en frío como en segundo plano vía `onNewIntent`.
- [x] **Eliminación Inteligente de Silencios al Inicio y Final (`AudioSilenceTrimmer`)**:
  - Interruptor interactivo previo a cada importación (archivos, carpetas, Video a Música, descargas de TikTok/YouTube y recepción con 'Abrir con...').
  - Detección acústica de silencios iniciales y finales por umbral RMS (`-42 dB`) decodificando únicamente las colas inicial y final.
  - Recorte sin pérdida por *Direct Stream Copy* en contenedores compatibles y configuración de recorte exacto con `MediaItem.ClippingConfiguration`.
- [x] **Repetidor de Segmento A-B (A-B Loop) y Ergonomía Visual**:
  - Barra compacta `[A]`, `[B]` y limpiar (`×`) integrada junto al progreso en *Now Playing* con marcador visual sobre el Seekbar.
  - Panel detallado en la hoja modal de efectos con ajuste fino de ±1 segundo (`-1s` / `+1s`) e interruptor de bucle.
  - Limpieza de la barra superior de *Now Playing* eliminando el texto redundante "REPRODUCIENDO AURA / álbum" para dar mayor amplitud a los iconos de acción.
  - Desplazamiento horizontal continuo tipo marquesina (`basicMarquee`) para títulos largos en *Now Playing*, *Mini Reproductor* y canción activa en listas y cola.
  - Modal de descarga (`DownloadFromLinkDialog`) completamente deslizable verticalmente (`verticalScroll`) para acceso total en pantallas compactas al descargar desde YouTube o TikTok.
  - Corrección definitiva del Reverb procesándolo 100% en el motor nativo C++20 (`ReverbProcessor`) sin bloqueo ni silenciamiento del driver LVREV de Android.
  - Ampliación de Velocidad de Música y Velocidad de Voz / Tono (`Pitch`) hasta `2.0x` con botones rápidos (`0.8x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`) en ambos controles.

- [x] **Garantía de Carátulas Oficiales en Descarga WebView y Fallback en Cascada**:
  - Corrección de miniaturas en el motor WebView mediante extracción de metadatos en el DOM y descarga en cascada multinivel (`maxresdefault.jpg` -> `hqdefault.jpg` -> `mqdefault.jpg` -> `i.ytimg.com` -> fotograma clave de video).
  - Elimina al 100% las canciones sin carátula al descargar mediante el motor WebView en YouTube.
- [x] **Importador de Letras (.LRC y .TXT) desde el Celular y Auto-Detección Local**:
  - Botón integrado en la tarjeta Karaoke y en estado vacío para importar archivos `.lrc` y `.txt` descargados en el teléfono mediante SAF (`OpenDocument`).
  - Detección y asociación automática de archivos de letras hermanos (`.lrc`/`.txt` con el mismo nombre) al importar música o carpetas desde el teléfono.
  - Soporte de visualización para texto plano `.txt` con desplazamiento continuo y adaptación tipográfica.
- [x] **Suite de Rendimiento, RAM Detallada e Inspector de Hilos en Aura Monitor**:
  - Navegación modular por pestañas: *Incidentes & Logs* y *Rendimiento & Hilos*.
  - Monitoreo en vivo de RAM segmentada: Java Heap (VM), Native Heap (C++20 DSP y CMake), Gráficos/Shaders (Compose y WebP) y PSS Total.
  - Telemetría de CPU del proceso (%), núcleos activos, FPS de UI y estado térmico del procesador.
  - Inspector de hilos en vivo (`Thread.getAllStackTraces()`) con clasificación por categorías (Audio, C++ DSP, UI, Corrutinas), estado (`RUNNABLE`, `TIMED_WAITING`), trazas de pila completas y copia al portapapeles.
- [x] **Video Canvas 100% Despejado, Sincronización de Velocidad y Armonización Cromática Instantánea**:
  - Eliminación definitiva del badge o indicador "VIDEO SYNC" / "LOOP CANVAS" sobre la carátula para una visualización sin obstrucciones.
  - Extracción cromática exacta con `MediaMetadataRetriever.OPTION_CLOSEST` y `MediaMetadataRetriever` persistente, erradicando el retraso de 2.5s de los Keyframes y respondiendo de inmediato a cada escena con `tween(180)`.
  - Eliminación total de fondos negros y parpadeos al abrir Now Playing o el Mini Reproductor mediante `seekTo` previo a `prepare()`, obturador transparente y placeholder de carátula (`placeholderTrack`) con fundido en `onRenderedFirstFrame()`.
  - Soporte de Video Canvas miniatura en el Mini Reproductor flotante configurable por el usuario desde Ajustes.
  - Sincronización milimétrica de velocidad de reproducción (0.50x a 2.00x) entre la música y el video de fondo.
  - Erradicación de la fuga de memoria en `AuraMediaPlaybackService` (`ResourcesImpl.mAppContext`) detectada por LeakCanary.
- [x] **Búsqueda Interactiva de Letras, Elección de Versiones y Recomendación de Lírica Oficial**:
  - Diálogo interactivo `SearchLyricsDialog` donde el usuario puede escribir y modificar el nombre de la canción y el artista libremente.
  - Algoritmo de priorización en `LyricsManager` que consulta endpoints canónicos (`/api/get`) y de búsqueda (`/api/search`) en LRCLIB para identificar la versión oficial.
  - Posicionamiento estricto de la Lírica Oficial en primera posición (#1) con insignia luminosa `⭐ OFICIAL / RECOMENDADA` y borde de neón.
  - Lista completa de opciones comunitarias con comparador de formato (Sincronizada vs Texto plano), duración y previsualización de versos.
  - Integración accesible tanto desde el botón de búsqueda en la tarjeta de Karaoke como desde el estado de canción sin letra.

- [x] **Integración Completa de FFmpeg Puro y Entorno Python con yt-dlp en el APK Final**:
  - `FFmpegNativeEngine`: Motor de procesamiento multimedia a nivel nativo/CLI sin wrappers obsoletos, con binario ejecutable `libffmpeg.so` real asegurado en `app/build.gradle.kts` (`provisionNativeDeps` + `native_ffmpeg_launcher.c` con puente NDK `dlopen`/`dlsym` hacia `libffmpegkit.so`/`libavcodec.so`/`libavfilter.so`) y paquete dinámico depurado (`libffmpeg.zip.so`) empaquetados en `jniLibs/` para todas las arquitecturas (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`), optimizado para extracción de audio, transcodificación a AAC/MP3 y fusión DASH `-c copy`.
  - **Bucle Infinito sin Cortes (Seamless Loop con Crossfade) & Efecto Boomerang / Ping-Pong (`reverse` + `concat`)**: Creación automatizada de loops de Video Canvas (≤ 20s) con selector interactivo entre fundido continuo mediante `xfade` y **Efecto Boomerang (`reverse` + `concat=n=2:v=1:a=0`)** en *Editar Canción*, *Video a Música* y *Descargar desde Enlace*, logrando repetición cíclica continua de ida y vuelta sin saltos en ExoPlayer.
  - **Optimización de Fotogramas Clave (Keyframes / GOP Corto a 30fps)**: Reestructuración de videos largos sincronizados con inserción de I-frames cada 1 segundo (GOP=30), eliminando audio residual y aplicando `-movflags +faststart` para saltos temporales instantáneos (0ms) en Now Playing y Mini Reproductor.
  - `YtDlpNativeEngine`: Runtime de CPython nativo (`libpython.so`) con entorno optimizado (`libpython.zip.so`) y QuickJS (`libqjs.so`) empaquetados en el APK, con copia base oficial en `assets/bin/yt-dlp` para funcionamiento inmediato offline.
  - `YtDlpAutoUpdater`: Sistema de actualización OTA en caliente que consulta GitHub Releases y descarga la versión más reciente del extractor directamente en `files/bin/yt-dlp` sin forzar al usuario a esperar una nueva versión del APK en Uptodown.
  - **Acelerador de Descarga por Bloques (Chunked Range Download)**: Erradicación del estrangulamiento de ~63 KB/s de Google Video mediante fragmentación de rangos HTTP en bloques de 2.5 MB, alcanzando velocidades de descarga de 10 a 40 MB/s.
  - **Resolución 480p Estricta por Defecto para Video Canvas**: Configuración y filtrado selectivo en `yt-dlp`, `InnerTube` e `Invidious` fijando por defecto la descarga de video a 480p (`height=480`) con vinculación automática garantizada al Video Canvas de fondo.
  - Tarjeta en Ajustes de Apariencia con información en vivo de FFmpeg y botón interactivo para actualizar yt-dlp en segundo plano.
- [x] **Transparencia Total y Gestor de Medios Almacenados en Ajustes (`StoredMediaSettingsTab`)**:
  - Nueva pestaña "Medios" en la pantalla de Ajustes con desglose exhaustivo de carátulas WebP y Videos Canvas MP4 persistidos en disco (`images/` y `videos/`).
  - Muestra la canción asociada, tamaño exacto del archivo (KB/MB) y botones individuales con confirmación para borrar la carátula o el video de cualquier pista, liberando espacio físico de inmediato.
- [x] **Miniaturas de Carátula en la Cola de Reproducción (NowPlayingQueueSheet)**:
  - Integración de carátulas en alta resolución (`ArtworkImage` de 42dp con bordes redondeados) para cada pista en la lista en espera, permitiendo identificar canciones al instante visualmente.
- [x] **Consistencia Geométrica y Pulido de Diseño en Letras / Karaoke**:
  - Homogeneización de dimensiones de `NowPlayingLyricsCard` con la carátula (`fillMaxWidth(0.86f).aspectRatio(1f)` con radio de 26dp), eliminando saltos o desalineaciones visuales al alternar entre carátula y letras.
  - Resaltado activo tipo píldora para la frase en reproducción y rediseño de insignias en `SearchLyricsDialog` para erradicar cualquier desbordamiento o colisión de texto.
- [x] **Persistencia Permanente de Configuración de Color y Material You**:
  - Almacenamiento y restauración automática en `SharedPreferences` del tema visual seleccionado (`AuraTheme`, incluyendo *Material You*), el estado de *Aura Dinámica de Carátula* y el modo de *Video Canvas*, garantizando que nunca se reinicien al cerrar y volver a entrar a la app.
- [x] **Portadas Personalizadas en Playlists y Collage Dinámico de 1 a 4 Canciones (`PlaylistCoverCollage`)**:
  - Soporte para elegir una imagen personalizada desde la galería (con compresión WebP sin pérdida en `images/` y migración Room `v2 -> v3`) al crear o editar cualquier Playlist.
  - Generador automático de collage adaptativo cuando la Playlist no tiene imagen propia: 1 foto (1 canción), collage de 2 fotos (2 canciones), collage de 3 fotos (3 canciones) y cuadrícula 2x2 de 4 fotos (4 o más canciones).
- [x] **Navegación a Vista de Canciones en Artistas y Álbumes**:
  - Al pulsar sobre un Artista o un Álbum en la Biblioteca, se abre la vista detallada con todas sus canciones listadas (igual que en las Playlists) en lugar de reproducir la primera pista de inmediato.
- [x] **Transición Fluida entre Videos sin Residuos y Reencuadre Inteligente 16:9 / 9:16 con FFmpeg**:
  - Erradicación definitiva de la persistencia del video anterior al terminar la canción o avanzar de pista en Now Playing y Mini Reproductor mediante aislamiento con claves `key(track.id, track.videoUri)`, bloque `update` en `AndroidView` y reseteo reactivo de primer fotograma.
  - Transición suave de fundido cruzado (*crossfade*) entre pistas sobre la carátula oficial de respaldo, garantizando cero retrasos o imágenes congeladas.
  - Detección de relación de aspecto de video (`probeVideoDimensions` / `onVideoSizeChanged`) para videos horizontales (16:9) vs verticales (9:16).
  - Generación de lienzo vertical cinemático 9:16 en FFmpeg (`createVerticalCanvasFromHorizontalVideo`) con video original centrado sin recortes de rostros y fondo ambiental difuminado (`boxblur=16:2`).
  - Adaptación visual en tiempo real en `BackgroundVideoPlayer` con `RESIZE_MODE_FIT` para videos horizontales en Fondo Completo, e indicador de aspecto en Ajustes > Medios.
- [x] **Limpieza Atómica de Buffer (Buffer Flushing) en C++20 y Media3**:
  - Purgado atómico de acumuladores y líneas de retardo (`flushDspBuffers`) en cambios de canción (`playTrack`), saltos de barra (`seekTo`), pausas (`pause`), fin de pista y cierre del reproductor (`release`).
  - Erradica de forma definitiva ruidos transitorios, "pops" o chasquidos digitales y colas de eco residuales de reverb de la pista previa.
  - Sincronización transparente con el ciclo de vida de Media3 mediante `onFlush()` en `NativeAudioProcessor`.
- [x] **Modularización y Partición de Archivos Gigantes**:
  - Reestructuración del núcleo C++20 (`auramusic_dsp.h`) en cabeceras modulares especializadas: `dsp_filters.h`, `dsp_spatial.h`, `dsp_crossfeed.h` y `dsp_reverb.h`.
  - Desacoplamiento de `MusicViewModel.kt` mediante el patrón de Coordinadores en `viewmodel/delegates/`: `LyricsCoordinator`, `IncomingMediaCoordinator` y `HeadphoneSettingsCoordinator`, reduciendo la complejidad ciclomática y manteniendo intacta la API pública.
- [x] **Blindaje Criptográfico de Seguridad y Aprovisionamiento Nativo Puro**:
  - Declaración obligatoria de los permisos `android.permission.INTERNET` y `ACCESS_NETWORK_STATE` en `AndroidManifest.xml`.
  - Protección criptográfica contra Ejecución Remota de Código (RCE) en `YtDlpAutoUpdater` mediante verificación de hash `SHA-256` contra `SHA2-256SUMS` oficial de GitHub Releases y validación de host.
  - Neutralización de vulnerabilidad Zip Slip (Path Traversal) con validación estricta de rutas canónicas en `FFmpegNativeEngine` y `YtDlpNativeEngine`.
  - Protección contra inyección de argumentos (`--`) en procesos CLI y sanitización de intents externos en `DebugMonitorActivity`.
  - Eliminación total de archivos `.sh` y migración del 100% de la lógica de aprovisionamiento y compilación nativa a `app/build.gradle.kts` (`provisionNativeDeps` y `ensureDebugKeystore`) y `app/src/main/cpp/` (`native_python_launcher.c`, `native_ffmpeg_launcher.c`, QuickJS C99 puro), aprovisionando CPython 3.11 real multi-ABI, FFmpeg nativo puro, QuickJS C99 y `yt-dlp` sin wrappers ni llamadas a `sh`.
- [x] **Compresión Ligera en FFmpeg: Pista de Audio Eliminada (`-an`) y Purga de Metadatos (`-map_metadata -1`)**:
  - Aplicación automática de `-an` en la tubería de Video Canvas de FFmpeg para descartar pistas de audio duplicadas, ahorrando de 5 a 20 MB por canción.
  - Purga de metadatos innecesarios (`-map_metadata -1`) en el contenedor MP4.
  - Implementación de `stripAudioAndMetadata` (`-c:v copy -an -map_metadata -1 -movflags +faststart`) como remux ultrarrápido y seguro de respaldo.
- [x] **Pantallas Independientes de Bienvenida, Inducción y Disclaimer de Almacenamiento (`OnboardingScreen`)**:
  - Flujo modular de bienvenida en 3 etapas con estética Dark Luxury Neo-Glass: Bienvenida y filosofía offline, resumen de lo que ofrecemos (C++20 DSP, Video Canvas, Karaoke LRCLIB, Video a Música) y aviso importante de almacenamiento.
  - Disclaimer transparente informando que los Video Canvas se almacenan en alta resolución en el teléfono y cómo gestionarlos desde Ajustes > Medios para liberar espacio en MB.
  - Persistencia con `pref_onboarding_completed`, navegación fluida en `MainActivity` sin barras inferiores intrusivas y botón de re-apertura en Ajustes.
- [x] **Encuadre Personalizable de Video de Fondo (Rellenar vs Adaptado), Descarga en Segundo Plano con Notificación Nativa y Pulido Visual**:
  - Elección libre entre **Fondo Completo • Rellenar (Recortar)** (`RESIZE_MODE_ZOOM`, 100% pantalla vertical sin divisiones centrales) y **Fondo Completo • Adaptado Horizontal** (`RESIZE_MODE_FIT`, cuadro 16:9 completo sin recortar caras) tanto en los diálogos de descarga/conversión/edición como en tiempo real desde *Now Playing*.
  - Servicio en primer plano `AuraDownloadService` (`foregroundServiceType="dataSync"`) que mantiene activas las descargas y el procesamiento FFmpeg al salir de la app, mostrando progreso en vivo en la barra de notificaciones de Android y avisando al terminar (reproduciendo la pista al tocar la notificación si el usuario estaba fuera de la app).
  - Eliminación total de la pantalla aislada redundante `EqualizerScreen.kt` y reemplazo del acceso en la cuadrícula de *Inicio* por la **Playlist más escuchada** del usuario (`totalPlays`).
  - Corrección de maquetación en *Ajustes > Medios* (`StoredMediaSettingsTab`), acotado de marquesinas con `clipToBounds()` y recorte automático de franjas negras (`letterbox` 4:3) en miniaturas de YouTube (`DeletterboxTransformation` y `removeHorizontalLetterboxBars`).
- [x] **Verificación y Actualización Transparente de Paquetes en Segundo Plano, Audio 16D Multi-Órbita y Clarificador de Voces HD en C++20**:
  - **Ciclo de Verificación al Iniciar (`YtDlpAutoUpdater`, `PackageUpdateState` & `PackageUpdateBanner`)**: Comprobación automática en segundo plano al abrir la app con notificación nativa en la barra de estado (*"Verificando paquetes necesarios..."*) y banner superior compacto no intrusivo.
  - **Descarga con Barra de Progreso, Staging Atómico y Reinicio Asistido**: Notificación nativa e indicador interno con barra de progreso en vivo (*"Descargando actualización de paquetes"*), validación SHA-256 sobre `yt-dlp.staged`, bloqueo temporal preventivo de descargas por `yt-dlp` hasta aplicar los cambios y botón rápido **"Actualizar y Reiniciar"** (o aplicación automática al salir y reabrir la app).
  - **Motor C++20 de Audio 16D Multi-Órbita (`EightDProcessor::set16DMode`)**: Doble órbita binaural contra-rotatoria en C++20 con división espectral (bajos en órbita interna y voces/agudos en órbita figura-8 con micro-retardo Haas), integrada con selector directo 8D / 16D libre de logotipos o animaciones decorativas.
  - **Motor C++20 Clarificador de Voces HD (`VocalClarityProcessor` en `dsp_filters.h`)**: Procesamiento Mid-Side en 64 bits con aislamiento del canal vocal central, atenuación de turbidez a 180 Hz y realce biquad de presencia (2.8 kHz) y articulación (5.5 kHz), ubicado estratégicamente en las pestañas *Ecualizador* y *Velocidad/Voz*.

---

## 🔊 Fase 7: Modo Bit-Perfect y Salida de Ultra-Baja Latencia (Siguiente Paso 🔄)

- [ ] **Integración con Google Oboe / AAudio**:
  - Modo exclusivo para saltarse el mezclador del sistema Android (*AudioFlinger*).
  - Reproducción directa hacia DACs USB externos en 24-bit/32-bit a 96 kHz o 192 kHz.
- [ ] **Decodificación Nativa de Formatos Especiales**:
  - Soporte de archivos DSD (.dsf / .dff), Monkey's Audio (.ape) y módulos chiptune (.mod, .xm).
- [ ] **Widgets de Pantalla de Inicio**:
  - Widgets interactivos con Material You y controles de reproducción directa.
