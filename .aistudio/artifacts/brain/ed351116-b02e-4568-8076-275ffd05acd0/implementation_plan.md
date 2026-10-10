# Modo Streaming Híbrido (Estilo Spotify), Caché Inteligente y Control de Red para Video Canvas

Este plan detalla la incorporación de un **Modo Streaming completo estilo Spotify** en Aura Music, permitiendo buscar y reproducir música al instante desde **YouTube y YouTube Music** con indicadores visuales de origen, resolver los flujos mediante **`yt-dlp` + `InnerTube`**, pre-almacenar en **caché temporal inteligente (audio y Video Canvas)** varios minutos por adelantado, encolar automáticamente **canciones similares** antes de terminar cada pista y ofrecer **descarga permanente con un solo toque**, manteniendo intacta la reproducción local.

---

## Decisiones Confirmadas con el Usuario

> [!IMPORTANT]
> Todas las decisiones clave han sido alineadas para preservar la estabilidad, modularidad y reglas de diseño Dark Luxury Neo-Glass de Aura Music:

- **Origen Dual (YouTube + YouTube Music con Insignias)**: La API de búsqueda y recomendaciones consultará tanto **YouTube Music** como **YouTube**, mostrando una insignia clara en cada resultado (`YT MUSIC` vs `YOUTUBE`). Además, se normalizarán los enlaces `music.youtube.com` para que **`yt-dlp`** (y el respaldo `InnerTube`) puedan resolver y descargar sin fallos pistas de ambas plataformas.
- **Nueva Pestaña Dedicada en la Barra Inferior**: Se agregará la pestaña **"Explorar"** (Streaming) en la barra de navegación inferior para mantener la pantalla de Inicio y la Biblioteca Local limpias y bien separadas.
- **Caché Temporal Inteligente Configurable (Audio + Video Canvas)**:
  - **Tamaño por defecto**: `50 MB`, configurable por el usuario en Ajustes hasta un **máximo de `500 MB`**.
  - **Pre-descarga no lineal (Read-Ahead Buffer)**: Mientras el usuario escucha una canción en streaming, el motor irá descargando por adelantado los siguientes minutos por bloques en la caché temporal (tanto el audio como el Video Canvas de fondo si aplica).
  - **Reutilización Instantánea y Limpieza Automática (TTL 30 min + LRU)**: Si el usuario repite la canción en bucle o retrocede a la canción anterior, se reproduce al instante desde la caché sin gastar datos. Los archivos que lleven **más de 30 minutos sin usarse** se eliminan automáticamente, y si la caché alcanza el límite configurado, se purgan primero los elementos más antiguos (LRU).
- **Política de Datos para Video Canvas en Streaming**: Nueva opción en Ajustes con 3 modos exclusivos para streaming (**"Solo con Wi-Fi"**, **"Siempre (Wi-Fi y datos móviles)"** o **"Desactivado"**), sin afectar en absoluto a los videos de las canciones locales ya guardadas.
- **Bypass de Procesamiento C++20 en Streaming (Por Ahora)**: Cuando se reproduzca una pista en modo streaming, el motor omitirá el procesamiento DSP en C++20 (reproducción directa limpia en ExoPlayer), manteniendo C++20 100% activo para todas las canciones locales.
- **Descarga Permanente con 1 Toque + Letras Karaoke**: Cualquier canción en streaming mostrará sincronización automática de letras (`.LRC` desde LRCLIB) y un botón directo para **Descargar** permanentemente a la biblioteca local (aprovechando los datos que ya estén en la caché temporal para acelerar aún más el guardado).
- **Documentación y Changelog**: Se actualizarán todos los archivos `.md` (`README.md`, `READMEAI.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md`, `AGENTS.md`) y se añadirá el registro de estas novedades en `chanelog-beta.md` conservando íntegro todo su contenido actual.

---

## 1. Visión General y Concepto Principal

- **¿Qué hace?**: Transforma Aura Music en un reproductor híbrido que combina su estudio audiófilo local con un **modo de descubrimiento y streaming infinito tipo Spotify**. El usuario busca cualquier canción, artista o video, ve claramente si proviene de `YT Music` o `YouTube`, la escucha de inmediato mientras se pre-carga en caché temporal con su letra Karaoke y su Video Canvas (según su configuración de Wi-Fi/Datos), y antes de que termine la pista, la app ya tiene lista y resuelta con `yt-dlp` una canción similar para continuar sin interrupciones.
- **Valor Principal**: Cero esperas, ahorro inteligente de datos móviles mediante caché reutilizable de 30 minutos y transición transparente entre escuchar en streaming y descargar para siempre en el teléfono.

