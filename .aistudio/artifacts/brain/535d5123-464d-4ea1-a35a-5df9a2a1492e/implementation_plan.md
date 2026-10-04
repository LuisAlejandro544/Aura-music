# Mejoras de Reproducción Inteligente, Recorte de Silencios, Bucle A-B y Ergonomía Visual en Aura Music

Este plan detalla la incorporación de un sistema inteligente de eliminación de silencios iniciales y finales al importar música desde cualquier fuente, un repetidor interactivo de segmento A-B, el desplazamiento automático tipo marquesina para títulos largos en reproducción y la optimización espacial de la barra superior en la pantalla Now Playing.

### Revisión del Usuario y Decisiones Confirmadas

> [!IMPORTANT]
> Todas las decisiones clave han sido alineadas según tus preferencias confirmadas y respetan las reglas arquitectónicas de Aura Music (`minSdk = 26`, almacenamiento estructurado y compatibilidad de 32 y 64 bits).

- **Decisión Confirmada 1 (Eliminación Inteligente de Silencios)**: Se mostrará siempre un interruptor interactivo antes de cada importación (en descargas de TikTok/YouTube, conversión de Video a Música, importación de archivos/carpetas y recepción externa mediante "Abrir con..." / "Compartir con...") para que decidas cuándo recortar los silencios al inicio y al final de la canción.
- **Decisión Confirmada 2 (Repetidor de Segmento A-B)**: Se integrará como una barra compacta e intuitiva junto a la barra de progreso en la pantalla completa Now Playing (con resaltado visual del tramo A-B en la barra de tiempo) y contará además con controles detallados dentro del modal de Efectos de Audio.
- **Decisión Confirmada 3 (Títulos Largos en Movimiento)**: Los títulos largos se desplazarán automáticamente de forma horizontal y fluida (efecto marquesina continuo) en la pantalla principal Now Playing, en el Mini Reproductor flotante y en la fila de la canción activa dentro de las listas de reproducción.
- **Decisión Confirmada 4 (Limpieza de la Barra Superior de Now Playing)**: Se eliminará por completo el bloque de texto superior izquierdo/central ("REPRODUCIENDO AURA / TikTok Music...") marcado en tu captura, liberando espacio visual y dejando una barra superior limpia y despejada.

---

### 1. Concepto General y Valor Principal

- **Qué Hace**:
  1. **Recorte Inteligente de Silencios (Inicio y Final)**: Analiza la energía acústica de las pistas al importarlas o descargarlas para detectar y suprimir silencios muertos al principio y al final de la canción.
  2. **Repetidor de Segmento A-B (A-B Loop)**: Permite fijar al vuelo un punto de inicio `A` y un punto final `B` durante cualquier canción para repetir ese fragmento en bucle ininterrumpido.
  3. **Títulos Dinámicos en Movimiento (Marquee)**: Anima horizontalmente los títulos extensos de la canción en curso para que puedan leerse completos sin cortarse con puntos suspensivos.
  4. **Barra Superior Despejada**: Suprime las etiquetas redundantes de reproducción y álbum en la cabecera de Now Playing para dar aire y protagonismo a los controles.
- **Público Objetivo**: Usuarios móviles que descargan y gestionan su colección musical directamente en su teléfono (desde TikTok, YouTube, videos de galería o gestores externos) y buscan una escucha continua sin silencios molestos ni textos truncados.
- **Valor Clave**: Transiciones musicales instantáneas desde el primer segundo de cada canción, control de repetición de fragmentos favoritos con precisión milimétrica y una interfaz más limpia y legible.

---

### 2. Experiencia de Usuario y Diseño Visual

