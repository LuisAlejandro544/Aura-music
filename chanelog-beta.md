# 🌌 Aura Beta — Changelog Oficial (`v0.1.0-beta.1a` • Codename: **Nebula**)

> **Versión:** `v0.1.0-beta.1a` (`NEBULA-00101A` • Build `#100101`)  
> **Nombre en Launcher:** `Aura Beta`  
> **Identificador de Paquete (`Android/data/`):** `com.auramusic.beta`  
> **Codename de la Serie Beta:** **Nebula**  
> **Requisitos del Sistema:** Android 8.0 Oreo (API 26) hasta Android 15+ (API 36)  
> **Estado de Depuración:** Producción Beta Limpia *(Sin Aura Monitor ni LeakCanary; con Actualizador OTA de `yt-dlp` 100% activo)*

---

## 🚀 Todo lo que ofrecemos en Aura Beta (`v0.1.0-beta.1a`)

**Aura Beta** es un reproductor musical audiófilo y centro multimedia todo-en-uno para Android, diseñado desde cero para que puedas disfrutar, personalizar, descargar y masterizar tu música directamente desde tu teléfono móvil sin necesidad de una PC.

### 🎛️ 1. Motor de Audio Nativo en ISO C++20 (64-bit DSP) & Modo Bit-Perfect
- **Ecualizador Paramétrico de 10 Bandas ISO Integrado en Modal**:
  - Bandas de precisión calibradas con nombres acústicos intuitivos: `31 Hz (Subgraves)`, `62 Hz (Bajos)`, `125 Hz (Graves)`, `250 Hz (Cuerpo)`, `500 Hz (Medios Bajos)`, `1 kHz (Voces)`, `2 kHz (Claridad)`, `4 kHz (Presencia)`, `8 kHz (Brillo)` y `16 kHz (Aire / Agudos)`.
  - Filtros Bi-cuadráticos (*Peaking Biquads*) en coma flotante de 64 bits con limitador suave anti-clipping (`-15 dB` a `+15 dB`), perfiles rápidos (*Rock, Pop, Electrónica, Jazz, Acústico, Bass Boost, Plano*) y refuerzo de subgraves a 60 Hz.
  - Accesible al instante desde el **Mini Reproductor** y desde **Now Playing** sin pantallas aisladas que interrumpan tu navegación.
- **Clarificador de Voces HD (`VocalClarityProcessor` Mid-Side en C++20)**:
  - Aísla el canal vocal central (*Mid*) del acompañamiento instrumental (*Side*), limpia la turbidez en 180 Hz y realza la articulación vocal en `2.8 kHz` y `5.5 kHz`.
- **Audio Espacial 8D y 16D Multi-Órbita Binaural en C++20**:
  - **Modo 8D Clásico**: Rotación orbital de 360° con simulación de sombra acústica de la cabeza (*Head Shadow Filtering*).
  - **Modo 16D Multi-Órbita**: Doble capa contra-rotatoria independiente (bajos estables en órbita interna y voces/agudos en trayectoria figura-8 con micro-retardo Haas).
- **Suite Reverb 100% C++20 (`ReverbProcessor`)**:
  - Simulación acústica de espacios reales (*Estudio, Sala Mediana, Club En Vivo, Gran Hall, Catedral, Eco Espacial*) libre de silencios o bloqueos del sistema, con ajuste manual de tamaño de sala, decaimiento y mezcla.
- **Normalización de Volumen Inteligente (Loudness Normalizer EBU R128 / Nivel Spotify)**:
  - Iguala automáticamente la energía acústica entre canciones de distintas épocas o fuentes con 3 modos: *Sutil (-18 LUFS)*, *Estándar (-14 LUFS)* y *Alto (-11 LUFS)*.
- **Automix Inteligente DJ con Curva de Ecualización en X (`DjAutomixFilter`)**:
  - Detecta el final acústico de la pista para evitar silencios muertos y atenúa progresivamente los subgraves (`<120 Hz`) de la canción saliente para evitar choques de bajos con la canción entrante.
