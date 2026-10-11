# Importador de Playlists (Spotify & YT Music), Búsqueda en SoundCloud y Sincronización Documental

Este plan define la arquitectura modular para incorporar un **Importador Universal de Playlists** desde **Spotify** y **YouTube Music / YouTube** (sin requerir inicio de sesión ni claves de API externas), la expansión del motor de **Streaming Híbrido** en la pestaña **Explorar** con soporte nativo para **SoundCloud**, y la actualización aditiva de toda la documentación técnica del proyecto (`README.md`, `READMEAI.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md`, `AGENTS.md` y `chanelog-beta.md`).

---

## Decisiones Confirmadas por el Usuario

> [!IMPORTANT]
> Las siguientes decisiones fueron confirmadas directamente por el usuario y guían toda la implementación:

- **Modo de Guardado de Playlists Importadas**: El usuario podrá elegir en el diálogo de importación entre **⚡ Modo Streaming Rápido** (crea la Playlist al instante en 1–2 segundos con su portada oficial en WebP y pistas listas para reproducir en streaming bajo demanda) o **📥 Descargar Todo Offline** (crea la Playlist y descarga en segundo plano todas las canciones con su carátula WebP y opción de Video Canvas).
- **Puntos de Acceso al Importador de Playlists**: Accesible desde **3 lugares clave**:
  1. **Tu Biblioteca** (pestaña *Playlists*, junto a la creación de listas).
  2. **Importar Música** (tarjeta dedicada de importación de Playlists de Spotify / YT Music).
  3. **"Compartir con..." / "Abrir con..."** (al compartir un enlace de playlist de Spotify o YouTube Music desde otra app, se abrirá automáticamente el modal del Importador de Playlists con la URL precargada).
- **Integración de SoundCloud en `Explorar`**:
  - **Integrado en el filtro `Todo`**: Combina e intercala inteligentemente resultados de **YouTube Music (`🎵 YT MUSIC`)**, **YouTube (`▶️ YOUTUBE`)** y **SoundCloud (`☁️ SOUNDCLOUD`)**.
  - **Filtro propio `☁️ Solo SoundCloud`**: Chip dedicado en la barra superior de filtros para buscar exclusivamente remixes, electrónica, phonk, sets de DJ y pistas de SoundCloud.

---

## 1. Visión General y Concepto Principal

- **Qué hace**:
  1. **Importador de Playlists de Spotify y YouTube Music**: Toma cualquier enlace público de playlist de Spotify (`open.spotify.com/playlist/...`) o de YouTube Music / YouTube (`music.youtube.com/playlist?list=...`, `youtube.com/playlist?list=...`), extrae su título, autor, portada oficial y listado completo de canciones, empareja automáticamente las pistas de Spotify con el catálogo de streaming y permite guardarlas como Playlist en Modo Streaming instantáneo o descargarlas al almacenamiento local.
  2. **Búsqueda y Streaming en SoundCloud**: Amplía la pestaña **Explorar** con un motor de búsqueda y resolución para SoundCloud (vía API pública de SoundCloud / extracción web + respaldo nativo en `yt-dlp`), soportando reproducción inmediata con caché inteligente de 30 minutos (50–500 MB), letras sincronizadas `.LRC` desde LRCLIB, Radio de canciones similares y descarga permanente en 1 toque.
  3. **Actualización Documental Completa**: Actualiza todos los documentos `.md` y `chanelog-beta.md` preservando el 100% de la información existente y añadiendo las nuevas capacidades.
- **Valor Clave**: Permite a cualquier usuario migrar sus bibliotecas de Spotify y YouTube Music en segundos desde su teléfono móvil sin PC y sin arriesgar sus cuentas personales, además de desbloquear el catálogo de música independiente y remixes de SoundCloud.

---

## 2. Experiencia de Usuario y Diseño Visual

