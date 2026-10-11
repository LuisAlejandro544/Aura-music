# Pulido Acústico del Emparejador Auto-Remix (BPM + Energía + `atempo`), Fotos/Descripciones Personalizadas en Álbumes y Artistas, y Sincronización Documental

Esta actualización incorpora un motor de análisis acústico real para el emparejador de **Auto-Remix (`SmartAutoRemixEngine`)** que detecta el **BPM (pulsos por minuto)** y la **energía RMS/dinámica** de las canciones locales para sincronizar matemáticamente el tempo (`atempo`) durante el Mashup o DJ Remix; añade la capacidad de personalizar la **foto en WebP sin pérdida** y la **descripción** de cualquier **Álbum** o **Artista** directamente desde su pantalla de detalle (igual que en las Playlists); y actualiza de forma aditiva `chanelog-beta.md` junto con todos los archivos `.md` del proyecto.

---

## Decisiones Confirmadas por el Usuario

> [!IMPORTANT]
> **Decisiones alineadas con tus respuestas:**
> - **Emparejador de Auto-Remix con Detección Real de BPM, Energía y Sincronización `atempo`**: Se analizarán las muestras de audio locales para estimar el **BPM real (65–180 BPM)** y la **energía acústica (RMS / densidad de ataques)** de cada canción, mostrando los BPM detectados y el porcentaje de afinidad rítmica en el diálogo de Auto-Remix, y ajustando el filtro `atempo` de FFmpeg en base a la relación real de BPM (`bpmA / bpmB`) en lugar de usar solo la proporción de duración.
> - **Edición de Foto Personalizada y Descripción de Álbumes y Artistas desde su Pantalla de Detalle**: Al entrar a un Álbum o Artista en *Tu Biblioteca*, el usuario podrá pulsar el **icono del lápiz** en la cabecera o **tocar la foto/collage** para abrir el diálogo de personalización, elegir una imagen de su galería (comprimida en WebP sin pérdida en `images/` o restaurar el collage de 1 a 4 fotos) y escribir una descripción personalizada, viéndose reflejada tanto en la pantalla de detalle como en las tarjetas de las pestañas *Álbumes* y *Artistas*.
> - **Actualización Aditiva de `chanelog-beta.md` y Archivos `.md`**: Se añadirán todas las novedades en `chanelog-beta.md` sin borrar ningún punto existente, y se sincronizarán `README.md`, `READMEAI.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md` y `AGENTS.md`.

---

## 1. Visión General y Concepto Principal

- **Qué hace**:
  1. **Análisis Rítmico Real en `SmartAutoRemixEngine`**: Extrae una ventana representativa del centro de cada canción local usando `MediaExtractor` + `MediaCodec` (en `Dispatchers.IO` con caché en memoria por `trackId`), detecta los picos de envolvente de subgraves/bajos para calcular el **BPM exacto** (resolviendo octavas `×2` / `÷2` para empatar estilos compatibles como `90 BPM` con `92 BPM` o `180 BPM`) y el **nivel de energía (%)**.
  2. **Sincronización Precisa de Ritmo (`atempo`) en FFmpeg**: En los modos *Mashup (Voz A + Base B)* y *Remix Alternado DJ (Beat-Sync)*, calcula el factor `atempo` a partir de la relación real de BPM entre la Canción A y la Canción B (`targetBpmRatio`), logrando que los bombos y las sílabas vocales encajen a compás.
  3. **Personalización de Álbumes y Artistas (`Album` / `Artist` Custom Metadata)**: Permite asignar una imagen WebP sin pérdida desde la galería (o volver al collage dinámico de 1–4 canciones) y una descripción personalizada para cada Álbum y cada Artista, persistidas localmente en Room Database y en `Android/data/.../files/images/`.
- **Valor Clave**: Convierte el generador de Auto-Remix en una herramienta con criterio musical real (BPM + Energía) y lleva la personalización visual de la biblioteca al mismo nivel en Playlists, Álbumes y Artistas sin cambiar el diseño general de la aplicación.

---

## 2. Experiencia de Usuario y Diseño Visual

- **Flujo 1 — Emparejador Auto-Remix Mejorado (`CreateMixtapeDialog` & `AutoRemixConfigSection`)**:
  1. Al abrir el generador de **Auto-Remix (2)** desde *Tu Biblioteca* o cualquier *Playlist/Álbum/Artista*, el sistema analiza en segundo plano las pistas locales y muestra en cada ranura (*Canción A* y *Canción B*) una insignia clara con su **BPM y Energía** (ej. `124 BPM • Energía 86%`).
  2. Al pulsar **"Elección Inteligente (Auto-Match 2 Pistas)"**, el algoritmo empareja las 2 canciones cuyos BPM (o múltiplos armónicos) y perfiles de energía mejor encajan, explicando el motivo exacto (ej. *"Compatibilidad rítmica del 97% • 122 BPM × 124 BPM (Beat-Sync +1.6%) y energía vocal complementaria"*).
  3. Al pulsar **"Crear Auto-Remix"**, FFmpeg aplica el factor `atempo` calculado por BPM para cuadrar el ritmo de ambas pistas.
