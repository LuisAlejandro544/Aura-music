# AGENTS.md - Manual de Operaciones para Agentes de Desarrollo 🛠️

Este documento define el protocolo de trabajo para los agentes de software y desarrolladores que operan sobre **Aura Music**, alineado con las 7 fases del ciclo real de ingeniería de software.

---

## 🎭 Roles y Fases de Desarrollo

### 1. El Arquitecto (Planificación y Diseño)
* **Objetivo**: Antes de escribir una sola línea de código, definir la estructura, entidades y flujo técnico.
* **Protocolo en Aura Music**:
  - Verificar que las nuevas funciones encajen en el flujo MVVM (Model - Room Database - Repository - Storage - ViewModel - Compose UI).
  - Diseñar pantallas modulares en `ui/screens/` evitando saturar interfaces.
  - El almacenamiento de datos de usuario se organiza exclusivamente en `Android/data/com.aistudio.musicplayer.aurasound/files/` en subcarpetas (`images/`, `songs/`, `lyrics/`, `metadata/`, `videos/`).
  - Asegurar compatibilidad arquitectónica con arquitecturas de 64 bits (`arm64-v8a`, `x86_64`) y 32 bits (`armeabi-v7a`, `x86`).

### 2. El Constructor (Generación de Código)
* **Objetivo**: Escribir código limpio, tipado, modular y con manejo exhaustivo de excepciones.
* **Protocolo en Aura Music**:
  - Utilizar Kotlin con Jetpack Compose y C++20 para código nativo DSP de 10 bandas.
  - El ecualizador paramétrico debe integrarse en la hoja modal unificada de efectos de audio accesible tanto desde el Mini Reproductor como desde Now Playing, **sin ocupar un apartado de pantalla completa aparte** que interrumpa la navegación del usuario.
  - La compresión de carátulas a WebP debe realizarse en un hilo secundario sin pérdida de calidad.
  - Toda canción sin carátula debe generarse proceduralmente mediante Canvas/matemáticas, evitando imágenes fijas genéricas.
  - Comentar cada archivo explicando la lógica que contiene y su rol arquitectónico.
  - Mantener los tamaños de archivo por debajo de 500 líneas cuando sea posible.

### 3. El Detective (Debugging)
* **Objetivo**: Diagnosticar y resolver errores de forma metódica con razonamiento paso a paso (*Chain of Thought*).
* **Protocolo en Aura Music**:
  - Formular 3 hipótesis antes de tocar código.
  - Evitar fondos translúcidos o contenedores de `AnimatedVisibility` con fondos negros residuales que provoquen parpadeos o capas negras al cerrar el reproductor.
  - En la modulación rápida de parámetros de reproducción (velocidad y tono/voz), aplicar *throttling* con corrutinas y mecanismos de recuperación en `onPlayerError` para que ExoPlayer nunca detenga la reproducción de forma imprevista.
  - Apoyarse en la suite **Aura Monitor** (`DebugMonitorActivity`) y **LeakCanary** para rastrear en caliente excepciones no controladas, warnings de memoria y fugas en la JVM.

### 4. El Crítico (Code Review)
* **Objetivo**: Inspeccionar seguridad, rendimiento y buenas prácticas como si fuera un Pull Request profesional.
* **Protocolo en Aura Music**:
  - Verificar que no se bloquee el hilo principal (`Dispatchers.Main`) y que la compresión WebP y E/S de archivos ocurra en `Dispatchers.IO`.
  - Auditar que no existan accesos globales al disco que violen la privacidad del usuario.
  - Confirmar que ningún componente interactivo tenga un área táctil menor a 48.dp.
  - Garantizar que la app compile de manera autónoma sin requerir archivos `.env`.

### 5. El Optimizador (Refactoring y Rendimiento)
* **Objetivo**: Mejorar velocidad, legibilidad y consumo de batería sin alterar el comportamiento observable.
* **Protocolo en Aura Music**:
  - Usar `remember` y `derivedStateOf` para evitar recomposiciones innecesarias en Compose.
  - En C++20, utilizar `std::span` para pasar buffers PCM de audio por referencia sin asignaciones dinámicas de memoria.
  - Usar compresión WebP Lossless para optimizar drásticamente el espacio de almacenamiento y tiempos de renderizado con Coil.

