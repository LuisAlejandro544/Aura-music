# Sistema de Widgets Adaptativos, Color Dinámico y Cuadrícula de Favoritos en Aura Music

Esta actualización transforma el sistema de Widgets de escritorio de **Aura Music**, incorporando extracción cromática reactiva desde la carátula de la canción, adaptación automática al estirar el widget en la pantalla de inicio, reanudación de música en segundo plano sin abrir la ventana de la app, un **segundo Widget independiente de gran formato** para tus **4 Canciones más escuchadas o 4 Playlists**, y un nuevo menú a pantalla completa llamado **"Widget"** dentro de **Ajustes**.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Se han integrado todas tus respuestas y preferencias confirmadas para asegurar una experiencia fluida, modular y fiel al diseño **Dark Luxury Neo-Glass OLED** de Aura Music:

- **Segundo Widget Independiente en el Selector de Android**: Además del Widget de Reproductor principal, el usuario encontrará un segundo Widget dedicado (*"Aura Music • Top 4 & Playlists"*) en el selector de widgets de su teléfono, que muestra en formato grande las **4 canciones más escuchadas** o las **4 playlists principales** con sus portadas WebP o collages.
- **Reanudación Directa en Segundo Plano + Controles Extra (Favorito ❤️ + Progreso)**: Al tocar *Play* (o elegir una canción/playlist del widget) incluso si la app fue cerrada horas antes, la música se reanudará directamente en segundo plano despertando al servicio multimedia y restaurando la última canción y posición guardada, además de incluir botón de **Favorito (Corazón ❤️)** y **barra de progreso** sincronizados en tiempo real.
- **Nuevo Apartado "Widget" en Ajustes (Todo Configurable + Vista Previa)**: Dentro de **Ajustes** se añade la tarjeta navegable **"Widget"**, que abre una pantalla independiente a pantalla completa donde el usuario puede elegir si el Widget de accesos rápidos muestra las **4 Canciones más escuchadas** o las **4 Playlists**, activar o desactivar el **color dinámico de carátula en el Widget** y visualizar una **vista previa interactiva en vivo** de cómo lucirán ambos widgets antes de volver a su escritorio.

---

## 1. Overview & Core Concept

- **Qué hace**:
  1. **Color Dinámico desde la Imagen (Carátula)**: El fondo, el halo superior, el botón principal de reproducción y la barra de progreso del Widget cambian automáticamente de color extrayendo la paleta vibrante de la imagen de portada WebP (o arte procedural) de la canción actual, manteniendo legibilidad perfecta y estética oscura OLED.
  2. **Adaptación Automática al Estirarlo (Responsive Sizing)**: El Widget reproductor detecta en tiempo real sus dimensiones en `dp` (`onAppWidgetOptionsChanged`) cuando el usuario lo estira horizontal o verticalmente en su pantalla de inicio, conmutando entre **Modo Compacto (4x1)**, **Modo Mediano con Progreso y Favorito (4x2)** y **Modo Estudio Expandido (4x3 / Cuadrado Grande)** con carátula de gran formato y fila completa de transporte.
  3. **Reanudación Persistente en Segundo Plano**: La última pista activa, la cola de reproducción y el segundo exacto donde quedó la música se guardan de forma persistente. Al tocar *Play*, *Siguiente*, *Favorito* o cualquiera de las 4 canciones/playlists desde el escritorio con la app cerrada, un motor de arranque en frío restaura la sesión desde la base de datos local y empieza a sonar al instante sin interrumpir al usuario abriendo la app.
  4. **Segundo Widget Independiente "Top 4 Canciones / 4 Playlists"**: Un widget de buen tamaño (4x2 redimensionable a 4x3) que presenta una cuadrícula 2x2 de acceso directo a las **4 canciones que más escuchas** (ordenadas por `playCount`) o a tus **4 Playlists** (con su portada personalizada o collage automático de hasta 4 fotos). Tocar cualquiera de los 4 elementos inicia su reproducción inmediata en segundo plano.
  5. **Nuevo Apartado "Widget" en Ajustes**: Menú a pantalla completa dentro de `Ajustes` con vista previa en vivo, selector de contenido del segundo widget (*4 Canciones más escuchadas* vs *4 Playlists*), interruptor de color dinámico en widgets y botón de sincronización instantánea.
- **Valor Principal**: Control total de tu música y acceso inmediato a tus canciones y listas favoritas desde la pantalla de inicio del teléfono, sin esperas ni pantallas intermedias.

---

## 2. User Experience & Visual Design

