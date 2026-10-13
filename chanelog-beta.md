# 🌌 Aura Beta — Changelog Oficial (`v0.2.0-beta-1a` • Codename: **Nebula**)

> **Versión:** `v0.2.0-beta-1a` (`NEBULA-00200A` • Build `#100200`)  
> **Nombre en Launcher:** `Aura Beta`  
> **Identificador de Paquete (`Android/data/`):** `com.auramusic.beta`  
> **Codename de la Serie Beta:** **Nebula**  
> **Repositorio & Releases Oficiales:** [https://github.com/LuisAlejandro544/Aura-music/releases](https://github.com/LuisAlejandro544/Aura-music/releases)  
> **Requisitos del Sistema:** Android 8.0 Oreo (API 26) hasta Android 15+ (API 36)  
> **Estado de Depuración:** Producción Beta Limpia *(Sin Aura Monitor, sin generador de audios de prueba ni LeakCanary; con Actualizador OTA de `yt-dlp` y Actualizador de APK 100% activos)*

---

## 🔥 Novedades Destacadas en `v0.2.0-beta-1a`

1. 🖼️ **Fondo de Pantalla Personalizado de Galería (Imagen WebP o Video Corto)**:
   - Ahora puedes elegir una **foto o un video corto de la galería de tu teléfono** para ponerlo como fondo de pantalla mientras navegas por **Tu Biblioteca**, **Inicio** o **toda la aplicación** (configurable desde **Ajustes > Aura Dinámica, Video & Modo Juego**).
   - Incluye **Control de Oscurecimiento / Velo OLED ajustable (25% a 92%)** para que los títulos y botones siempre se lean con nitidez perfecta, y **Control de Desenfoque / Blur ajustable (0 a 25 dp)** sobre la imagen de fondo.
2. 🎛️ **Motor de Crossfade Profesional Dual-Deck (Mezcla Superpuesta Real sin Pausas)**:
   - Nuevo sistema de **doble reproductor sincronizado (`Deck A + Deck B`)** con curva trigonométrica de igual potencia (*Equal-Power S-Curve: `sin/cos`*): cuando una canción empieza a bajar de volumen en sus últimos segundos (o al cambiar de canción con el Crossfade activo), **la siguiente canción empieza a sonar exactamente al mismo tiempo** subiendo desde `0%` a `100%`, eliminando por completo cualquier pausa o silencio entre pistas.
   - Añadidos botones rápidos de estudio (`Off`, `3s`, `5s DJ`, `8s`, `12s`) en la pestaña de **Transiciones**.
3. 🧩 **Sistema de 2 Widgets Interactivos Adaptativos con Color de Carátula y Reanudación en 2do Plano**:
   - **Widget 1 — Reproductor Adaptativo (`AuraMusicWidgetProvider`)**: Extrae dinámicamente los colores de la carátula de la canción actual, permite **reanudar la música directamente en segundo plano** aunque hayas cerrado la app (`WidgetPlaybackHeadlessController`), incluye botón de **Favorito ❤️** y **se adapta automáticamente al estirarlo** en la pantalla de inicio en 3 diseños: *Compacto (4x1)*, *Mediano (4x2 con barra de progreso y tiempos `mm:ss`)* y *Expandido de estudio (4x3)*.
   - **Widget 2 Independiente — Top 4 Canciones & 4 Playlists (`AuraLibraryWidgetProvider`)**: Un segundo widget en cuadrícula 2x2 que muestra tus **4 canciones más escuchadas** o tus **4 Playlists principales** (con su portada o collage 2x2 de hasta 4 fotos) para iniciar su reproducción con un toque, todo configurable con vista previa en vivo desde el nuevo apartado **Ajustes > Widget**.
4. 🎮 **Modo Juego / Ahorro Inteligente de Recursos en Segundo Plano**:
   - Al minimizar la app mientras juegas o usas otras aplicaciones pesadas, Aura Beta **libera automáticamente todo lo que no se está viendo** (apaga el decodificador de video en GPU, detiene el muestreo de texturas, suspende el hilo del visualizador espectral de 28 bandas y recorta cachés gráficas en RAM), mientras **mantiene 100% activos la música y únicamente los efectos de audio que tengas encendidos** (EQ, 8D/16D, Reverb, A-B Loop o Crossfade Dual-Deck).
5. 🎨 **Transición de Colores de Video Suave a 1 Segundo (1000 ms)**:
   - Tanto en el reproductor a pantalla completa como en el **Mini Reproductor**, el muestreo y fundido de colores del Video Canvas se realiza cada **1 segundo (1000 ms)** con interpolación orgánica (`tween(1000)`), logrando cambios cromáticos elegantes sin marear la vista.
6. 📁 **Carpeta Pública / Personalizada de Usuario y Exportación de Vídeos con Audio Completo**:
   - En **Ajustes > Medios & Almacenamiento** ahora puedes pulsar **"Crear / Elegir carpeta"** para seleccionar o crear libremente la carpeta que tú quieras en tu teléfono (fuera de la ruta restringida `Android/data/`), mostrando en todo momento la **ruta exacta** donde quedan tus archivos (`.../Videos`).
   - Permite guardar cualquier vídeo ya descargado (o todos con un solo toque en **"Guardar todos"**) como un archivo `.mp4` **con su pista de audio original incluida** (uniendo instantáneamente el vídeo y el audio de la canción mediante `FFmpeg` en segundo plano) para que puedas reproducirlo con sonido en tu Galería o compartirlo en redes sociales.
7. 🌐 **Modo Streaming Híbrido Tri-Plataforma Estilo Spotify (`Explorar`: YT Music, YouTube & SoundCloud), Caché Inteligente No Lineal (50MB–500MB • 30 min TTL), Radio Infinita y Política de Red para Video Canvas**:
   - **Pestaña `Explorar` en la Barra Inferior (`StreamingExploreScreen`) con Búsqueda en YT Music, YouTube y SoundCloud**: Busca canciones, remixes, phonk, música electrónica y sets de DJ al instante combinando resultados de **YouTube Music (`🎵 YT MUSIC`)**, **YouTube (`▶️ YOUTUBE`)** y **SoundCloud (`☁️ SOUNDCLOUD`)**, tanto integrados en el filtro **Todo** como mediante el filtro exclusivo **☁️ Solo SoundCloud**.
   - **Motor SoundCloud Integrado Sin Login (`SoundCloudApiClient`)**: Hidrata automáticamente el `client_id` público de SoundCloud (`api-v2.soundcloud.com`) sin pedir cuentas ni claves, resolviendo flujos de audio progresivos en alta calidad (MP3 / Opus) con portadas HD (`500x500`) y respaldo en `yt-dlp`.
   - **Resolución con `yt-dlp` + Pre-Descarga No Lineal Adelantada (Read-Ahead Buffer)**: Mientras escuchas una canción en streaming, Aura Beta pre-descarga por adelantado en segundo plano los próximos minutos de la pista mediante bloques `HTTP Range` (igual que hace YouTube) hacia una caché temporal inteligente.
   - **Caché Temporal Configurable (50 MB por defecto hasta 500 MB • Expiración de 30 Minutos)**: Guarda tanto la **música** como los **videos de fondo (Video Canvas)** en caché temporal para reproducir al instante si retrocedes a la canción anterior o la escuchas en bucle. Los elementos que lleven **más de 30 minutos sin usarse** se purgan automáticamente y, si se alcanza el límite configurado (hasta 500 MB), elimina primero las canciones más viejas (*LRU Eviction*).
   - **Nueva Opción de Video Canvas para Streaming (`Solo con Wi-Fi`, `Siempre (Wi-Fi y Datos Móviles)` o `Desactivado`)**: Configurable en **Ajustes > Aura Dinámica, Streaming & Modo Juego**, controla exclusivamente los videos de fondo en modo streaming sin tocar ni afectar en absoluto tu reproducción local.
   - **Radio Automática de Canciones Similares y Consulta Previa al Cambiar de Pista**: Antes de que termine la canción actual (o antes de saltar hacia la canción anterior/siguiente en streaming), la app consulta a la API una canción similar o relacionada y toma su flujo para reproducirla sin pausas, incluyendo **letras sincronizadas automáticas (`LRCLIB`)**, **visualizador espectral de 28 bandas activo** y **botón de descarga permanente en 1 toque** hacia tu biblioteca local.
   - **Bypass Temporal de C++20 en Streaming**: Durante la reproducción en streaming, el motor DSP C++20 entra en bypass limpio (`NativeAudioProcessor.isStreamingBypassActive = true`) tal como fue diseñado para esta fase, manteniendo 100% activo el motor C++20 para todas tus canciones locales.
8. 🎧 **Smart AutoMix Estilo Apple Music & Generador de Auto-Remix / Mashup con 2 Canciones Locales (`SmartAutoRemixEngine`)**:
   - **Smart AutoMix al Terminar Canciones (Música Local)**: Inspirado en las mezclas de **Apple Music**, cuando una canción local está a punto de acabar el motor C++20 detecta el punto óptimo de salida (*Smart Outro Cue Point*), activa la transición superpuesta **Dual-Deck** (`sin/cos` de igual potencia) y modula progresivamente en vivo la **Curva Armónica en X** (atenuando subgraves `<120 Hz` y abriendo un bolsillo vocal en `2.6 kHz`) para que las voces y el ritmo entrante no choquen. *(Por ahora disponible para música local; más adelante llegará también a streaming)*.
   - **Auto-Remix / Mashup Automático con 2 Canciones (Manual o Auto-Match Inteligente)**: Accesible directamente desde **Tu Biblioteca**, desde cualquier **Playlist** y dentro del **Creador de Mixtapes/Remixes**:
     - Puedes **elegir tú mismo las 2 canciones locales** (Canción A y Canción B con botón para intercambiarlas `A ⇄ B`) o pulsar **"Elección Inteligente (Auto-Match 2 Pistas)"** para que el algoritmo escoja las 2 canciones de tu biblioteca cuyos tiempos y energías mejor combinan.
     - **2 Estilos de Remix Automático en FFmpeg**:
       1. 🎤 **Mashup (Voz A + Base B)**: Extrae y realza la presencia vocal central de la Canción A y la superpone sobre la base rítmica e instrumental de la Canción B con sincronización de ritmo y limitador de estudio.
       2. 🎛️ **Remix Alternado DJ (Beat-Sync)**: Estructura ambas canciones en 4 bloques dinámicos estilo DJ Set (*Intro A → Beat Drop B → Vocal Switch A → Outro Finale B*) con ajuste armónico de tempo (`atempo`) y cruces `acrossfade`.
     - Genera automáticamente su **portada dividida en WebP sin pérdida**, concatena sus **letras `.LRC` para Karaoke** y cambia la carátula y el **Video Canvas en vivo** según el capítulo que esté sonando.
9. 📋 **Importador Universal de Playlists de Spotify y YouTube Music / YouTube (`ExternalPlaylistImporter` & `ImportExternalPlaylistDialog`)**:
   - Ahora puedes traer tus **Playlists públicas de Spotify o de YouTube Music / YouTube** directamente a Aura Beta **sin necesidad de iniciar sesión ni configurar cuentas**.
   - Ábrelo desde **Tu Biblioteca (Pestaña Playlists)**, desde **Importar Música** o usando **"Compartir con..."** directamente desde la app de Spotify o YouTube Music hacia Aura Beta.
   - Previsualiza la portada oficial, el autor y todas las canciones detectadas, cambia el nombre si lo deseas y **elige cómo quieres guardarla**:
     1. ⚡ **Modo Streaming Rápido (Instantáneo)**: Crea la Playlist en segundos con su portada oficial en **WebP sin pérdida** lista para reproducirse en Modo Streaming bajo demanda sin ocupar almacenamiento en tu teléfono.
     2. 📥 **Descargar Todo Offline (En 2do plano)**: Crea la Playlist y descarga automáticamente todas las canciones en segundo plano (`AuraDownloadService`), con opción para incluir su **Video Canvas en 480p** y **eliminar silencios al inicio y final**.
   - ⏳ **Descargas 1-en-1 globales y persistentes (Fase B)**: el servicio procesa **una sola descarga a la vez en todo el sistema** (único consumidor sobre `Channel` FIFO — una playlist de 100 pistas y una canción suelta de Explorar **nunca compiten** por `yt-dlp`/`FFmpeg`/red), la notificación muestra la posición **`En cola: N`**, la tarjeta flotante global muestra `i/N` con botón **Cancelar lote** (retira las en espera sin matar la activa), el lote se persiste en **`metadata/playlist_batch.json`** y **se reanuda solo** al reabrir la app, y sin red la espera **se pausa** (`Sin conexión — en pausa`) en vez de marcar fallos.

---

## 🚀 Todo lo que ofrecemos en Aura Beta (`v0.2.0-beta-1a`)

**Aura Beta** es un reproductor musical audiófilo y centro multimedia todo-en-uno para Android, diseñado desde cero para que puedas disfrutar, personalizar, descargar y masterizar tu música directamente desde tu teléfono móvil sin necesidad de una PC.

### 🎛️ 1. Motor de Audio Nativo en ISO C++20 (64-bit DSP), Smart AutoMix (Estilo Apple Music), Auto-Remix de 2 Pistas & Modo Bit-Perfect
- **Ecualizador Paramétrico de 10 Bandas ISO Integrado en Modal**:
  - Bandas de precisión calibradas con nombres acústicos intuitivos: `31 Hz (Subgraves)`, `62 Hz (Bajos)`, `125 Hz (Graves)`, `250 Hz (Cuerpo)`, `500 Hz (Medios Bajos)`, `1 kHz (Voces)`, `2 kHz (Claridad)`, `4 kHz (Presencia)`, `8 kHz (Brillo)` y `16 kHz (Aire / Agudos)`.
  - Filtros Bi-cuadráticos (*Peaking Biquads*) en coma flotante de 64 bits con limitador suave anti-clipping (`-15 dB` a `+15 dB`), perfiles rápidos (*Rock, Pop, Electrónica, Jazz, Acústico, Bass Boost, Plano*), alcance configurable (*Solo esta canción* vs *Todas las siguientes*) y refuerzo de subgraves a 60 Hz.
  - Accesible al instante desde el **Mini Reproductor** y desde **Now Playing** sin pantallas aisladas que interrumpan tu navegación.
- **Smart AutoMix Estilo Apple Music (Música Local) & Crossfade Profesional Dual-Deck con Curva Armónica en X**:
  - Mezcla superpuesta real con dos motores `ExoPlayer` sincronizados: detecta el outro acústico de la canción local a punto de terminar y, mientras la pista saliente baja con curva `cos(t)`, la pista entrante sube simultáneamente con curva `sin(t)` modulando en tiempo real la atenuación de subgraves (`<120 Hz`) y el bolsillo vocal (`2.6 kHz`) en C++20 para evitar choques de bajos y de voces.
- **Generador de Auto-Remix & Mashup con 2 Canciones Locales (`SmartAutoRemixEngine`)**:
  - Crea remixes automáticos eligiendo manualmente 2 canciones locales o mediante **Auto-Match Inteligente**, con soporte para **Mashup (Voz A + Base B)** y **Remix Alternado DJ con sincronización de ritmo**, portada dividida WebP, letras `.LRC` y capítulos de Video Canvas reactivos.
- **Clarificador de Voces HD (`VocalClarityProcessor` Mid-Side en C++20)**:
  - Aísla el canal vocal central (*Mid*) del acompañamiento instrumental (*Side*), limpia la turbidez en 180 Hz y realza la articulación vocal en `2.8 kHz` y `5.5 kHz`.
- **Audio Espacial 8D y 16D Multi-Órbita Binaural en C++20**:
  - **Modo 8D Clásico**: Rotación orbital de 360° con simulación de sombra acústica de la cabeza (*Head Shadow Filtering*).
  - **Modo 16D Multi-Órbita**: Doble capa contra-rotatoria independiente (bajos estables en órbita interna y voces/agudos en trayectoria figura-8 con micro-retardo Haas).
- **Suite Reverb 100% C++20 (`ReverbProcessor`)**:
  - Simulación acústica de espacios reales (*Estudio, Sala Mediana, Club En Vivo, Gran Hall, Catedral, Eco Espacial*) libre de silencios o bloqueos del sistema, con ajuste manual de tamaño de sala, decaimiento y mezcla.
- **Normalización de Volumen Inteligente (Loudness Normalizer EBU R128 / Nivel Spotify)**:
  - Iguala automáticamente la energía acústica entre canciones de distintas épocas o fuentes con 3 modos: *Sutil (-18 LUFS)*, *Estándar (-14 LUFS)* y *Alto (-11 LUFS)*.
- **Modo Bit-Perfect 1:1 & Salida Google AAudio de Ultra-Baja Latencia (`dsp_bitperfect.h`)**:
  - Bypass directo 1:1 sin alteración matemática para DACs USB externos y audífonos Hi-Res, con presets *Nativo 1:1*, *Hi-Res 96 kHz / 24-bit* y *Ultra Hi-Res 192 kHz / 32-bit Float*, además de telemetría en vivo en la ficha técnica de cada canción.
- **Decodificación Nativa de Formatos Especiales y Audiófilos**:
  - Soporte directo para **DSD** (`.dsf`, `.dff`), **Monkey's Audio** (`.ape`), **WavPack** (`.wv`), **ALAC / AIFF** (`.alac`, `.aiff`), **Musepack** (`.mpc`) y módulos **Chiptune / Tracker** (`.mod`, `.xm`, `.it`, `.s3m`), además de `FLAC`, `WAV`, `MP3`, `M4A`, `OGG` y `OPUS`.

---

### 🎬 2. Video Canvas Dinámico, Fondo de Pantalla de Galería, Modo Cinemático y Modo Juego
- **Fondo de Pantalla Personalizado de Galería (`AppWallpaperBackground`)**:
  - Pon tu foto favorita (convertida a WebP sin pérdida) o un video corto de tu galería como fondo de pantalla en **Tu Biblioteca**, en **Biblioteca e Inicio** o en **toda la app**, con controles deslizantes de **Oscurecimiento (25%–92%)** y **Desenfoque / Blur (0–25 dp)**.
- **Selección de Diseños del Reproductor (`NowPlayingDesignMode`)**:
  - 🎛️ **Modo Clásico**: Carátula central 1:1, visualizador espectral de 28 bandas en C++20, barra de balance estéreo L/R y atajos inferiores.
  - 🎬 **Modo Cinemático Canvas (Estilo Spotify)**: Despeja más del 70% de la pantalla para el video en movimiento, con viñeta ambiental reactiva a los colores del video cada 1 segundo, minicarátula lateral, verso de Karaoke flotante y controles ergonómicos con selector circular clásico ("bolita").
  - ✨ **Modo Automático Inteligente**: Alterna automáticamente al diseño Cinemático cuando la pista cuenta con Video Canvas y al diseño Clásico en pistas de solo carátula.
- **4 Modos de Visualización y Encuadre de Video (`VideoDisplayMode`)**:
  - *Fondo Completo (Rellenar / Recortar)*, *Fondo Completo (Adaptado Horizontal 16:9 sin recortar rostros)*, *Lienzo en Carátula (1:1)* y *Solo Carátula*.
- **Bucles Infinitos en FFmpeg (Seamless Crossfade & Efecto Boomerang), Saltos en 0ms y Modo Juego**:
  - Para loops cortos (≤20s), permite elegir entre fundido cruzado (`xfade`) o **Efecto Boomerang / Ping-Pong** (`reverse + concat`).
  - Para videos largos sincronizados (>20s), estructura fotogramas clave cada 1 segundo (GOP corto a 30fps) y elimina la pista de audio redundante (`-an`) para ahorrar hasta un 85% de espacio en disco.
  - **Modo Juego en 2do Plano**: Suspende automáticamente el renderizado de video, el muestreo de colores y el visualizador FFT cuando minimizas la app para jugar, conservando 100% activos tus efectos de audio.

---

### 🔗 3. Descargas de YouTube / TikTok, Importador de Playlists (Spotify / YT Music), Video a Música, Mixtapes y Actualizador OTA (`yt-dlp`)
- **Importador Universal de Playlists de Spotify y YouTube Music / YouTube (`ExternalPlaylistImporter`)**:
  - Pega o comparte el enlace de cualquier Playlist pública de **Spotify** o **YouTube Music / YouTube** para importarla con su portada oficial en **WebP sin pérdida** y todas sus canciones, eligiendo entre **⚡ Modo Streaming Rápido (Instantáneo)** o **📥 Descargar Todo Offline en 2do plano**.
- **Descarga Directa desde YouTube, TikTok, SoundCloud y Enlaces Web en Segundo Plano (`AuraDownloadService`)**:
  - Motor predeterminado **`yt-dlp` (98% de efectividad)** con entorno **CPython 3.11 / QuickJS**, separación independiente de cabeceras por flujo (`audioHeaders` vs `videoHeaders` alineadas al perfil `c=` de `googlevideo.com`) y **cascada automática anti-HTTP 403 de 5 niveles** (**Cabeceras por perfil `c=`** → **InnerTube Multi-Cliente (`ANDROID_VR`/`VISIONOS`/`ANDROID_MUSIC`/`IOS`) `<300ms`** → **Bypass Invidious** → **WebView Móvil** → **Descarga directa nativa con `yt-dlp` CLI**).
  - **Acelerador HTTP Range Multi-Bloque**: Descarga pistas y videos a máxima velocidad (10–40 MB/s) eliminando el estrangulamiento de red, con vinculación automática de **Video Canvas en 480p** y carátula **WebP Lossless**.
  - Continúa descargando en segundo plano con notificación nativa interactiva aunque salgas de la aplicación.
- **Actualizador Automático de Versiones APK (`AppReleaseUpdater`)**:
  - Consulta automáticamente los Pre-Releases `-beta` de GitHub sin importar cómo cambie el tag (`v0.1.0-beta.1a` → `v0.1.1-beta-1a` → `v0.2.0-beta.1m`), detecta el procesador de tu teléfono (`arm64-v8a` o `armeabi-v7a`) para bajar el APK exacto entre los 3 disponibles, verifica su suma `SHA-256` contra `SHA256SUMS.txt`, muestra las novedades de `chanelog-beta.md` en pantalla y lanza el instalador de Android conservando intactas tus canciones y playlists en `Android/data/com.auramusic.beta/`.
- **Actualizador Automático en Caliente de `yt-dlp` (OTA con Verificación SHA-256)**:
  - Integrado y activo en **Aura Beta**: verifica al iniciar la app (y desde *Ajustes > Motores Nativos & Actualizador yt-dlp OTA*) si existe una nueva versión oficial de `yt-dlp`, descargándola y validándola criptográficamente con `SHA2-256SUMS` para que las descargas web nunca dejen de funcionar sin necesidad de reinstalar el APK.
- **Conversor "Video a Música" 3 en 1 (Sin PC)**, **Puente "Abrir con..." / "Compartir con..."**, **Mixtape Maker con Capítulos Reactivos** y **Eliminación Inteligente de Silencios (`AudioSilenceTrimmer`)**.

---

### 🎤 4. Karaoke Sincronizado (.LRC / .TXT), 2 Widgets Interactivos, Auriculares y Almacenamiento
- **Karaoke Automático e Inmersivo a Pantalla Completa (`LRCLIB`)**:
  - Descarga automática y gratuita de letras sincronizadas `.lrc`, buscador interactivo con insignia `⭐ OFICIAL / RECOMENDADA` en primer lugar, importador de archivos `.lrc`/`.txt` locales y **Modo Karaoke a Pantalla Completa** estilo *Apple Music Sing / Spotify* con salto táctil por verso.
- **2 Widgets Interactivos para Pantalla de Inicio & Apartado "Widget" en Ajustes**:
  - **Widget Reproductor Adaptativo (4x1, 4x2 y 4x3)**: Se tiñe con los colores de la portada actual, incluye botón de Favorito ❤️, barra de progreso sincronizada al estirarlo y reanuda tu música en segundo plano sin abrir la app.
  - **Widget Biblioteca (4x2)**: Muestra tus **4 canciones más escuchadas** o tus **4 Playlists principales** (con collage 2x2) para reproducirlas al instante, configurable desde **Ajustes > Widget**.
- **Suite Acústica para Auriculares**:
  - Filtro **Crossfeed Chu Moy / Bauer** en C++20 (exclusivo al conectar auriculares), **Balance Estéreo Fino L/R**, protección contra desconexiones (*Becoming Noisy Guard*), memoria de volumen independiente y mapeo de gestos de botones.
- **Almacenamiento Limpio, Transparente y Carpeta Pública del Usuario (`UserPublicMediaExporter`)**:
  - Organizado internamente en `Android/data/com.auramusic.beta/files/` (`songs/`, `images/`, `lyrics/`, `metadata/` y `videos/`), con panel dedicado en **Ajustes > Medios & Almacenamiento** para revisar cuántos KB/MB ocupa cada portada o video y liberar espacio con un toque.
  - **Carpeta Pública de Vídeos (Sin Restricciones)**: Permite crear o seleccionar cualquier carpeta de tu teléfono, ver la ruta exacta y exportar tus vídeos descargados en `.mp4` **con su audio completo unido por FFmpeg** hacia la subcarpeta `Videos/` (con soporte arquitectónico listo para `Images/`).

---

## 📦 Guía de Descarga: ¿Para qué sirve cada APK disponible?

En cada lanzamiento de **Aura Beta (`Nebula`)** generamos **3 archivos APK exclusivos para dispositivos móviles** (eliminando arquitecturas innecesarias de PC para máxima eficiencia). Elige el que mejor se adapte a tu teléfono:

| Archivo APK | Arquitectura | ¿Para qué sirve y qué teléfonos deben instalarlo? |
| :--- | :--- | :--- |
| 📱 **`AuraBeta-v0.2.0-beta-1a-Nebula-arm64-v8a.apk`** | **ARM 64-bits (`arm64-v8a`)** | **Recomendado para la mayoría de teléfonos modernos (2018 en adelante).** Incluye exclusivamente los motores nativos en C++20, FFmpeg y Python de 64 bits. Es más ligero en espacio de almacenamiento y ofrece el máximo rendimiento acústico y energético. |
| 📱 **`AuraBeta-v0.2.0-beta-1a-Nebula-armeabi-v7a.apk`** | **ARM 32-bits (`armeabi-v7a`)** | **Diseñado para teléfonos antiguos, básicos o con sistemas Android de 32 bits.** Contiene exclusivamente los binarios nativos optimizados con instrucciones ARMv7 NEON de 32 bits sin cargar librerías de 64 bits que tu procesador no utilice. |
| 🌐 **`AuraBeta-v0.2.0-beta-1a-Nebula-universal.apk`** | **Universal Móvil (`arm64-v8a` + `armeabi-v7a`)** | **Funciona en absolutamente cualquier teléfono Android (32 y 64 bits).** Es el APK todo-terreno ideal si no sabes qué procesador tiene tu celular, para compartir el archivo con amigos por mensajería o para subir directamente a tiendas como **Uptodown**. |

---

## 🆕 Actualización — Calidad de Audio Opus / M4A + 📶 Modo Ahorro de Datos (solo Explorar)

> Todo lo anterior se conserva intacto; esto solo añade las nuevas funciones sin reescribir el historial.

10. 🎚️ **Selector de Calidad de Audio (Opus y M4A, para local y streaming con `yt-dlp`)**:
    - En **Ajustes > Streaming & Caché Inteligente** ahora puedes elegir entre **Ahorro Opus ~48 kbps (itag 249)**, **Estándar M4A ~128 kbps (itag 140, el formato de siempre)**, **Alta Opus ~160 kbps (itag 251, suena mejor y pesa menos)** o **Automática Hi-Fi (recomendada: Opus 251 si existe, si no M4A 140)**.
    - Vale para **canciones en streaming (Explorar)** y para **canciones locales/descargas con `yt-dlp`**: el ranking por itag rige en `InnerTubeClient`, `Invidious` y `YtDlpNativeEngine`, el selector `bestaudio[ext=webm]/bestaudio[ext=m4a]` rige en `YtDlpStreamDownloader`, y lo descargado se guarda en `songs/` como **`.opus`** u **`.m4a`** según tu elección (ambos reproducibles por ExoPlayer).
    - Nota honesta: YouTube no da lossless real; Opus ~160 kbps es el techo práctico.
11. 📶 **Modo Ahorro de Datos (exclusivamente para el modo Explorar / streaming)**:
    - Un solo interruptor en Ajustes que, solo en streaming, **fuerza Opus ahorro**, **apaga el Video Canvas de fondo**, **no busca letras automáticamente (ni LRCLIB ni auto-detección online)** y **pausa la pre-descarga de audio/video y la radio de canciones similares**.
    - Tus **canciones locales, sus videos y sus letras no se tocan en absoluto**: siguen sonando, mostrándose y buscándose como siempre.