- **Flujos de Usuario Principales**:
  1. **Flujo de Importación con Interruptor de Silencios**:
     - *Desde TikTok / YouTube / Web y Video a Música*: En el diálogo de previsualización, justo debajo de la opción de Video Canvas, verás el interruptor **"Eliminar silencios al inicio y final"** con icono de onda recortada.
     - *Desde Archivos Locales, Carpetas y "Abrir con..."*: Antes de procesar los archivos seleccionados o compartidos desde otra aplicación, un diálogo o panel de confirmación con el interruptor **"Eliminar silencios al inicio y final"** te permitirá activar o desactivar la limpieza acústica con un toque.
  2. **Flujo del Repetidor A-B**:
     - En Now Playing, justo debajo de las marcas de tiempo de la barra de progreso, una cápsula compacta ofrece los botones **`[A]`**, **`[B]`** y **`[Limpiar]`** (cuando está activo).
     - Al pulsar **`[A]`**, se fija el inicio del segmento en el segundo actual; al pulsar **`[B]`**, se cierra el tramo y el reproductor cicla automáticamente entre `A` y `B`, iluminando visualmente ese rango sobre la barra de progreso con el color de acento de la canción.
     - En la hoja modal de Efectos de Audio (pestaña de Transiciones / Bucle), podrás ajustar finamente los tiempos `A` y `B` en incrementos de `±1s` o desactivar el bucle.
  3. **Flujo de Títulos en Movimiento y Cabecera Limpia**:
     - Al abrir Now Playing, la cabecera superior muestra únicamente el botón de plegar a la izquierda y los botones de acción a la derecha, sin el texto comprimido intermedio.
     - Si el título de la canción supera el ancho de una línea en Now Playing, en el Mini Reproductor o en la canción activa de cualquier lista, el texto se deslizará suavemente de forma horizontal con una breve pausa entre ciclos.

- **Identidad Visual y Tema**:
  - *Dirección Estética*: **Dark Luxury OLED** con acentos neón reactivos armonizados con la carátula o el Video Canvas en reproducción.
  - *Paleta de Colores*: Superficies 100% opacas (`#08080C` / `#12131A`), indicadores activos sincronizados con el color primario dinámico de la pista y alertas de estado claras.
  - *Tipografía y Jerarquía*: Escala tipográfica protegida (`fontScale = 1.0f`), títulos en negrita con desplazamiento horizontal fluido y etiquetas de tiempo en formato `mm:ss`.
  - *Composición de Componentes*: Controles con áreas táctiles cómodas (mínimo `48.dp` en acciones principales) y cápsulas translúcidas de alto contraste para los marcadores `A` y `B`.

---

### 3. Decisiones de Producto y Compromisos Técnicos

- **Decisión 1: Estrategia Híbrida de Detección y Eliminación de Silencios**
  - *Enfoque Elegido*: Análisis acústico rápido en segundo plano (`Dispatchers.IO`) del umbral de energía RMS (en decibelios) en los extremos del audio para localizar el primer y último instante con sonido real, realizando recorte sin pérdida por *Direct Stream Copy* (`MediaExtractor` + `MediaMuxer` ajustado a keyframes de audio) en contenedores `.m4a`/`.mp4`/`.wav`, y respaldado universalmente por límites de recorte nativos (`ClippingConfiguration` en Media3) para cualquier otro formato (`.mp3`, `.flac`, `.ogg`, `.opus`).
  - *Por Qué*: Garantiza que el recorte sea ultrarrápido (menos de 1 segundo sin recodificar toda la canción), preserve el 100% de la calidad original y funcione con absolutamente cualquier formato de audio en Android 8.0+.
  - *Alternativas Descartadas*: Recodificar toda la pista completa con un codificador por software, lo cual tardaría varios segundos por canción, consumiría batería y degradaría la calidad de formatos comprimidos.

- **Decisión 2: Sincronización Reactiva del Bucle A-B en el Motor de Reproducción**
  - *Enfoque Elegido*: Gestionar el estado `ABLoopState(pointAMs, pointBMs, isEnabled)` directamente en el motor de reproducción (`AuraAudioPlayer`) con verificación de alta precisión que reposiciona el cursor a `pointAMs` en cuanto alcanza `pointBMs`, reiniciándose automáticamente al cambiar de canción.
  - *Por Qué*: Permite que el bucle A-B siga funcionando con exactitud incluso si el usuario minimiza Now Playing, navega por la biblioteca o apaga la pantalla del teléfono.