### 6. El Escudo (Testing y Calidad)
* **Objetivo**: Garantizar estabilidad mediante pruebas unitarias y de integración.
* **Protocolo en Aura Music**:
  - Probar flujos locales con Robolectric (`gradle :app:testDebugUnitTest`).
  - Cubrir casos límite: archivos de audio corruptos, URIs no disponibles, listas vacías y manipulación rápida de parámetros de audio.

### 7. El Narrador (Documentación)
* **Objetivo**: Documentar claramente cada cambio y módulo técnico.
* **Protocolo en Aura Music**:
  - Mantener sincronizados `README.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md` y `AGENTS.md`.
  - Asegurar que la información técnica y documentación esté siempre redactada en **español**.

---

## 📋 Lista de Verificación Pre-Entrega (Checklist Obligatorio)

- [x] ¿El proyecto compila sin errores (`compile_applet`)?
- [x] ¿`minSdk` se mantiene en 26 (Android 8.0)?
- [x] ¿Se eliminó completamente la necesidad de archivos `.env`?
- [x] ¿El estándar de C++ está fijado en C++20 con soporte multi-arquitectura y empaquetado del .so en el APK final?
- [x] ¿El ecualizador de 10 bandas funciona y está integrado en modal sin un apartado de pantalla completa separado?
- [x] ¿El motor de Audio 8D Espacial en C++20 está implementado y configurable desde Now Playing?
- [x] ¿El temporizador de apagado personalizable con atenuación de 10s (fade-out) funciona correctamente?
- [x] ¿La modulación de velocidad (0.5x-2.0x) y tono musical cuenta con protección contra pausas accidentales?
- [x] ¿Se eliminó el fondo negro residual y el parpadeo al cerrar o minimizar la pantalla de reproducción?
- [x] ¿Aura Monitor está integrado como aplicación independiente con su propio icono en el cajón de aplicaciones (con taskAffinity propio y launchMode singleTask) y accesible también desde los ajustes?
- [x] ¿LeakCanary está añadido y funcional en dependencias de depuración?
- [x] ¿El almacenamiento estructurado (`images/`, `songs/`, `lyrics/`, `metadata/`) está activo?
- [x] ¿Las carátulas se procesan como WebP sin pérdida en segundo plano y las faltantes se generan proceduralmente?
- [x] ¿El Video Canvas de fondo soporta loops cortos (≤20s) con bucle infinito sin cortes (Seamless Loop con crossfade xfade en FFmpeg) y Efecto Boomerang / Ping-Pong (reverse + concat en FFmpeg), videos largos sincronizados con fotogramas clave (GOP corto a 30fps) para saltos instantáneos (0ms) y armonización cromática sin interferencias de carátula?
- [x] ¿El video de fondo soporta los 3 modos (Fondo Completo con carátula al frente, Lienzo en carátula y Solo Carátula) seleccionables por el usuario mediante diálogo modal?
- [x] ¿El corazón en Now Playing se refleja inmediatamente en rojo y se sincroniza reactivamente con Room y ExoPlayer?
- [x] ¿La sección 'Populares en tu biblioteca' de la pantalla de inicio permanece fija sin renombrarse a 'Tus favoritos' ni ocultar canciones al marcar favoritos?
- [x] ¿El evento TRIM_MEMORY_UI_HIDDEN (nivel 20) está clasificado como información normal en lugar de advertencia para evitar falsas alarmas de memoria?
- [x] ¿La función 'Video a Música' extrae el audio a .m4a, genera carátula en WebP y vincula el Video Canvas automáticamente?
- [x] ¿Se pueden modificar los metadatos de las canciones (título, artista, álbum)?
- [x] ¿La pestaña 'Auriculares' está implementada con apartados de Acústica DSP, Seguridad y Gestos?
- [x] ¿El filtro Crossfeed (Bauer / Chu Moy en C++20) se activa exclusivamente al detectar auriculares conectados?
- [x] ¿El Balance Estéreo Fino (L/R) se puede ajustar en tiempo real en Now Playing y hoja modal?
- [x] ¿La protección contra desconexiones ("Becoming Noisy" Guard) y Fade-In suave están activos?
- [x] ¿La memoria de volumen dedicada y el control avanzado por botones de audífonos son configurables?
- [x] ¿La notificación nativa del reproductor de Android (System Media Controls con MediaSessionService, ForwardingPlayer completo con Anterior/Play/Siguiente y MediaStyle retrocompatible) está integrada y funcional?
- [x] ¿Las 10 bandas del ecualizador cuentan con etiquetas acústicas intuitivas (Subgraves, Bajos, Graves, Voces, Claridad, Brillo, Aire) junto a los Hz?
- [x] ¿La carátula y Video Canvas se muestran despejados al 100% sin cápsulas superpuestas y con escala fija 1.0f para máxima nitidez, manteniendo el selector de video en la barra superior?
- [x] ¿La armonización cromática dinámica en Video Canvas es instantánea y exacta (con OPTION_CLOSEST y sin retraso de 2.5s) y se eliminaron por completo las pantallas negras y parpadeos al abrir Now Playing o el Mini Reproductor?
- [x] ¿Las letras sincronizadas (.LRC) estilo Karaoke con descarga automática libre desde LRCLIB, auto-scroll y salto táctil están integradas y persistidas en `lyrics/`?
- [x] ¿El temporizador de apagado ejecuta su atenuación suave y pausa en el hilo principal (Main Thread) previniendo excepciones de ExoPlayer?
- [x] ¿La función de descarga desde TikTok y enlaces web extrae la música de cualquier duración, genera carátula en WebP y vincula el Video Canvas de fondo automáticamente?
- [x] ¿La descarga desde YouTube / Video Web con arquitectura resiliente de 3 niveles (InnerTube multi-cliente sin fricción + Bypass Invidious + Headless WebView reparado sobre m.youtube.com) y selector interactivo está integrada y operativa?
- [x] ¿La opción de paleta dinámica 'Material You' y las configuraciones de color/tema están disponibles y se guardan permanentemente al salir y volver a entrar a la app?
- [x] ¿Las Playlists permiten asignar una imagen personalizada en WebP o muestran automáticamente un collage dinámico de 1, 2, 3 o 4 fotos según las canciones añadidas?
- [x] ¿Al tocar un Artista o un Álbum en la biblioteca se abre su lista detallada de canciones (igual que en las Playlists) en lugar de reproducir inmediatamente la primera canción?
- [x] ¿La escala tipográfica está fijada a un valor cómodo (fontScale = 1.0f) para evitar que los ajustes de texto del sistema desborden la interfaz?
- [x] ¿La reproducción continua automática al finalizar una pista pasa fluidamente a la siguiente canción de la biblioteca sin detenerse?
- [x] ¿El fundido de transición (fade-out) y entrada progresiva (fade-in) restauran suavemente el volumen original sin quedarse atrapados en volumen bajo?
- [x] ¿La recepción inteligente 'Abrir con...' y 'Compartir con...' (puente para SnapTube, gestores de descarga, mensajería y navegadores) discrimina con precisión entre audio, video y enlaces web, copiando a 'songs/' y reproduciendo al instante?
- [x] ¿La opción con interruptor para eliminar inteligentemente los silencios al inicio y al final está disponible antes de cada importación (archivos, carpetas, Video a Música, descargas de TikTok/YouTube y 'Abrir con...')?
- [x] ¿El repetidor de segmento A-B está integrado como barra compacta junto al progreso en Now Playing y con ajuste fino (±1s) en la hoja modal de efectos?
- [x] ¿Se eliminó el texto redundante 'REPRODUCIENDO AURA / álbum' de la barra superior de Now Playing para liberar espacio visual?
- [x] ¿Los títulos largos se desplazan automáticamente con marquesina fluida (basicMarquee) en Now Playing, Mini Reproductor y en la canción activa dentro de listas y cola?
- [x] ¿El modal de descarga de enlaces (YouTube / TikTok) es completamente deslizable verticalmente para no cortar botones en pantallas compactas?
- [x] ¿El Reverb se procesa 100% en C++20 (`ReverbProcessor`) sin silencios ni demoras al activarse o desactivarse?
- [x] ¿Tanto la Velocidad de la Música como la Velocidad de Voz / Tono (`Pitch`) llegan hasta 2.0x e incluyen botones rápidos con `2.0x`?
- [x] ¿Las descargas de YouTube por WebView garantizan carátula oficial en WebP sin pérdida mediante descarga en cascada (maxresdefault, hqdefault, mqdefault, i.ytimg.com y fotograma clave)?
- [x] ¿El importador de archivos de letras (.lrc y .txt) desde el celular y la auto-detección/vinculación de letras locales (.lrc/.txt hermanos o tags) están integrados y funcionales?
- [x] ¿La búsqueda interactiva de letras con edición libre de título/artista, búsqueda multidimensional (`track_name` y `q` sin restricción de duración local), tarjeta vacía sin desbordamientos y recomendación oficial canónica (#1) está integrada y operativa?
- [x] ¿Aura Monitor cuenta con navegación por pestañas (*Incidentes & Logs* y *Rendimiento & Hilos*), desglose de RAM segmentada (Java/Native C++/Gráficos/PSS), carga de CPU e Inspector Quirúrgico de Hilos con trazas de pila completas?
- [x] ¿El motor FFmpeg puro sin wrapper (`FFmpegNativeEngine`), el entorno Python nativo con QuickJS (`YtDlpNativeEngine`) y la copia base de `yt-dlp` están integrados en el APK final para todas las arquitecturas (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) con actualización en caliente OTA (`YtDlpAutoUpdater`) sin inflar el APK con encoders pesados innecesarios?
- [x] ¿El pipeline de GitHub Actions (`build-debug-apk.yml`) implementa caché inteligente de dependencias nativas (`actions/cache@v4`) con compilación y ensamblaje autónomo NDK mediante la tarea `:app:provisionNativeDeps` de `app/build.gradle.kts` para builds limpios y ultrarrápidos?
- [x] ¿El acelerador de descarga fragmentada por bloques HTTP Range (Chunked Range Download) elimina la limitación artificial de ~63 KB/s de YouTube y la resolución de Video Canvas está fijada por defecto en 480p con vinculación garantizada?
- [x] ¿El archivo `.gitignore` excluye permanentemente binarios `.so`, `.zip.so` y `yt-dlp`, y se provee el workflow manual de purga (`purge-native-binaries-history.yml`) con `git-filter-repo` para limpiar el historial remoto?
- [x] ¿La pestaña 'Medios' en Ajustes (`StoredMediaSettingsTab`) permite inspeccionar minuciosamente las carátulas WebP y videos MP4 almacenados, ver a qué canción pertenecen, cuánto espacio consumen en KB/MB y borrarlos con confirmación para liberar almacenamiento?
- [x] ¿La cola de reproducción ("Up Next" en `NowPlayingQueueSheet`) muestra la miniatura de carátula oficial en alta fidelidad junto a cada pista para su reconocimiento visual inmediato?
- [x] ¿La tarjeta de letras y Karaoke (`NowPlayingLyricsCard`) sincroniza dimensiones exactas con la carátula evitando saltos visuales y el diálogo de búsqueda (`SearchLyricsDialog`) previene colisiones y desbordamientos?
- [x] ¿El video de la canción anterior se libera y desmonta atómicamente al terminar la pista o avanzar de canción en Now Playing y Mini Reproductor con fundido suave (*crossfade*) sobre la carátula oficial?
- [x] ¿El encuadre de videos horizontales (16:9) y verticales (9:16) en Fondo Completo previene el recorte de rostros mediante generación de lienzo 9:16 en FFmpeg y modo `RESIZE_MODE_FIT` en tiempo real?
- [x] ¿La limpieza de buffer (*Buffer Flushing*) purga a cero los acumuladores IIR en C++20, filtros de graves, líneas de retardo de Crossfeed y colas de Reverb en cambios de pista, seekTo, pausas y liberaciones sin chasquidos?
- [x] ¿El motor DSP nativo C++20 (`auramusic_dsp.h`) está modularizado en submódulos especializados (`dsp_filters.h`, `dsp_spatial.h`, `dsp_crossfeed.h`, `dsp_reverb.h`) y `MusicViewModel.kt` desacoplado con coordinadores (`LyricsCoordinator`, `IncomingMediaCoordinator`, `HeadphoneSettingsCoordinator`) manteniendo el 100% de compatibilidad pública?
- [x] ¿`AuraAudioPlayer.kt` (desacoplado en `PlayerQueueController`, `AudioFadeController`, `ABLoopController`, `MediaSessionBridge`), `OnlineVideoAudioImporter.kt` (desacoplado en `ChunkedStreamDownloader`, `TikTokMediaResolver`, `MediaAssetProcessor`) y `MusicRepository.kt` (desacoplado en `PlaylistRepository` y `SafTrackImporter`) están modularizados manteniendo todos los archivos bajo 500 líneas y con 100% de compatibilidad pública?
- [x] ¿El permiso `INTERNET` y `ACCESS_NETWORK_STATE` están declarados en `AndroidManifest.xml` desbloqueando todas las funciones de red?
- [x] ¿La actualización OTA de `yt-dlp` (`YtDlpAutoUpdater`) cuenta con blindaje criptográfico SHA-256 (`SHA2-256SUMS`) y validación estricta de host previniendo cualquier ejecución remota de código (RCE)?
- [x] ¿La vulnerabilidad Zip Slip (Path Traversal) está neutralizada mediante validación canónica de rutas (`canonicalFile.toPath().startsWith(...)`) en `FFmpegNativeEngine` y `YtDlpNativeEngine`?
- [x] ¿Las actividades exportadas (`DebugMonitorActivity`) y la invocación de procesos CLI (`YtDlpNativeEngine`) están protegidas contra inyección de argumentos (`--`) y manipulación externa?
- [x] ¿Se eliminaron por completo los scripts `.sh` y toda la lógica de aprovisionamiento y compilación nativa de `yt-dlp`, QuickJS C99 puro, CPython 3.11 real y FFmpeg nativo puro está integrada en `app/build.gradle.kts` (`provisionNativeDeps`) y `app/src/main/cpp/` sin stubs de 500 KB ni wrappers ni llamadas a `sh`?
- [x] ¿La optimización con FFmpeg para Video Canvas elimina la pista de audio redundante (-an) y purga metadatos innecesarios (-map_metadata -1) reduciendo el tamaño en disco hasta un 85% sin pérdida de nitidez?
- [x] ¿Las pantallas modulares e independientes de bienvenida (Onboarding) presentan la propuesta de valor de Aura Music, detallan las funciones clave e integran un disclaimer transparente sobre el almacenamiento de Video Canvas con re-acceso desde Ajustes?
- [x] ¿El usuario puede elegir libremente entre el encuadre de video 'Fondo Completo (Rellenar / Recortar)' (`RESIZE_MODE_ZOOM`) y 'Fondo Completo (Adaptado Horizontal)' (`RESIZE_MODE_FIT`) tanto al descargar/importar/editar como en tiempo real desde Now Playing sin divisiones horizontales a mitad de pantalla?
- [x] ¿Se eliminó por completo la pantalla aislada del ecualizador y se reemplazó su tarjeta en Inicio por el acceso directo a la Playlist más escuchada del usuario?
- [x] ¿Las descargas de video/música se ejecutan en segundo plano mediante `AuraDownloadService` con notificación nativa en vivo en la barra de estado, avisando al terminar si el usuario está fuera de la app y reproduciendo la pista al tocar la notificación?
- [x] ¿Se corrigió la maquetación vertical de las tarjetas en Ajustes > Medios, el recorte de marquesinas (`clipToBounds`) y las franjas negras de las miniaturas 4:3 de YouTube?
- [x] ¿Al entrar a la app se verifican y actualizan los paquetes nativos (`yt-dlp`) en segundo plano con notificación nativa en la barra de estado, barra de progreso en vivo, banner no intrusivo en la app, bloqueo temporal preventivo de descargas por `yt-dlp` y botón de reinicio rápido para aplicar los paquetes?
- [x] ¿El motor de Audio 16D Multi-Órbita en C++20 (`EightDProcessor::set16DMode`) y el Clarificador de Voces HD en C++20 (`VocalClarityProcessor` Mid-Side) están integrados estratégicamente sin logotipos ni animaciones decorativas?
- [x] ¿El Mixtape Maker / Fusión de Canciones con Crossfade Continuo en FFmpeg (`MixtapeEngine`), carátula collage WebP sin pérdida, sincronización reactiva de carátulas y Video Canvas por capítulos en tiempo real, y concatenación offset de letras Karaoke (.LRC) están integrados y operativos desde las Playlists?
- [x] ¿Se completó el desarrollo modular de `MusicViewModel.kt`, `MainActivity.kt`, `DownloadFromLinkDialog.kt` y `StoredMediaSettingsTab.kt` manteniendo todos los archivos bajo 500 líneas con 100% de compatibilidad pública?
- [x] ¿Se mitigó la Omisión Silenciosa de SHA-256 en OTA, la desactivación TLS/SSL en yt-dlp, las colisiones de nombres/extensiones en SAF y la inyección de opciones en FFmpeg?
- [x] ¿Se erradicó el bucle de re-descarga infinita de yt-dlp (`forceDownload`), alcanzando el progreso al 100% verificado en la notificación nativa, descartándola limpiamente ante fallos de red, aplicando paquetes en caliente y desbloqueando el diálogo con fallback instantáneo a InnerTube?
- [x] ¿Se solucionó el error de verificación de certificados SSL/TLS en Android 14+ (`CERTIFICATE_VERIFY_FAILED: unable to get local issuer certificate`) mediante consolidación automática del almacén CA de Conscrypt APEX (`/apex/com.android.conscrypt/cacerts`), KeyStore PEM y auto-recuperación con reintento resiliente en `YtDlpNativeEngine`?
- [x] ¿La Normalización de Volumen Inteligente (Loudness Normalizer Spotify / EBU R128) en C++20 (`VolumeNormalizerProcessor`) nivela la energía entre canciones con modos Sutil (-18 LUFS), Estándar (-14 LUFS) y Alto (-11 LUFS) sin distorsión?
- [x] ¿El Automix Inteligente DJ con Curva de Ecualización en X (`DjAutomixFilter`) atenúa subgraves (<120 Hz) durante la mezcla y detecta el outro acústico (<0.07f) para transiciones fluidas de discoteca?
- [x] ¿El Modo Karaoke a Pantalla Completa Inmersivo (`FullScreenLyricsScreen`) proporciona una experiencia visual tipo Spotify / Apple Music Sing con resaltado neón, escala dinámica, auto-scroll interactivo y barra de transporte flotante?
- [x] ¿El usuario puede seleccionar libremente entre los diseños del reproductor (Modo Clásico con carátula 1:1 vs Modo Cinemático Canvas estilo Spotify con video de fondo completo, minicarátula lateral, frase lírica flotante y controles en el tercio inferior, o selección Automática Inteligente) con persistencia en preferencias?
- [x] ¿El apartado antes llamado "Apariencia" ahora se denomina "Ajustes" y navega hacia menús independientes a pantalla completa (`SettingsSubMenuScreen`) en lugar de modales emergentes al tocar cada categoría (incluyendo "Diseño del Reproductor")?
- [x] ¿El Modo Bit-Perfect 1:1 y salida de ultra-baja latencia con Google AAudio (`dsp_bitperfect.h` y `libaaudio.so`) están integrados en C++20 y son configurables desde Ajustes > Auriculares & Acústica DSP con telemetría en vivo en `AudioSpecsDialog`?
- [x] ¿La decodificación nativa de formatos especiales (DSD `.dsf`/`.dff`, Monkey's Audio `.ape`, WavPack `.wv`, ALAC/AIFF y módulos Chiptune `.mod`/`.xm`/`.it`/`.s3m`) está operativa en `SpecialAudioFormatDecoder`, `AudioMetadataParser` y `SafTrackImporter`?
- [x] ¿El Widget interactivo de pantalla de inicio (`AuraMusicWidgetProvider`) con controles directos de reproducción, carátula/arte procedural e insignia Bit-Perfect, junto con el descriptor de Android Auto (`automotive_app_desc.xml`), están integrados y sincronizados?
- [x] ¿El canal de compilación APK Beta (`Aura Beta`, paquete `com.auramusic.beta`, codename `Nebula`, versión `v0.1.0-beta.1a`, código `NEBULA-00101A` / `100101`) elimina Aura Monitor y LeakCanary manteniendo el actualizador OTA de `yt-dlp`, firma desde GitHub Secrets, compila sin caché en `.github/workflows/build-beta-apk.yml`, genera 3 APKs móviles (`arm64-v8a`, `armeabi-v7a` y `universal` sin arquitecturas de PC) y publica en Pre-Releases con `chanelog-beta.md` solo cuando no es manual?
- [x] ¿El actualizador automático de versiones APK (`AppReleaseUpdater`, `AppUpdateDialog` y `AppReleaseUpdateSettingsCard`) compara semánticamente tags dinámicos (`-beta.1a`, `-beta.2d`, etc.), selecciona automáticamente el APK de la arquitectura del móvil (`arm64-v8a`, `armeabi-v7a` o `universal`), verifica `SHA256SUMS.txt`, muestra las notas de `chanelog-beta.md` e instala con `FileProvider` sin perder datos?
- [x] ¿Los archivos Markdown (`README.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md`, `AGENTS.md` y `chanelog-beta.md`) están actualizados y en español?

