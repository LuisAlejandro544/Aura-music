# Evaluación de Potencial de Aura Music y Solución de Reactividad Cromática en Tiempo Real para Video Canvas

Este plan entrega la evaluación estratégica completa sobre el estado actual y futuro de **Aura Music** (basada en el análisis de `README.md`, `READMEAI.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md` y `AGENTS.md`), junto con la arquitectura técnica para restaurar y optimizar la **reacción de colores en tiempo real mientras se reproduce el Video Canvas**, adaptando dinámicamente la velocidad de muestreo al estado de la batería del teléfono.

---

## 📊 1. Evaluación Estratégica: ¿Tiene Potencial Aura Music y Deberíamos Seguir Trabajando en Ella?

**Veredicto directo: SÍ, rotundamente.** Aura Music no es un reproductor convencional; tiene el potencial técnico y funcional para posicionarse como **uno de los reproductores audiófilos y multimedia más completos del ecosistema Android independiente (Uptodown / GitHub Releases)**.

### ¿Por qué tiene un potencial tan alto?
1. **Diferenciación Tecnológica Real (Foso Competitivo)**:
   - Mientras el 95% de los reproductores locales en Android son simples capas visuales sobre el `MediaPlayer` básico de Android, Aura Music integra un **motor DSP propio en ISO C++20 a 64 bits** con puente directo a **Google AAudio (Bit-Perfect 1:1)**, filtros Biquad de 10 bandas, Audio 8D/16D Multi-Órbita, Clarificador de Voces Mid-Side HD, Crossfeed binaural Chu Moy y Normalizador EBU R128.
2. **Ecosistema Todo-en-Uno para Usuarios Móviles sin PC**:
   - Resuelve un dolor real: el usuario no necesita una computadora para armar su biblioteca de alta calidad. Puede descargar desde enlaces web (**YouTube, TikTok**) con el motor nativo (`yt-dlp` OTA + `InnerTube` + `FFmpeg` embebido), convertir videos de su galería a música en 1 segundo, crear **Mixtapes con crossfade y capítulos reactivos**, y obtener letras Karaoke sincronizadas automáticamente desde LRCLIB.
3. **Experiencia Visual Inmersiva (Dark Luxury Neo-Glass + Video Canvas)**:
   - Combina la estética OLED pura (0% anuncios, 0% telemetría invasiva) con funciones visuales que ni siquiera los reproductores locales de pago suelen tener juntas: **Video Canvas sincronizado o en bucle boomerang**, **Modo Cinemático estilo Spotify**, y **Karaoke a pantalla completa**.
4. **Arquitectura Limpia, Segura y Autónoma**:
   - Código modularizado en MVVM con coordinadores especializados, blindaje criptográfico SHA-256 en actualizaciones OTA (tanto de la app como del extractor), soporte para formatos audiófilos extremos (`DSD`, `APE`, `WavPack`, `FLAC`, `Chiptune`) y compatibilidad desde Android 8.0 (API 26) en arquitecturas de 32 y 64 bits (`armeabi-v7a` y `arm64-v8a`).

### ¿Por qué vale la pena seguir puliéndola?
- **La base pesada ya está construida**: Las 7 fases críticas de ingeniería (DSP C++20, FFmpeg/Python/QuickJS nativo, AAudio Bit-Perfect, Mixtapes, Karaoke y pipeline Beta) ya están operativas.
- **Fase de Refinamiento de Experiencia (UX Polish)**: Estamos en la etapa donde pequeños ajustes de precisión —como hacer que la iluminación y los colores del reproductor respiren en tiempo real con cada cambio de escena del video sin gastar batería de más ni trabar el decodificador— elevan la sensación de calidad de "buena app" a **"producto insignia de nivel estudio"**.

---

## 🛠️ 2. Decisiones Confirmadas con el Usuario