- **Flujos Principales del Usuario**:
  1. **Flujo de Importación de Playlist (Spotify / YT Music)**:
     - El usuario pulsa **"Importar Playlist"** en *Tu Biblioteca > Playlists*, en *Importar Música*, o comparte un enlace desde Spotify/YouTube Music hacia Aura Music.
     - Se despliega el modal deslizable **Importador de Playlists** con estética *Dark Luxury Neo-Glass OLED*, que detecta automáticamente la plataforma del enlace (**Verde Esmeralda `#1DB954`** para Spotify, **Rojo/Rosa Carmesí `#FF0033`** para YouTube Music).
     - Al pulsar **"Analizar Playlist"**, muestra la tarjeta de previsualización con la portada oficial, nombre editable de la Playlist, creador, número total de canciones encontradas y la lista desplazable de pistas detectadas.
     - El usuario elige el modo de importación:
       - **⚡ Crear Playlist en Modo Streaming (Instantáneo)**: Guarda la portada en WebP Lossless, registra las pistas de streaming en la Playlist y la abre lista para reproducir.
       - **📥 Descargar Playlist Completa (Offline)**: Crea la Playlist y encola la descarga de sus pistas en segundo plano mediante el servicio de descargas con progreso en vivo.
  2. **Flujo de Búsqueda y Reproducción de SoundCloud en `Explorar`**:
     - En la pestaña **Explorar**, la fila de chips de filtro muestra: `Todo`, `🎵 Solo YT Music`, `▶️ Solo YouTube` y **`☁️ Solo SoundCloud`**.
     - Los resultados provenientes de SoundCloud lucen una insignia distintiva en **Naranja Vibrante (`#FF5500` • `☁️ SOUNDCLOUD`)** sobre la miniatura.
     - Al tocar un resultado de SoundCloud, se resuelve el stream de audio de alta calidad, se activa la pre-descarga no lineal en la caché de 30 minutos, se buscan sus letras `.LRC` y el reproductor adapta sus colores cada 1 segundo desde la portada de SoundCloud.
- **Identidad Visual y Tema**:
  - Mantiene intacta la estética **Dark Luxury Neo-Glass OLED** (`#090A0F` / `#12151E` 100% opaco), controles con área táctil mínima de `48.dp` y marquesina fluida (`basicMarquee`) sin alterar ningún diseño existente que no haya sido solicitado.

---

## 3. Decisiones Técnicas y Estrategia sin Claves Externas

- **Decisión 1: Extracción de Playlists de Spotify y YT Music sin Login ni `.env`**
  - *Enfoque Elegido*:
    - **Para YouTube Music / YouTube**: Consulta directa al endpoint `browse` de InnerTube (`VL` + `playlistId` con continuaciones) combinada con respaldo en `yt-dlp --flat-playlist --dump-single-json` para obtener todas las pistas en milisegundos.
    - **Para Spotify**: Extracción multicapa desde el endpoint público de Embed/oEmbed (`open.spotify.com/embed/playlist/{id}` y lectura del estado hidratado JSON `__NEXT_DATA__` / `initial-state`) complementado con extractor WebView invisible si la playlist requiere hidratación dinámica. Cada pista de Spotify (`Título + Artista + Duración`) se vincula al instante mediante emparejamiento en paralelo con la API de búsqueda de streaming.
  - *Por qué*: Cumple la regla inviolable de cero archivos `.env`, cero claves privadas y cero inicio de sesión obligatorio.
- **Decisión 2: Persistencia de Pistas de Streaming dentro de Playlists Locales**
  - *Enfoque Elegido*: Permitir que una Playlist de Room almacene tanto pistas locales descargadas como pistas en Modo Streaming (identificadas por su URL/identificador de plataforma en `TrackEntity`), de modo que al abrir la Playlist en *Tu Biblioteca* y tocar cualquier canción en Modo Streaming, el coordinador resuelva el flujo al vuelo usando la caché de 30 minutos o `InnerTube` / `yt-dlp` tal como ocurre en la pestaña *Explorar*.