---

## 2. Experiencia de Usuario y Diseño Visual

### Flujos Principales del Usuario
1. **Explorar y Buscar (Pestaña "Explorar")**:
   - El usuario toca el nuevo icono **"Explorar"** en la barra inferior.
   - Encuentra un buscador rápido en la parte superior con filtros rápidos (*Todo*, *YT Music*, *YouTube*) y secciones de tendencias/recomendaciones.
   - Cada tarjeta de resultado muestra la miniatura en alta definición, título en marquesina si es largo, artista, duración, una **insignia distintiva (`🎵 YT MUSIC` en violeta/rosa neón o `▶️ YOUTUBE` en rojo carmesí)** y dos acciones rápidas: **Reproducir en Streaming** (al tocar la tarjeta) y **Botón de Descarga Directa (`⬇️`)**.
2. **Reproducción en Streaming con Pre-Carga, Letras y Video Canvas**:
   - Al tocar una canción, empieza a sonar en *Now Playing* y en el *Mini Reproductor*.
   - Las letras sincronizadas (`.LRC`) se buscan y muestran automáticamente en la tarjeta de Karaoke y en el modo Karaoke a pantalla completa.
   - Si la opción de **Video Canvas en Streaming** lo permite (ej. conectado a Wi-Fi en modo *"Solo con Wi-Fi"*, o en modo *"Siempre"*), el video de fondo 480p se pre-carga en la caché temporal y se sincroniza visualmente con el diseño Cinemático o Clásico.
   - Si el usuario pulsa **"Descargar"** mientras escucha o desde la lista, la canción, su portada WebP, su letra `.LRC` y su Video Canvas se guardan permanentemente en la biblioteca local (`songs/`, `images/`, `videos/`, `lyrics/`).
3. **Continuación Automática (Radio / Canción Similar Antes de Terminar)**:
   - Cuando a la canción actual le quedan pocos segundos (o durante el último tramo de reproducción), la app consulta automáticamente a la API por una **canción similar recomendada**, resuelve su flujo real mediante **`yt-dlp` / `InnerTube`** en segundo plano y la deja pre-cargada para que al pasar a la siguiente pista (o al terminar la actual) suene de inmediato sin silencios de carga.
4. **Nuevos Controles en Ajustes**:
   - En **Ajustes > Aura Dinámica, Video & Modo Juego** (y sección de Streaming/Medios):
     - Selector **"Video Canvas en Streaming"**: *Solo con Wi-Fi (Recomendado)* • *Siempre (Wi-Fi y Datos Móviles)* • *Desactivado*.
     - Panel **"Caché Temporal de Streaming (Audio y Video)"**: Deslizador y chips para ajustar el límite entre **50 MB (por defecto)** y **500 MB (máximo)**, indicador en vivo de cuántos MB están en uso, aviso de expiración automática a los 30 minutos de inactividad y botón para limpiar la caché manualmente si lo desea.

### Identidad Visual y Estética
- **Estilo Dark Luxury Neo-Glass OLED**: Fondos 100% opacos (`#07080D` y `#11131F`), tarjetas con bordes sutiles luminosos y áreas táctiles mínimas de `48.dp`.
- **Insignias de Plataforma y Estado**:
  - `YT MUSIC`: Píldora compacta con acento púrpura/magenta neón.
  - `YOUTUBE`: Píldora compacta con acento rojo/coral vibrante.
  - `EN CACHÉ ⚡`: Indicador sutil cuando la pista o el video ya están pre-cargados en la caché temporal de 30 minutos.

---

## 3. Decisiones de Producto y Arquitectura

- **1. Búsqueda y Radio por API + Resolución de Flujo con `yt-dlp` (Compatibilidad Total con YT Music)**:
  - *Enfoque elegido*: La API de búsqueda y de pistas relacionadas (*Watch Next / Automix*) obtiene al instante la lista de canciones con sus metadatos e IDs tanto de YouTube Music como de YouTube. Para reproducir o descargar, convertimos cualquier enlace o ID de `music.youtube.com` al formato canónico compatible y lo procesamos con **`yt-dlp`** (apoyado por `InnerTube` multi-cliente para arranque instantáneo) de modo que `yt-dlp` pueda reproducir y descargar sin problemas tanto de YouTube como de YouTube Music.
