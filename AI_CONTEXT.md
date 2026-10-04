# AI_CONTEXT.md - Contexto de Inteligencia Artificial 🤖

Este archivo define el contexto de diseño, directrices técnicas y restricciones inviolables para cualquier modelo de lenguaje o agente de IA que interactúe, modifique o extienda la base de código de **Aura Music**.

---

## 🎯 Propósito del Proyecto
**Aura Music** es un reproductor de audio local fuera de línea para Android con una experiencia estética inspirada en Spotify, pero con diseño moderno, colores vibrantes de neón, fondos 100% opacos, ecualización paramétrica de **10 bandas impulsada por C++20**, compresión WebP sin pérdida, almacenamiento de datos organizado y una suite de diagnóstico propia (**Aura Monitor**).

---

## 🔒 Reglas Técnicas Inviolables

1. **Requisitos de Sistema**:
   - `minSdk = 26` (Android 8.0 Oreo). No bajar esta versión bajo ninguna circunstancia.
   - `targetSdk = 36` y `compileSdk = 36`.

2. **Sin Dependencia de Archivos `.env`**:
   - Este proyecto **NO DEBE requerir `.env`** ni el plugin de secretos para compilar. Debe mantenerse 100% autónomo y reproducible en cualquier entorno.

3. **Privacidad y Modelo de Acceso a Archivos**:
   - **PROHIBIDO** el escaneo masivo automático del almacenamiento del teléfono (`READ_EXTERNAL_STORAGE` o rastreo ciego del disco).
   - Todo archivo debe ser importado **explícitamente por el usuario** mediante el *Storage Access Framework* (SAF) con `OpenMultipleDocuments` u `OpenDocumentTree`, persistiendo permisos de lectura con `takePersistableUriPermission`.

4. **Almacenamiento Estructurado**:
   - El almacenamiento de datos de la app se organiza en `Android/data/com.aistudio.musicplayer.aurasound/files/`:
     - `images/`: Carátulas comprimidas en WebP sin pérdida de calidad.
     - `songs/`: Canciones locales y demos.
     - `lyrics/`: Letras de canciones sincronizadas (.lrc).
     - `metadata/`: Archivos JSON con información técnica y descriptiva de las pistas.
     - `videos/`: Videos de fondo (.mp4/.webm) y loops de Canvas vinculados.
   - Los registros de diagnóstico del monitor se guardan de forma atómica en `filesDir/aura_debug_logs.json`.

5. **Carátulas Procedurales & WebP**:
   - Las canciones sin carátula deben usar el generador procedural vectorial, no imágenes genéricas estáticas o de IA fijas.
   - Toda compresión de imágenes debe realizarse en hilos de fondo (`Dispatchers.IO`) en formato WebP con calidad máxima.

6. **Motor de Audio y C++20**:
   - Se debe utilizar **Jetpack Media3 (ExoPlayer)** (`androidx.media3:media3-*`).
   - El ecualizador paramétrico cuenta con **10 bandas ISO** y limitador anti-clipping en **ISO C++20** con filtros biquad de doble precisión.
   - El motor de **Audio 8D Espacial** está integrado en **ISO C++20** mediante paneo orbital continuo de potencia constante, filtro de sombra de cabeza y reverberación binaural para auriculares.
   - El ecualizador **NO DEBE TENER UN APARTADO APARTE DE PANTALLA COMPLETA**. Se integra y despliega como una hoja modal unificada (`AudioEffectsBottomSheet`) accesible desde el Mini Reproductor y Now Playing con un solo toque.
   - Los cambios de velocidad y tono (*playback parameters*) deben contar con amortiguación (*throttling* con corrutinas) para no saturar ExoPlayer ni pausar la canción accidentalmente.
   - La finalización de pista debe avanzar de forma continua a la siguiente canción, y las transiciones con fundido (*fade-out / fade-in*) deben reestablecer progresivamente el 100% del volumen original sin interrupciones.

7. **Diseño de Interfaz (Jetpack Compose & M3)**:
   - Fondos 100% opacos OLED: evitar transparencias que permitan que las listas o cabeceras se filtren por detrás del reproductor o mini reproductor.
   - La animación de salida de Now Playing hacia abajo debe realizarse sin superposiciones de capas negras residuales (`slideOutVertically` limpio sobre fondo nativo).
   - Todos los elementos interactivos deben cumplir con un tamaño mínimo de toque de **48.dp**.
   - Modularidad en pantallas: `Home`, `Library`, `Import`, `NowPlaying`, `Settings`, `PlaylistDetail`.
   - **Soporte Material You**: Permitir seleccionar tema dinámico Material You en Android 12+ (API 31+) armonizado con el wallpaper del sistema sin perder las superficies oscuras puras OLED.
   - **Protección Tipográfica Fija**: La escala tipográfica del sistema se fija mediante `LocalDensity` con `fontScale = 1.0f` para garantizar que la configuración global de tamaño de texto del usuario en Android no altere ni rompa la composición visual ni corte títulos o etiquetas en la app.