- **Flujos de Usuario Clave**:
  1. **Reacción de Color y Estiramiento en Escritorio**: Al reproducir cualquier canción, el Widget extrae los colores dominantes y vibrantes de la imagen de carátula y pinta su fondo con un degradado diagonal Neo-Glass con borde luminoso sutil. Si el usuario mantiene presionado el widget en su launcher y lo estira hacia abajo, el widget pasa automáticamente de una barra horizontal compacta a una tarjeta amplia con barra de progreso en vivo, tiempos (`mm:ss`), botón de **Favorito (❤️)** e insignia técnica (`BIT-PERFECT AAUDIO` / `C++20 DSP`).
  2. **Reanudación sin Abrir la App**: Si el teléfono reinició o Android cerró la app por falta de RAM, el Widget conserva en pantalla la última canción que estabas escuchando con su carátula y color. Al tocar **Play**, el servicio `AuraMediaPlaybackService` despierta en segundo plano, recarga la canción en el minuto donde la dejaste y comienza a sonar de inmediato.
  3. **Uso del Segundo Widget (Top 4 Canciones / 4 Playlists)**: Al agregar el segundo widget a la pantalla de inicio, verás una cabecera con acceso rápido a reproducción y una cuadrícula 2x2 con las 4 canciones más escuchadas (o tus 4 playlists con su collage). Un toque sobre cualquier tarjeta reproduce esa canción o lista al instante.
  4. **Personalización en `Ajustes > Widget`**: Al entrar a `Ajustes`, el usuario toca el nuevo apartado **"Widget"**. Allí ve una **Vista Previa en Vivo** de ambos widgets con los colores de su canción actual, puede alternar entre *"Mostrar 4 Canciones más escuchadas"* o *"Mostrar 4 Playlists"*, activar/desactivar el *"Color dinámico de la imagen en Widgets"* y elegir la opacidad/intensidad del tinte.
- **Identidad Visual y Estética**:
  - *Dirección Estética*: **Dark Luxury Neo-Glass OLED** armonizado con los colores reales de la carátula de cada canción.
  - *Paleta y Contraste*: El color extraído de la imagen se combina con una base oscura profunda (`#0B0F19` a `#161F33`) para garantizar que el título blanco (`#FFFFFF`), el artista (`#CBD5E1`) y los controles mantengan un contraste impecable sin importar si la portada es muy clara o muy oscura.
  - *Áreas Táctiles*: Todos los botones interactivos del widget (*Anterior*, *Play/Pausa*, *Siguiente*, *Favorito ❤️* y las *4 tarjetas de canciones/playlists*) cumplen con áreas táctiles cómodas y bien espaciadas.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisión 1: Renderizado de Fondo Degradado Dinámico por Bitmap en `RemoteViews`**
  - *Enfoque Elegido*: Como los `RemoteViews` de Android tienen limitaciones para crear degradados complejos por código en todas las versiones desde Android 8.0 (API 26) hasta Android 16 (API 36), generaremos en un hilo secundario un lienzo vectorial redondeado (`Canvas` + `LinearGradient` + `RadialGradient` con los colores extraídos de la imagen por `Palette`) para el fondo del widget, el botón de Play y la barra de progreso.
  - *Por qué*: Garantiza que el cambio de color según la imagen funcione al 100% en cualquier teléfono Android (desde Android 8.0 en adelante) con bordes curvos suaves, halo de neón y cero fallos visuales.
- **Decisión 2: Reanudación en Frío Silenciosa vía `MediaSessionService` + Persistencia de Estado**
  - *Enfoque Elegido*: Persistir cada cambio de canción, estado de favorito y posición periódica en el almacén local de estado del widget (`WidgetStateStore`). Cuando el usuario toca un botón del widget y el motor en memoria no está activo, el `BroadcastReceiver` arranca `AuraMediaPlaybackService` en segundo plano enviando la acción solicitada (`RESUME_LAST`, `PLAY_TRACK_ID`, `PLAY_PLAYLIST_ID`, `TOGGLE_FAVORITE`) sin lanzar `MainActivity`.
  - *Por qué*: Evita que la app se abra de golpe en la cara del usuario cuando solo quería darle Play o cambiar de canción desde su pantalla de inicio.