- **Modo Bit-Perfect 1:1 & Salida Google AAudio de Ultra-Baja Latencia (`dsp_bitperfect.h`)**:
  - Bypass directo 1:1 sin alteración matemática para DACs USB externos y audífonos Hi-Res, con presets *Nativo 1:1*, *Hi-Res 96 kHz / 24-bit* y *Ultra Hi-Res 192 kHz / 32-bit Float*, además de telemetría en vivo en la ficha técnica de cada canción.
- **Decodificación Nativa de Formatos Especiales y Audiófilos**:
  - Soporte directo para **DSD** (`.dsf`, `.dff`), **Monkey's Audio** (`.ape`), **WavPack** (`.wv`), **ALAC / AIFF** (`.alac`, `.aiff`), **Musepack** (`.mpc`) y módulos **Chiptune / Tracker** (`.mod`, `.xm`, `.it`, `.s3m`), además de `FLAC`, `WAV`, `MP3`, `M4A`, `OGG` y `OPUS`.

---

### 🎬 2. Video Canvas Dinámico, Modo Cinemático (Estilo Spotify) y Armonización Cromática
- **Selección de Diseños del Reproductor (`NowPlayingDesignMode`)**:
  - 🎛️ **Modo Clásico**: Carátula central 1:1, visualizador espectral de 28 bandas en C++20, barra de balance estéreo L/R y atajos inferiores.
  - 🎬 **Modo Cinemático Canvas (Estilo Spotify)**: Despeja más del 70% de la pantalla para el video en movimiento, con viñeta ambiental reactiva a los colores del video, minicarátula lateral, verso de Karaoke flotante y controles ergonómicos con selector circular clásico ("bolita").
  - ✨ **Modo Automático Inteligente**: Alterna automáticamente al diseño Cinemático cuando la pista cuenta con Video Canvas y al diseño Clásico en pistas de solo carátula.
- **4 Modos de Visualización y Encuadre de Video (`VideoDisplayMode`)**:
  - *Fondo Completo (Rellenar / Recortar)*, *Fondo Completo (Adaptado Horizontal 16:9 sin recortar rostros)*, *Lienzo en Carátula (1:1)* y *Solo Carátula*.
- **Bucles Infinitos en FFmpeg (Seamless Crossfade & Efecto Boomerang) y Saltos en 0ms**:
  - Para loops cortos (≤20s), permite elegir entre fundido cruzado (`xfade`) o **Efecto Boomerang / Ping-Pong** (`reverse + concat`).
  - Para videos largos sincronizados (>20s), estructura fotogramas clave cada 1 segundo (GOP corto a 30fps) y elimina la pista de audio redundante (`-an`) para ahorrar hasta un 85% de espacio en disco y lograr saltos temporales (`seekTo`) instantáneos sin pantallas negras.

---

### 🔗 3. Descargas de YouTube / TikTok, Video a Música, Mixtapes y Actualizador OTA (`yt-dlp`)
- **Descarga Directa desde YouTube, TikTok y Enlaces Web en Segundo Plano (`AuraDownloadService`)**:
  - Arquitectura resiliente de 3 niveles (**InnerTube Nativo `<300ms`** + **Bypass Invidious** + **WebView Móvil con cascada de carátulas**) + motor **CPython 3.11 / QuickJS / `yt-dlp`**.
  - **Acelerador HTTP Range Multi-Bloque**: Descarga pistas y videos a máxima velocidad (10–40 MB/s) eliminando el estrangulamiento de red, con vinculación automática de **Video Canvas en 480p** y carátula **WebP Lossless**.
  - Continúa descargando en segundo plano con notificación nativa interactiva aunque salgas de la aplicación.
- **Actualizador Automático en Caliente de `yt-dlp` (OTA con Verificación SHA-256)**:
  - Integrado y activo en **Aura Beta**: verifica al iniciar la app (y desde *Ajustes > Motores Nativos & Actualizador yt-dlp OTA*) si existe una nueva versión oficial de `yt-dlp`, descargándola y validándola criptográficamente con `SHA2-256SUMS` para que las descargas web nunca dejen de funcionar sin necesidad de reinstalar el APK.
- **Conversor "Video a Música" 3 en 1 (Sin PC)**:
  - Convierte cualquier video de tu galería en una canción `.m4a` en ~1 segundo sin pérdida de calidad, extrayendo su carátula en WebP y vinculando el Video Canvas automáticamente.