8. **Suite de Diagnóstico Propia & LeakCanary**:
   - La actividad `DebugMonitorActivity` ("Aura Monitor") debe mantenerse accesible desde el cajón de aplicaciones con su propio icono independiente.
   - Debe interceptar excepciones no controladas a nivel de proceso (`Thread.setDefaultUncaughtExceptionHandler`) y registrar datos del teléfono (modelo, CPU ABI, RAM, almacenamiento, versión de Android) junto con el stacktrace en crudo.
   - LeakCanary debe estar configurado en `debugImplementation` para auditar fugas de memoria en la JVM.

9. **Video de Fondo Multifuncional (Fondo Completo, Lienzo o Desactivado)**:
   - Soporte para asociar videos a pistas individuales. Los videos cortos (≤ 10s - 20s) se reproducen en bucle continuo (*Canvas Loop*); los videos largos se sincronizan temporalmente con el audio y los saltos de búsqueda (*Video Sync*). Permite forzar el modo (Loop o Sync) en `EditTrackDialog`.
   - **3 Modos de Visualización Seleccionables**:
     - `FULLSCREEN_BACKGROUND`: El video se reproduce en todo el fondo de pantalla completa con un velo oscuro/gradiente para garantizar contraste y legibilidad, con la carátula flotando al frente.
     - `CARD_CANVAS`: El video se reproduce dentro del marco central de la carátula (1:1).
     - `OFF`: Desactivado; solo se muestra la carátula estática.
   - **Armonización Cromática Sin Interferencia**: Cuando el video está activo, el resplandor ambiental (*ambient aura*), visualizador y acentos se extraen de un fotograma clave del video en lugar de la carátula, garantizando que el color de la carátula estática no choque con la imagen en movimiento del video.
   - El reproductor de video de fondo opera con `volume = 0.0f` para no contaminar el procesador PCM ni el motor DSP C++20 de audio principal.
   - **Sincronización Reactiva de Favoritos**: El corazón en Now Playing refleja instantáneamente el estado de `isFavorite` (rojo al estar marcado) sincronizándose con Room y ExoPlayer. La lista de inicio "Populares en tu biblioteca" permanece estable y nunca oculta canciones al marcar favoritos.

10. **Modo Video a Música (Extracción 3 en 1)**:
    - Permite a los usuarios móviles sin PC convertir videos de su galería en canciones locales.
    - Se realiza mediante demuxing directo de audio sin recodificación (*Direct Stream Demuxing*) con `MediaExtractor` y `MediaMuxer` a `.m4a` en `songs/`, garantizando velocidad instantánea (1-2s) y cero pérdida acústica.
    - Captura automática de fotograma de video en alta definición a WebP sin pérdida en `images/` como carátula de álbum.
    - Vinculación automática del Video Canvas sincronizado y reproducción inmediata tras la conversión.

11. **Descarga Directa desde TikTok, YouTube y Enlaces Web**:
    - Permite a los usuarios descargar cualquier canción completa, parodia o audio de cualquier duración pegando un enlace de TikTok, YouTube o URL web.
    - **Arquitectura de Extracción Resiliente de 3 Niveles (InnerTube + Invidious Bypass + WebView Móvil)**:
      - *Motor InnerTube*: API nativa directa de YouTube mediante clientes de baja fricción (`ANDROID_VR` y `VISIONOS`). 100% gratuita, ultrarrápida (<300ms) y libre de restricciones de inicio de sesión (`LOGIN_REQUIRED`) o firmas cifradas (`n-sig`).
      - *Bypass de Respaldo Invidious*: Instancias públicas libres para resolución inmediata de canciones oficiales con restricciones de derechos estrictas.
      - *Motor WebView Reparado*: Navegador efímero en segundo plano cargado sobre `m.youtube.com` (sin bloqueos de inserción ni error 150) con timeout de 22s e intercepción del stream en memoria.
      - *Selector Interactivo*: Permite al usuario alternar entre InnerTube y WebView en el diálogo con auto-fallback cruzado de 3 capas.
    - Almacenamiento organizado: audio `.m4a`/`.mp3` en `songs/`, carátula oficial en WebP sin pérdida en `images/`, y video vinculado en `videos/` para reproducir el Video Canvas de fondo.
    - Interfaz adaptativa: Degradados visuales reactivos (Cyan/Magenta para TikTok, Rojo Carmesí/Naranja para YouTube/Web) y reproducción inmediata en Now Playing tras descargar.