- **Decisión 3: Adaptación Multi-Tamaño por `OPTION_APPWIDGET_MIN_HEIGHT` y `MIN_WIDTH`**
  - *Enfoque Elegido*: Soportar 3 niveles de diseño adaptativo dentro del Widget de Reproductor según la altura y anchura al estirarlo:
    - **Compacto (`alto < 110dp`)**: Carátula, insignia, título/artista, botón Favorito ❤️ y controles principales.
    - **Mediano (`110dp <= alto < 175dp`)**: Añade barra de progreso visual sincronizada, tiempo actual/total y fila ampliada de controles (Favorito ❤️, Anterior, Play/Pausa iluminado, Siguiente).
    - **Expandido (`alto >= 175dp`)**: Diseño tipo estudio con portada destacada, metadatos completos, insignia audiófila y botonera de gran formato.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Diagrama de Arquitectura y Flujo de Componentes**:

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│                     CAPA DE AJUSTES & VISTA PREVIA (UI)                      │
│  [SettingsScreen] ──► [SettingsSubScreen.WIDGETS]                            │
│                        ├── Selector: 4 Canciones Top vs 4 Playlists          │
│                        ├── Switch: Color Dinámico desde Imagen de Carátula   │
│                        └── Vista Previa Interactiva en Vivo de ambos Widgets │
└──────────────────────────────────────┬───────────────────────────────────────┘
                                       │ Guarda preferencias y refresca
                                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                  ALMACÉN & SINCRONIZADOR DE WIDGETS (DATA)                   │
│  [WidgetStateStore / WidgetDataSynchronizer]                                 │
│   ├── Persiste última canción, posición (ms), favorito y colores de imagen   │
│   ├── Caché de las 4 Canciones más escuchadas (Room: playCount DESC)         │
│   └── Caché de las 4 Playlists principales + Collage WebP renderizado        │
└───────────────┬──────────────────────────────────────────────┬───────────────┘
                │                                              │
                ▼                                              ▼
┌─────────────────────────────────────────┐  ┌─────────────────────────────────┐
│   WIDGET 1: REPRODUCTOR ADAPTATIVO      │  │  WIDGET 2: TOP 4 / PLAYLISTS    │
│   [AuraMusicWidgetProvider]             │  │  [AuraLibraryWidgetProvider]    │
│   ├── Reacciona al color de la imagen   │  │  ├── Cuadrícula 2x2 de buen     │
│   ├── Se adapta al estirarlo:           │  │  │   tamaño (4 Canciones o      │
│   │   • Compacto (4x1)                  │  │  │   4 Playlists configurables) │
│   │   • Mediano con Progreso y ❤️ (4x2) │  │  ├── Reacciona al color activo  │
│   │   • Expandido Estudio (4x3)         │  │  └── Toque directo reproduce    │
└───┴──────────────────┬──────────────────┘  └───┴──────────────┬──────────────┘
                       │                                        │
                       └───────────────────┬────────────────────┘
                                           │ Intents en 2do Plano (Sin abrir UI)
                                           ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│               MOTOR DE REANUDACIÓN EN SEGUNDO PLANO (PLAYBACK)               │
│  [AuraMediaPlaybackService] + [AuraAudioPlayer] + [Room Database]            │
│   ├── Si la app está abierta: ejecuta acción al instante en memoria          │
│   └── Si la app estaba cerrada: restaura pista/playlist desde Room,          │
│       posiciona el segundo guardado y reanuda el audio en segundo plano      │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Modelo de Datos y Estado Persistente**:
  - `WidgetConfig`: Modelo inmutable persistido en preferencias con:
    - `collectionMode`: Enum (`TOP_SONGS` para las 4 canciones más escuchadas, `PLAYLISTS` para las 4 playlists).
    - `isDynamicColorEnabled`: Booleano (activo por defecto) para teñir el fondo y acentos de los widgets con los colores de la imagen de la canción actual.
    - `showProgressInWidget`: Booleano para mostrar la barra de progreso y tiempos.
  - `WidgetPersistedPlaybackState`: Guarda el `trackId`, `title`, `artist`, `albumArtPath`, `isFavorite`, `positionMs`, `durationMs`, `primaryColorInt` y `secondaryColorInt` de la última canción reproducida, permitiendo que el widget nunca pierda su diseño ni su capacidad de reanudar.
- **Mapeo de Interacciones y Manejadores**:
  - **Estiramiento (`onAppWidgetOptionsChanged`)**: Calcula `minWidth` y `minHeight` de las opciones del widget y selecciona la plantilla correspondiente (`Compact`, `Medium`, `Expanded`), regenerando el bitmap de fondo con las proporciones exactas para evitar deformaciones.
  - **Botón Favorito (❤️) en el Widget**: Invoca `ACTION_WIDGET_FAVORITE`, alterna `isFavorite` en la base de datos Room y en `AuraAudioPlayer`, actualiza el icono del corazón en rojo intenso (`#EF4444`) al instante y sincroniza el estado con `NowPlayingScreen`.
  - **Toque en cualquiera de las 4 Canciones o 4 Playlists del Segundo Widget**: Envía `ACTION_WIDGET_PLAY_ITEM` con el ID de la canción o de la playlist; inicia la cola correspondiente en `AuraAudioPlayer` en segundo plano y actualiza ambos widgets simultáneamente.