- **2. Pre-Descarga No Lineal por Bloques en Caché Temporal (Estilo YouTube)**:
  - *Enfoque elegido*: Un gestor de caché temporal (`StreamingCacheManager`) almacena bloques progresivos de audio y video en un directorio dedicado de caché (`cacheDir/streaming_media/`). A medida que el usuario escucha el primer minuto, en segundo plano se descargan por adelantado los siguientes minutos usando rangos HTTP (`ChunkedStreamDownloader`).
  - *Ventaja*: Si el usuario vuelve a la canción anterior, adelanta/retrocede la barra o la pone en bucle, lee directamente del archivo en caché a `0ms` sin volver a descargar nada. Un recolector automático purga cualquier archivo no accedido en los últimos **30 minutos** o cuando el peso total supera el límite elegido por el usuario (`50 MB` por defecto, hasta `500 MB`).
- **3. Bypass Limpio de C++20 Durante Streaming**:
  - *Enfoque elegido*: En `NativeAudioProcessor` / `AuraAudioPlayer`, se detecta si la pista activa es un flujo de streaming (`isStreamingTrack`). Cuando está en streaming, el buffer PCM pasa en modo directo (*passthrough*) sin ejecutar los filtros C++20, cumpliendo estrictamente la regla de desactivar C++ para streaming por ahora sin tocar su funcionamiento en pistas locales.

---

## 4. Arquitectura Técnica y Flujo de Datos

### Diagrama de Arquitectura del Modo Streaming y Caché

```text
┌────────────────────────────────────────────────────────────────────────────┐
│                   UI: Pestaña "Explorar / Streaming"                       │
│  [Buscador Dual YT / YT Music]  [Insignias de Origen]  [Botón Descargar]   │
└───────────────────┬────────────────────────────────────────┬───────────────┘
                    │                                        │
                    ▼                                        ▼
┌─────────────────────────────────────────┐    ┌─────────────────────────────┐
│     Coordinador de Streaming MVVM       │    │   AuraDownloadService       │
│  • Búsqueda API (YT + YT Music)         │    │  • Guarda en Biblioteca     │
│  • Pre-consulta de Canción Similar      │    │    Local (songs/, videos/,  │
│  • Verificación Red (Wi-Fi vs Datos)    │    │    images/, lyrics/)        │
└─────────┬──────────────────────┬────────┘    └──────────────▲──────────────┘
          │                      │                            │ Reutiliza
          ▼                      ▼                            │ bytes listos
┌───────────────────────┐  ┌──────────────────────────────────┴──────────────┐
│ Resolución de Stream  │  │      Caché Temporal Inteligente (50MB–500MB)    │
│ • yt-dlp (Normalizado │  │  • Pre-descarga por adelantado (Audio + Video)  │
│   para YT y YT Music) │─▶│  • Retención 30 min sin uso (TTL) + Purga LRU   │
│ • Respaldo InnerTube  │  │  • Reproducción instantánea en Bucle / Anterior │
└───────────────────────┘  └─────────────────────────┬───────────────────────┘
                                                     │
                                                     ▼
                           ┌─────────────────────────────────────────────────┐
                           │     Reproductor AuraAudioPlayer + Video Canvas  │
                           │  • Audio Streaming (Bypass de DSP C++20 activo) │
                           │  • Letras Sincronizadas Automáticas (LRCLIB)    │
                           │  • Video Canvas según regla (Wi-Fi/Siempre/Off) │
                           └─────────────────────────────────────────────────┘
```

### Mapeo de Componentes y Estado
- **Política de Video Canvas en Streaming (`StreamingVideoPolicy`)**:
  - `WIFI_ONLY`: Comprueba mediante `ConnectivityManager` y `NetworkCapabilities.TRANSPORT_WIFI` si hay Wi-Fi activo antes de descargar/mostrar el Video Canvas del stream. Si cambia a datos móviles, muestra la carátula oficial sin gastar datos en video.
  - `ALWAYS`: Reproduce y pre-almacena en caché el Video Canvas tanto en Wi-Fi como en datos móviles.
  - `DISABLED`: Desactiva los videos de fondo en streaming (solo audio + carátula + letras), manteniendo intactos los videos de la biblioteca local.
- **Pre-Fetch de Canción Similar (Autoplay Radio)**:
  - Cuando la pista en reproducción alcanza el último tramo (o antes de cambiar de pista cuando no hay más elementos manuales en cola), el coordinador consulta la API de recomendaciones relacionadas con el `videoId` actual, selecciona una pista similar no repetida, invoca a `yt-dlp` para obtener su stream y pre-carga el inicio en la caché temporal para una transición inmediata.
- **Sincronización Documental Completa**:
  - Actualización detallada en español de `README.md`, `READMEAI.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md`, `AGENTS.md` y adición de las nuevas funciones en `chanelog-beta.md` preservando cada línea de información existente.