- **Flujo 2 — Foto Personalizada y Descripción en Álbumes y Artistas (`PlaylistDetailScreen` & `LibraryScreen`)**:
  1. El usuario toca un **Álbum** o un **Artista** en *Tu Biblioteca* para abrir su vista detallada de canciones.
  2. En la esquina superior derecha aparece el **botón del lápiz (Editar)** y sobre la portada de `108.dp` aparece la insignia de cámara (`AddPhotoAlternate`), exactamente igual que en las Playlists.
  3. Al tocar cualquiera de los dos, se abre el diálogo de edición adaptado para Álbum o Artista: permite elegir una **foto personalizada desde la galería** (o pulsar *"Collage (1-4)"* para volver al collage automático) y editar libremente la **descripción del Álbum o biografía/nota del Artista**.
  4. Al guardar, la nueva portada WebP y la descripción se actualizan al instante tanto en la cabecera de detalle como en las tarjetas de las pestañas **Álbumes** (`AlbumGroupCard`) y **Artistas** (`ArtistGroupCard`).
- **Identidad Visual**:
  - Se conserva 100% intacta la estética **Dark Luxury Neo-Glass OLED** (`SurfaceCard`, `CardBorder`, `TextPrimary`, `TextSecondary`) y todos los iconos existentes.

---

## 3. Decisiones de Producto y Arquitectura

- **Decisión 1: Detector Ligero de BPM y Energía por Envolvente Acústica con Caché**:
  - *Enfoque elegido*: Analizador acústico modular que muestrea una ventana central de la canción local en un hilo secundario (`Dispatchers.IO`), calcula la energía de transitorios (onset envelope) y autocorrelación de intervalos entre pulsos (IOI) en el rango `65–180 BPM`, guardando el resultado en caché para respuestas instantáneas (`<15ms` en consultas sucesivas).
  - *Por qué*: Evita decodificar archivos completos de 5 minutos en el hilo principal, no bloquea la interfaz y ofrece un factor `atempo` musicalmente exacto para FFmpeg.
- **Decisión 2: Persistencia de Foto WebP y Descripción de Álbumes/Artistas en Room**:
  - *Enfoque elegido*: Reutilizar la infraestructura probada de `AppDatabase` + `AppStorageManager` guardando los metadatos personalizados de Álbumes (`id = -2L`) y Artistas (`id = -3L`) indexados por su tipo y nombre canónico, comprimiendo sus portadas a **WebP Lossless** en `images/` y eliminando el archivo WebP antiguo cuando el usuario cambie de foto o restaure el collage automático.
  - *Por qué*: Mantiene 100% de compatibilidad con `PlaylistCoverCollage`, no ensucia el almacenamiento y garantiza persistencia permanente offline.

---

## 4. Arquitectura Técnica y Flujo de Datos

```
┌────────────────────────────────────────────────────────────────────────────┐
│                   1. EMPAREJADOR AUTO-REMIX (BPM + ENERGÍA)                │
│                                                                            │
│  ┌──────────────────────────────┐      ┌────────────────────────────────┐  │
│  │ Pistas Locales (Track)       │ ───► │ Analizador Acústico BPM / RMS  │  │
│  └──────────────────────────────┘      │ • Ventana central PCM (IO)     │  │
│                                        │ • Detección de Onsets (BPM)    │  │
│                                        │ • Caché de perfiles acústicos  │  │
│                                        └───────────────┬────────────────┘  │
│                                                        ▼                   │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ SmartAutoRemixEngine                                                 │  │
│  │ • Auto-Match: Puntúa afinidad de BPM (1:1 / 2:1), Energía y Artista  │  │
│  │ • FFmpeg Graph: Calcula atempo real = clamp(bpmA / bpmB, 0.88..1.14) │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────┐
│           2. FOTO WEBP Y DESCRIPCIÓN EN ÁLBUMES Y ARTISTAS                 │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ Pantalla de Detalle (Álbum id=-2 / Artista id=-3 / Playlist id>0)    │  │
│  │ • Icono Lápiz en cabecera + Botón de Foto sobre Portada (108.dp)     │  │
│  └──────────────────────────────────┬───────────────────────────────────┘  │
│                                     ▼                                      │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ Diálogo de Personalización (Imagen Galería vs Collage + Descripción) │  │
│  └──────────────────────────────────┬───────────────────────────────────┘  │
│                                     ▼                                      │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ AppStorageManager (WebP Lossless en images/) + Persistencia Local    │  │
│  │ • Actualiza reactivamente Detalle + AlbumGroupCard + ArtistGroupCard │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
```