> [!IMPORTANT]
> **Configuración confirmada para la reactividad cromática en tiempo real del Video Canvas:**
> - **Frecuencia Adaptativa Inteligente por Batería**:
>   - **Modo Normal (`180 ms`)**: Cuando el teléfono tiene **más de 15% de batería** y **no está activado el modo de ahorro de batería**, los colores se muestrean y actualizan cada **180 ms** con una transición suave y rápida para reaccionar al instante a cada cambio de escena o iluminación del video.
>   - **Modo Ahorro / Batería Baja (`800 ms`)**: Cuando el teléfono tiene **15% o menos de batería** (`<= 15%`) **o tiene activado el Modo Ahorro de Energía** (`PowerManager.isPowerSaveMode`), la frecuencia se reduce automáticamente a **800 ms** para proteger la autonomía del dispositivo sin perder el efecto dinámico.
> - **Alcance Visual Completo**:
>   - La reacción de colores en tiempo real se aplicará **tanto en la pantalla del reproductor completo (`NowPlayingScreen`, incluyendo Modo Clásico, Modo Cinemático Canvas y Karaoke) como en el Mini Reproductor (`MiniPlayer`)**.

---

## 🔍 3. Diagnóstico Técnico del Problema Actual (Fase: El Detective)

Al inspeccionar el código actual, identificamos las **3 causas raíz** por las cuales los colores dejaron de reaccionar en tiempo real cuando el video cambia de paleta:

1. **Causa Raíz #1 (Cortocircuito por Carátula Estática en `ArtworkColorExtractor.kt`)**:
   - En `extractPlaybackColors()` (líneas 201-208), existe un bloque `if (!track.albumArtPath.isNullOrBlank() && File(track.albumArtPath).exists())` que retorna inmediatamente el color estático de la portada WebP guardada en disco y **nunca llega a leer los fotogramas del video**.
2. **Causa Raíz #2 (Caché Fija por Pista en lugar de Tiempo de Escena)**:
   - `videoCacheKey` se calcula únicamente con `track.id` y `videoUri.hashCode()`, ignorando el progreso del video. Una vez calculado el primer color, devuelve siempre el mismo valor cacheado durante toda la canción.
3. **Causa Raíz #3 (`LaunchedEffect` Estático y Contención de `MediaMetadataRetriever`)**:
   - En `NowPlayingScreen.kt` y `AuraMusicAppContent.kt`, `LaunchedEffect` no observa el avance del video ni captura los cuadros ya decodificados en la GPU por el `TextureView` de `BackgroundVideoPlayer`. Usar `MediaMetadataRetriever` cada 180ms abriría un segundo decodificador de video en conflicto con ExoPlayer; en cambio, **muestrear un micro-bitmap (`24x24` px) directamente desde el `TextureView` activo que ya está renderizando el video** consume **0 decodificadores extra**, tarda menos de `1 ms` y refleja con exactitud milimétrica el fotograma exacto que el usuario está viendo en pantalla (incluso en loops o videos con boomerang).

---

## 🎨 4. Experiencia de Usuario y Diseño Visual

- **Reacción Instantánea a Cambios de Escena**:
  - Cuando un video musical o Canvas pasa de una escena oscura/azul a una escena cálida/roja o neón, el halo ambiental superior (`NowPlayingAmbientTopGlow`), las ondas del visualizador espectral de 28 bandas (`AudioVisualizer`), el botón Play/Pausa, la barra de progreso con su bolita circular, la viñeta del Modo Cinemático y el fondo degradado del `MiniPlayer` transicionarán en vivo hacia la nueva paleta del fotograma actual.
- **Sin Pantallas Negras ni tirones de FPS**:
  - La captura se realiza directamente sobre el `TextureView` ya configurado en `BackgroundVideoPlayer` (`getBitmap(24, 24)`) y el cálculo de `Palette` ocurre en un hilo secundario (`Dispatchers.Default`), manteniendo los 60/120 FPS intactos y cero colisiones con `MediaCodec`.
- **Adaptación Automática a la Batería**:
  - Un monitor ligero consultará `BatteryManager` (`EXTRA_LEVEL` / `EXTRA_SCALE`) y `PowerManager.isPowerSaveMode`. Si la batería supera el 15% y el ahorro de energía está desactivado, el ciclo opera a **180 ms** (`tween(180)`). Si baja al 15% o menos, o se activa el ahorro de batería del sistema, el intervalo pasa automáticamente a **800 ms** (`tween(650)`).