---

### 4. Arquitectura Técnica y Flujo de Datos

- **Diagrama de Arquitectura y Componentes**:

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                         CAPA DE INTERFAZ (JETPACK COMPOSE)                    │
├───────────────────────────┬───────────────────────────┬───────────────────────┤
│   Flujos de Importación   │   Pantalla Now Playing    │  MiniPlayer y Listas  │
│  ┌─────────────────────┐  │  ┌─────────────────────┐  │  ┌─────────────────┐  │
│  │ Interruptor Previo  │  │  │  NowPlayingTopBar   │  │  │  Título Activo  │  │
│  │ "Eliminar Silencios"│  │  │ (Cabecera Despejada)│  │  │ con Marquesina  │  │
│  └──────────┬──────────┘  │  └─────────────────────┘  │  └─────────────────┘  │
│             │             │  ┌─────────────────────┐  │                       │
│             │             │  │ Barra Progreso +    │  │                       │
│             │             │  │ Barra Compacta A-B  │  │                       │
│             │             │  └──────────┬──────────┘  │                       │
└─────────────┼─────────────┴─────────────┼─────────────┴───────────────────────┘
              │                           │
              ▼                           ▼
┌─────────────────────────────────────────┴─────────────────────────────────────┐
│                        VIEWMODEL CENTRAL (MusicViewModel)                     │
│  • Coordina la opción trimSilence en Archivos, Carpetas, Video, Web y Externo │
│  • Expone abLoopState (Punto A, Punto B, Activo) hacia NowPlaying y Efectos   │
└─────────────┬───────────────────────────────────────────┬─────────────────────┘
              │                                           │
              ▼                                           ▼
┌─────────────────────────────────────┐     ┌───────────────────────────────────┐
│    ANALIZADOR Y RECORTE DE AUDIO    │     │   MOTOR MEDIA3 (AuraAudioPlayer)  │
│       (AudioSilenceTrimmer)         │     │  • Vigilancia de ciclo A -> B     │
│  • Escaneo RMS de bordes en IO      │     │  • Recorte y salto de silencios   │
│  • Demuxing / Clipping sin pérdida  │     │  • Sincronización con VideoCanvas │
└─────────────────────────────────────┘     └───────────────────────────────────┘
```

- **Modelo de Datos y Estado**:
  - `ABLoopState`: Contiene `pointAMs: Long?`, `pointBMs: Long?` e `isActive: Boolean` (activo automáticamente cuando `pointAMs != null && pointBMs != null && pointBMs > pointAMs`).
  - `SilenceTrimResult`: Contiene `startOffsetMs: Long`, `endOffsetMs: Long`, `trimmedDurationMs: Long` y el archivo optimizado en `songs/` cuando aplica recorte directo de contenedor.

- **Mapeo de Componentes Interactivos y Manejo de Estado**:
  - **Interruptor de Eliminación de Silencios**: Presente en el diálogo de descarga de enlaces (`DownloadFromLinkDialog`), en el conversor de galería (`VideoToMusicDialog`), en la confirmación previa al importar archivos/carpetas en `ImportMusicScreen` y en la recepción de audios externos (`Abrir con...`). Cuando está activo, invoca el analizador de silencios en `Dispatchers.IO` antes de guardar la pista en Room y `songs/`.
  - **Controles del Repetidor A-B**:
    - Pulsar `Marcar A` guarda la posición actual (`currentPositionMs`) como inicio. Si `B` ya existía y es menor o igual al nuevo `A`, se reinicia `B`.
    - Pulsar `Marcar B` (disponible cuando `currentPositionMs > pointAMs + 500ms`) activa el bucle inmediato entre `A` y `B`.
    - Pulsar `Limpiar A-B` desactiva el bucle y borra ambos marcadores.
  - **Desplazamiento de Títulos (Marquee)**: Aplicado con `basicMarquee` sobre el título en `NowPlayingPlaybackControls`, en `MiniPlayer` y condicionalmente en `TrackListItem` cuando `isCurrentTrack` es verdadero.