12. **Suite de Auriculares y Ajustes Acústicos**:
    - **Filtro Crossfeed en C++20**: Algoritmo Bauer / Chu Moy que se activa **exclusivamente** cuando hay auriculares conectados (`isHeadphoneConnected`). Debe permanecer en reposo cuando se reproduzca por los altavoces del teléfono.
    - **Balance Estéreo Fino L/R**: Ajustable en tiempo real tanto en la pantalla de *Ajustes* como directamente en la pantalla completa *Now Playing* y en la hoja modal de efectos con botón de centrado.
    - **Protección Becoming Noisy y Fade-In**: Pausa inmediata ante desconexión de audífonos (cable o Bluetooth) y aumento progresivo de volumen en reanudación (~1s) para cuidar la salud auditiva.
    - **Controles de Auriculares Personalizables**: Manejo configurable de 1 clic, 2 clics, 3 clics y pulsación prolongada (hold) con mapeo flexible.

13. **Notificación Nativa del Sistema y MediaSessionService (Media3)**:
    - La reproducción en segundo plano debe apoyarse en `AuraMediaPlaybackService` extendiendo `MediaSessionService`.
    - Proporciona el controlador multimedia del sistema (System Media Controls) en Android 13, 14, 15+ (con carátula HD y barra ondulada de progreso) y compatibilidad retroactiva limpia en Android 8.0 a 12 (`MediaStyle`).
    - El canal de notificación debe ser silencioso (`IMPORTANCE_LOW`) para evitar pitidos en cada cambio de canción.
    - En Android 13+ (API 33+) se debe solicitar el permiso en tiempo de ejecución `POST_NOTIFICATIONS`.

14. **Carátula Limpia y Estática, Armonización Dinámica de Video Canvas y Mini Reproductor Tintado**:
    - La carátula central y el Video Canvas se muestran con visibilidad completa del 100% sin cápsulas superpuestas que obstruyan la ilustración. Su escala se mantiene fija en 1.0f para garantizar máxima nitidez sin movimientos no deseados por la música.
    - Durante la reproducción de Video Canvas, se muestrean fotogramas del video en intervalos de ~2.5 segundos adaptando de forma suave y continua (`tween(1200)`) los tonos del halo lumínico y visualizador según las escenas del video.
    - El Mini Reproductor aplica un fondo completamente tintado y degradado con los colores extraídos de la pista activa, manteniendo consistencia visual en todas las pantallas.
    - Los atajos inferiores de Now Playing se organizan en 3 módulos equilibrados de igual proporción (`EQ FX`, `Letras`/`Carátula` y `Cola`), con área táctil superior a 48dp y texto en una sola línea sin cortes verticales.

15. **Letras Sincronizadas (.LRC) Estilo Karaoke**:
    - Integración de analizador de marcas de tiempo `[mm:ss.xx]` con persistencia local en `Android/data/.../files/lyrics/track_{id}.lrc`.
    - Descarga automática libre desde **LRCLIB** al cambiar a canciones sin letra, sin registro ni API keys.
    - Interfaz Karaoke interactiva con desplazamiento automático suave (*auto-scroll*), tipografía resaltada neón para el verso actual, y salto directo en la canción (*Seek-to-time*) al compás de cualquier línea pulsada.

16. **Recepción Inteligente 'Abrir con...' y 'Compartir con...' (Puente con Gestores de Descarga y Apps Externas)**:
    - Debe responder a `ACTION_VIEW`, `ACTION_SEND` y `ACTION_SEND_MULTIPLE` mediante `IncomingMediaHandler`.
    - Si es audio (`audio/*`), copiar inmediatamente a la subcarpeta estructurada `songs/` para garantizar persistencia sin conexión de por vida, añadir a Room e iniciar reproducción instantánea con Now Playing expandido.
    - Si es video (`video/*`), activar automáticamente el diálogo de conversión 'Video a Música' 3 en 1 para demuxing rápido a `.m4a`, carátula WebP y Video Canvas vinculado.
    - Si es enlace web (`text/plain` o URL), abrir el diálogo de descarga de enlaces con auto-resolución de información (título, autor y carátula).
    - Soportar `singleTop` y `onNewIntent` sin recreación destructiva de la interfaz ni reinicio de pistas en reproducción.

17. **Idioma de Comunicación**:
   - La documentación, comentarios en código, cadenas de usuario (`strings.xml`) y mensajes de commit deben redactarse en **español**.