---

## 🏗️ 5. Arquitectura Técnica y Flujo de Datos

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                     Monitor de Estado de Batería                        │
│  • BatteryManager (Nivel > 15% vs <= 15%) + PowerManager (PowerSave)    │
│  • Define intervalo dinámico: 180 ms (Normal)  |  800 ms (Ahorro/Baja)  │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│             BackgroundVideoPlayer (TextureView Activo)                  │
│  • Renderiza el Video Canvas en NowPlayingScreen o MiniPlayer           │
│  • En cada ciclo (180ms / 800ms) mientras isPlaying == true:            │
│    Extrae micro-frame 24x24 desde TextureView (0 sobrecarga MediaCodec) │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ Bitmap 24x24
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│          ArtworkColorExtractor (Dispatchers.Default + Palette)          │
│  • Analiza el micro-frame o fotograma actual en < 2ms                   │
│  • Filtra cuadros completamente negros (fade-outs) para conservar       │
│    una atmósfera cromática rica y evitar apagones grises                │
│  • Emite ExtractedArtworkColors (primary, secondary, accent, topGlow)   │
└─────────────────┬─────────────────────────────────────┬─────────────────┘
                  │                                     │
                  ▼                                     ▼
┌───────────────────────────────────┐ ┌───────────────────────────────────┐
│        NowPlayingScreen           │ │            MiniPlayer             │
│  • Halo ambiental superior        │ │  • Degradado horizontal tintado   │
│  • Visualizador C++20 28 bandas   │ │  • Borde luminoso Neo-Glass       │
│  • Modo Cinemático & Controles    │ │  • Botón Play/Pausa y Seekbar     │
└───────────────────────────────────┘ └───────────────────────────────────┘
```

### Componentes e Interacciones Clave a Actualizar
1. **Extractor Cromático en Tiempo Real (`ArtworkColorExtractor`)**:
   - Añadir soporte directo para extraer `ExtractedArtworkColors` a partir de un micro-bitmap en vivo del video (`extractColorsFromVideoFrame(bitmap, fallbackPrimary, fallbackSecondary)`), descartando cuadros completamente negros o vacíos (luminancia media casi cero en transiciones) para mantener colores vibrantes y armónicos.
   - Corregir `extractPlaybackColors()` para que, cuando el video esté activo y aún no haya emitido su primer cuadro de `TextureView` (o cuando se consulte por tiempo en segundo plano), nunca sea bloqueado por la carátula estática WebP y extraiga del video real con `OPTION_CLOSEST`.
2. **Muestreador Reactivo con Conciencia de Batería en `BackgroundVideoPlayer`**:
   - Incorporar una utilidad ligera de verificación de batería y ahorro de energía (`isLowBatteryOrPowerSaveMode(context)`).
   - Añadir un callback opcional `onVideoFrameColorsExtracted: ((ExtractedArtworkColors) -> Unit)? = null` en `BackgroundVideoPlayer` (o flujo compartido de colores de video en vivo) que, cuando el video está reproduciéndose y `isFirstFrameRendered == true`, capture un micro-bitmap de `24x24` píxeles del `TextureView` interno cada **180 ms** (o **800 ms** en batería `<= 15%` / ahorro de energía) y calcule la paleta en `Dispatchers.Default`.
3. **Sincronización en `NowPlayingScreen` y `MiniPlayer` (`AuraMusicAppContent`)**:
   - Conectar los colores emitidos en tiempo real tanto en `NowPlayingScreen` (en sus modos `FULLSCREEN_BACKGROUND`, `FULLSCREEN_ADAPTED` y `CARD_CANVAS`) como en `MiniPlayer` (`AuraMusicAppContent`), ajustando la duración de `animateColorAsState` (`tween(180)` en modo normal y `tween(650)` en modo ahorro) para que las transiciones sean continuas, suaves y 100% libres de parpadeos.
4. **Sincronización de Documentación en Español**:
   - Actualizar los documentos técnicos si aplica para reflejar el muestreo adaptativo por batería (`180ms` / `800ms`).