- **Puente Inteligente "Abrir con..." y "Compartir con..."**:
  - Recibe audios, videos o enlaces directamente desde SnapTube, navegadores, Telegram, WhatsApp o gestores de archivos.
- **Mixtape Maker / Fusión de Canciones con Crossfade Continuo en FFmpeg**:
  - Une varias canciones en una sola sesión continua (`.m4a`) con transiciones suaves de 3s, 5s, 8s o 10s, cambiando automáticamente la carátula, el título, el Video Canvas y la letra sincronizada en cada capítulo del mix.
- **Eliminación Inteligente de Silencios (`AudioSilenceTrimmer`) & Repetidor A-B**:
  - Interruptor previo a cada importación o descarga para recortar silencios al inicio y final (`-42 dB` RMS), y barra de bucle A-B con ajuste fino de `±1s`.

---

### 🎤 4. Karaoke Sincronizado (.LRC / .TXT), Auriculares y Gestión Transparente
- **Karaoke Automático e Inmersivo a Pantalla Completa (`LRCLIB`)**:
  - Descarga automática y gratuita de letras sincronizadas `.lrc`, buscador interactivo con insignia `⭐ OFICIAL / RECOMENDADA` en primer lugar, importador de archivos `.lrc`/`.txt` locales y **Modo Karaoke a Pantalla Completa** estilo *Apple Music Sing / Spotify* con salto táctil por verso.
- **Suite Acústica para Auriculares**:
  - Filtro **Crossfeed Chu Moy / Bauer** en C++20 (se activa exclusivamente al conectar auriculares por cable, USB-C o Bluetooth), **Balance Estéreo Fino L/R**, protección contra desconexiones (*Becoming Noisy Guard*), memoria de volumen independiente y mapeo de gestos de botones.
- **Playlists con Collage Dinámico (1 a 4 Fotos), Widgets y Android Auto**:
  - Portadas personalizadas en WebP o collage automático de 1, 2, 3 o 4 carátulas; widget interactivo para pantalla de inicio con insignia en vivo y compatibilidad con Android Auto.
- **Almacenamiento Limpio y Transparente en `Android/data/com.auramusic.beta/files/`**:
  - Organizado en `songs/`, `images/`, `lyrics/`, `metadata/` y `videos/`, con panel dedicado en **Ajustes > Medios & Almacenamiento** para revisar cuántos KB/MB ocupa cada portada o video y liberar espacio con un toque.

---

## 📦 Guía de Descarga: ¿Para qué sirve cada APK disponible?

En cada lanzamiento de **Aura Beta (`Nebula`)** generamos **3 archivos APK exclusivos para dispositivos móviles** (eliminando arquitecturas innecesarias de PC para máxima eficiencia). Elige el que mejor se adapte a tu teléfono:

| Archivo APK | Arquitectura | ¿Para qué sirve y qué teléfonos deben instalarlo? |
| :--- | :--- | :--- |
| 📱 **`AuraBeta-v0.1.0-beta.1a-Nebula-arm64-v8a.apk`** | **ARM 64-bits (`arm64-v8a`)** | **Recomendado para la mayoría de teléfonos modernos (2018 en adelante).** Incluye exclusivamente los motores nativos en C++20, FFmpeg y Python de 64 bits. Es más ligero en espacio de almacenamiento y ofrece el máximo rendimiento acústico y energético. |
| 📱 **`AuraBeta-v0.1.0-beta.1a-Nebula-armeabi-v7a.apk`** | **ARM 32-bits (`armeabi-v7a`)** | **Diseñado para teléfonos antiguos, básicos o con sistemas Android de 32 bits.** Contiene exclusivamente los binarios nativos optimizados con instrucciones ARMv7 NEON de 32 bits sin cargar librerías de 64 bits que tu procesador no utilice. |
| 🌐 **`AuraBeta-v0.1.0-beta.1a-Nebula-universal.apk`** | **Universal Móvil (`arm64-v8a` + `armeabi-v7a`)** | **Funciona en absolutamente cualquier teléfono Android (32 y 64 bits).** Es el APK todo-terreno ideal si no sabes qué procesador tiene tu celular, para compartir el archivo con amigos por mensajería o para subir directamente a tiendas como **Uptodown**. |