- **Decisión 3: Motor de Búsqueda y Resolución de SoundCloud**
  - *Enfoque Elegido*: Integrar un cliente de búsqueda rápida para SoundCloud (consulta de catálogo web / API v2 con hidratación automática de `client_id` público y respaldo directo en `yt-dlp` con `scsearch:`), resolviendo los flujos HTTP/MP3/Opus directos para ExoPlayer y `AuraDownloadService`.

---

## 4. Arquitectura Técnica y Flujo de Datos

```text
┌────────────────────────────────────────────────────────────────────────────┐
│                        CAPA DE INTERFAZ (JETPACK COMPOSE)                  │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────┐  │
│  │ StreamingExploreScreen│  │ ImportPlaylistDialog │  │  LibraryScreen & │  │
│  │ (Chips: Todo, YTM,   │  │ (Previsualización +  │  │ ImportMusicScreen│  │
│  │  YT, ☁️ SoundCloud)  │  │  Streaming vs Offline│  │ (Botones acceso) │  │
│  └──────────┬───────────┘  └──────────┬───────────┘  └────────┬─────────┘  │
└─────────────┼─────────────────────────┼───────────────────────┼────────────┘
              │                         │                       │
              ▼                         ▼                       ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   COORDINADORES MVVM (VIEWMODEL DELEGATES)                 │
│  ┌─────────────────────────────┐         ┌──────────────────────────────┐  │
│  │   StreamingModeCoordinator  │◄───────►│  PlaylistImportCoordinator   │  │
│  │ (Búsqueda Tri-Plataforma,   │         │ (Orquestación de análisis,   │  │
│  │  Reproducción, Caché y      │         │  creación en Room y cola de  │  │
│  │  Descarga en 1 Toque)       │         │  descarga en 2do plano)      │  │
│  └──────────────┬──────────────┘         └──────────────┬───────────────┘  │
└─────────────────┼───────────────────────────────────────┼──────────────────┘
                  │                                       │
                  ▼                                       ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                 CAPA DE DATOS, EXTRACTORES Y MOTORES NATIVOS               │
│  ┌─────────────────────────┐  ┌─────────────────────────┐  ┌────────────┐  │
│  │  SoundCloudApiClient    │  │ ExternalPlaylistImporter│  │ Room DB &  │  │
│  │ (Búsqueda rápida API v2 │  │ (Parser Spotify Embed + │  │ AppStorage │  │
│  │  + Resolución Stream    │  │  InnerTube Browse VL +  │  │ (Portadas  │  │
│  │  directo / yt-dlp)      │  │  yt-dlp --flat-playlist)│  │  WebP)     │  │
│  └─────────────────────────┘  └─────────────────────────┘  └────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
```

### Mapeo de Interacciones y Estado
1. **Detección Inteligente de Enlaces Entrantes**: Si el usuario comparte una URL que contiene `/playlist` o `list=PL`/`list=OLAK` desde Spotify o YouTube Music, `IncomingMediaCoordinator` y `GlobalDialogsHost` abren directamente el **Importador de Playlists** en lugar del descargador de una sola pista.
2. **Reproducción Transparente de Pistas de Streaming en Playlists**: Si el usuario importa una Playlist en **Modo Streaming Rápido** y entra a ella desde *Tu Biblioteca*, al pulsar cualquier pista el reproductor detecta que es una pista de streaming y delega su resolución y almacenamiento en caché temporal a `StreamingModeCoordinator`, mostrando los indicadores de carga en el botón Play/Pausa y la portada.
3. **Sincronización de Documentación**: Se actualizarán `README.md`, `READMEAI.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md`, `AGENTS.md` y `chanelog-beta.md` en español, conservando íntegramente todo su contenido actual y documentando el Importador de Playlists (Spotify / YT Music) y la integración de SoundCloud.
