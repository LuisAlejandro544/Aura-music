<div align="center">

# 🌌 Aura Music (`Aura Beta` • Codename: *Nebula*)

### **Tu estudio audiófilo, centro de descargas y experiencia Video Canvas en tu bolsillo.**
**100% libre de anuncios y sin suscripciones.**

[![Versión Beta](https://img.shields.io/badge/Versi%C3%B3n-v0.1.0--beta.1a%20(Nebula)-8B5CF6?style=for-the-badge&logo=android&logoColor=white)](../../releases)
[![Lenguaje Principal](https://img.shields.io/badge/Lenguaje-Kotlin%20%2B%20Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](./READMEAI.md)
[![Motor de Audio](https://img.shields.io/badge/Audio%20DSP-ISO%20C%2B%2B20%20(64--bit)%20%2B%20AAudio-00D2FF?style=for-the-badge&logo=cplusplus&logoColor=white)](./READMEAI.md)
[![Android API](https://img.shields.io/badge/Android-8.0%2B%20(API%2026--36)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](../../releases)
[![Arquitecturas](https://img.shields.io/badge/ABI-arm64--v8a%20%7C%20armeabi--v7a-F59E0B?style=for-the-badge)](../../releases)
[![Privacidad](https://img.shields.io/badge/Anuncios%20y%20Rastreo-0%25%20(100%25%20Offline--First)-10B981?style=for-the-badge)](./LICENSE)

<br/>

[![Descargar Último APK](https://img.shields.io/badge/⬇️%20DESCARGAR%20APK%20OFICIAL%20(RELEASES)-111827?style=for-the-badge&logo=github&logoColor=00D2FF)](../../releases)
[![Documentación Técnica](https://img.shields.io/badge/📖%20ARQUITECTURA%20E%20INGENIERÍA%20(READMEAI)-1F2937?style=for-the-badge&logo=readthedocs&logoColor=A78BFA)](./READMEAI.md)
[![Changelog Beta](https://img.shields.io/badge/🚀%20NOVEDADES%20DE%20LA%20VERSIÓN%20(CHANGELOG)-1F2937?style=for-the-badge&logo=sparkles&logoColor=F472B6)](./chanelog-beta.md)

</div>

---

## 🌟 ¿Por qué elegir Aura Music?

**Aura Music** es un reproductor musical de alta fidelidad para Android diseñado con estética **Dark Luxury Neo-Glass OLED** (100% opaco, sin transparencias borrosas), creado para quienes buscan calidad de sonido de estudio, fondos de video inmersivos y herramientas completas para armar su biblioteca directamente desde el celular:

- 🚫 **Cero Anuncios y Privacidad Estricta**: Sin publicidad, sin telemetría invasiva y sin escaneos ocultos de tus carpetas. Tú eliges exactamente qué música o videos importar.
- 🎛️ **Motor Acústico Nativo en C++20 y Bit-Perfect**: Procesamiento en coma flotante de 64 bits con **Ecualizador de 10 bandas**, **Audio 8D / 16D Multi-Órbita**, **Clarificador de Voces HD**, **Reverb sin bloqueos**, **Normalizador EBU R128** y **Modo Bit-Perfect con Google AAudio**.
- 🎬 **Video Canvas & Modo Cinemático**: Reproduce videos sincronizados o bucles continuos de fondo (con colores reactivos en tiempo real y 0ms de retraso al adelantar la canción).
- 📥 **Descargas y Conversión Todo en Uno**: Descarga canciones y videos de fondo directamente desde enlaces web (**YouTube, TikTok y más**) a máxima velocidad, convierte cualquier video de tu galería en música en 1 segundo y une canciones con el **Creador de Mixtapes**.
- 🔄 **Siempre al Día (Actualizaciones OTA Integradas)**: Incluye actualizador automático tanto para nuevas versiones del APK como para el motor interno de extracción (`yt-dlp`), todo verificado con seguridad criptográfica `SHA-256`.

---

## 🎧 Especificaciones Audiófilas y Formatos Soportados

Aura Music combina la versatilidad de **Jetpack Media3 (ExoPlayer)** con un puente directo a **Google AAudio (`libaaudio.so`)**, **FFmpeg nativo** y un procesador **DSP en ISO C++20**:

| Categoría Técnica | Especificación en Aura Music |
| :--- | :--- |
| **Precisión de Procesamiento DSP** | Coma flotante de doble precisión (**64-bit Float**) en filtros Biquad IIR con limitador suave anti-clipping (`std::tanh`) y *Buffer Flushing* atómico (cero chasquidos al saltar o pausar). |
| **Salida de Audio & Latencia** | **Google AAudio (`AAUDIO_PERFORMANCE_MODE_LOW_LATENCY`)** con soporte para **Modo Bit-Perfect 1:1** (Bypass directo sin alteración de fase/ganancia) y modo exclusivo para DACs USB / Audífonos Hi-Res. |
| **Resoluciones de Salida** | *Nativo 1:1 (Sin Resampling)* • *Hi-Res 96 kHz / 24-bit* • *Ultra Hi-Res 192 kHz / 32-bit Float*. |
| **Formatos Estándar y Sin Pérdida** | `FLAC`, `WAV` (PCM 16/24/32-bit), `ALAC` (`.alac`), `AIFF` (`.aiff`), `M4A` (AAC), `MP3`, `OGG` (Vorbis), `OPUS`. |
| **Formatos Audiófilos & Especiales** | **DSD** (`.dsf`, `.dff` — DSD64/128), **Monkey's Audio** (`.ape`), **WavPack** (`.wv`), **Musepack** (`.mpc`). |
| **Formatos Retro / Chiptune** | Decodificación nativa de módulos Tracker: `.mod`, `.xm`, `.it`, `.s3m`. |
| **Procesamiento de Portadas** | Compresión automática en segundo plano a **WebP Lossless** (sin pérdida de calidad) + **Arte Procedural Vectorial** para pistas sin portada. |

---

## 🚀 Funciones Principales en Detalle

Haz clic en cada categoría para desplegar todas sus herramientas y características técnicas:

<details open>
<summary><b>🎛️ 1. Estudio Acústico DSP (10 Bandas, Audio 8D/16D, Voces HD, Reverb y DJ Automix)</b></summary>
<br/>

Todo el panel de efectos se despliega al instante en una hoja modal desde el **Mini Reproductor** o desde **Now Playing**, sin interrumpir tu navegación:

- **Ecualizador Paramétrico de 10 Bandas ISO + Bass Boost (60 Hz)**:
  - Bandas identificadas con nombres claros: `31 Hz (Subgraves)`, `62 Hz (Bajos)`, `125 Hz (Graves)`, `250 Hz (Cuerpo)`, `500 Hz (Medios Bajos)`, `1 kHz (Voces)`, `2 kHz (Claridad)`, `4 kHz (Presencia)`, `8 kHz (Brillo)` y `16 kHz (Aire / Agudos)`.
  - Rango de `-15 dB` a `+15 dB`, perfiles predefinidos (*Rock, Pop, Electrónica, Jazz, Acústico, Bass Boost, Plano*) y selector de alcance: aplícalo **🌐 Para todas las canciones** o **🎵 Solo para la canción actual**.
- **Clarificador de Voces HD (Procesamiento Mid-Side)**:
  - Separa el canal vocal central (*Mid*) del acompañamiento lateral (*Side*), atenúa la turbidez en `180 Hz` y realza la presencia y dicción de la voz en `2.8 kHz` y `5.5 kHz`.
- **Audio Espacial 8D y 16D Multi-Órbita Binaural**:
  - **Modo 8D Clásico**: Rotación de 360° alrededor de tu cabeza con filtro de sombra acústica craneal (*Head Shadow*).
  - **Modo 16D Multi-Órbita**: Divide la canción en dos capas contra-rotatorias independientes (los bajos se mantienen estables en una órbita interna mientras las voces y agudos trazan una figura-8 con micro-retardo Haas).
- **Suite Reverb 100% C++20 (Cero Silencios)**:
  - Simulación acústica de espacios reales (*Estudio, Sala Mediana, Club En Vivo, Gran Hall, Catedral, Eco Espacial*) con control libre de tamaño de sala, decaimiento y mezcla.
- **Velocidad y Tono Independientes (`0.50x` a `2.00x`) + Repetidor A-B**:
  - Modula la velocidad de la música y el tono de la voz por separado (ideal para *Slowed + Reverb*, *Nightcore* o practicar instrumentos) con botones rápidos (`0.8x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`) y barra compacta de **Bucle A-B** con ajuste fino de `±1s`.
- **Normalizador de Volumen Inteligente (EBU R128 / Nivel Spotify)**:
  - Iguala automáticamente el volumen entre canciones suaves y fuertes con 3 niveles: *Sutil (-18 LUFS)*, *Estándar (-14 LUFS)* y *Alto (-11 LUFS)*.
- **Automix Inteligente DJ con Curva en X**:
  - Detecta el final real de la canción para saltar silencios muertos y atenúa progresivamente los subgraves (`<120 Hz`) de la pista saliente para evitar choques de bajos en el *crossfade*.
- **Temporizador de Apagado con Fade-Out**:
  - Apaga la música automáticamente tras el tiempo elegido disminuyendo suavemente el volumen durante los últimos 10 segundos.

</details>

<details>
<summary><b>🎧 2. Zona Exclusiva para Auriculares (Crossfeed, Balance L/R, Seguridad y Gestos)</b></summary>
<br/>

- **Filtro Crossfeed Acústico (Algoritmo Bauer / Chu Moy)**:
  - Reduce la fatiga auditiva en sesiones largas mezclando sutilmente ambos canales con retardo interaural, logrando que los audífonos suenen naturales como monitores de estudio frente a ti. **Se activa automáticamente solo cuando conectas auriculares** (cable 3.5mm, USB-C o Bluetooth) y se apaga al usar el altavoz del móvil.
- **Balance Estéreo Fino (Izquierda / Derecha L/R)**:
  - Ajusta el volumen de cada oído por separado en tiempo real desde *Now Playing* o la hoja de efectos, con botón rápido para centrar a `0%`.
- **Protección contra Desconexiones (*Becoming Noisy Guard*) y Fade-In**:
  - Pausa la reproducción en el acto si se desconecta el cable o se apagan tus audífonos Bluetooth para que jamás suene por accidente en público, y reanuda el sonido con una subida progresiva de 1 segundo para cuidar tus oídos.
- **Memoria de Volumen Independiente y Mapeo de Botones**:
  - Guarda un nivel de volumen distinto para tus audífonos y otro para el altavoz, y personaliza qué acción ejecutan 1, 2, 3 pulsaciones o mantener presionado el botón de tus auriculares.

</details>

<details>
<summary><b>🎬 3. Video Canvas Dinámico, Modo Cinemático y Armonización Cromática</b></summary>
<br/>

- **2 Diseños de Reproductor + Modo Automático**:
  - **Modo Clásico**: Carátula cuadrada 1:1 en alta nitidez, visualizador espectral de 28 bandas y controles tradicionales.
  - **Modo Cinemático Canvas (Estilo Spotify)**: Despeja más del 70% de la pantalla para el video de fondo, mostrando una viñeta luminosa adaptada en tiempo real a los colores del video, minicarátula de 54dp, frase de Karaoke flotante y controles inferiores con selector circular clásico ("bolita").
  - **Modo Automático Inteligente**: Cambia solo al diseño Cinemático cuando la canción tiene video y vuelve al Clásico cuando solo tiene portada.
- **4 Modos de Encuadre para Videos Horizontales (16:9) y Verticales (9:16)**:
  - Elige en cualquier momento entre *Fondo Completo (Rellenar / Recortar)*, *Fondo Completo (Adaptado Horizontal sin recortar rostros)*, *Lienzo en Carátula (1:1)* o *Solo Carátula*.
- **Ingeniería FFmpeg en tus Videos de Fondo**:
  - **Bucles Infinitos sin Cortes**: En videos cortos (≤20s), elige entre fundido continuo (*Seamless Crossfade*) o **Efecto Boomerang / Ping-Pong** (ida y vuelta sin saltos).
  - **Saltos Instantáneos (0ms) y Ahorro de Espacio (hasta -85%)**: En videos largos sincronizados (>20s), reestructura los fotogramas clave a 1 segundo (GOP corto a 30fps) y elimina la pista de audio redundante (`-an`) del archivo de video para que adelantar o cambiar de canción sea instantáneo y sin pantallas negras.

</details>

<details>
<summary><b>📥 4. Descargas Web (YouTube/TikTok), Video a Música, Mixtapes y "Compartir con..."</b></summary>
<br/>

- **Descargador Integrado de Enlaces Web en Segundo Plano (`AuraDownloadService`)**:
  - Pega enlaces de **YouTube, TikTok o la web** para descargar la canción con su portada oficial en WebP y su **Video Canvas en 480p** vinculado automáticamente.
  - **Arquitectura Resiliente Multi-Motor**: Combina el motor oficial **`yt-dlp + FFmpeg`** (con actualizador OTA en caliente) + **`InnerTube Nativo`** (resolución en `<300ms`) + **`Bypass Invidious`** + **`WebView Móvil`** con cascada garantizada de portadas.
  - **Acelerador HTTP Range Multi-Bloque**: Rompe el límite de velocidad de red descargando entre **10 MB/s y 40 MB/s**, continuando en segundo plano con notificación en vivo aunque salgas de la aplicación.
- **Conversor "Video a Música" 3 en 1 (En 1 Segundo)**:
  - Selecciona cualquier video de tu galería y extrae el audio directo a `.m4a` sin perder calidad (*Direct Stream Copy*), generando su portada WebP y vinculando el video de fondo al instante.
- **Puente Inteligente "Abrir con..." y "Compartir con..."**:
  - Comparte archivos de audio, videos o enlaces directamente desde **SnapTube, navegadores, Telegram, WhatsApp o gestores de archivos** hacia Aura Music: la app detecta qué es, lo guarda en tu biblioteca permanente y lo reproduce de inmediato.
- **Creador de Mixtapes (Fusión Continua con Capítulos Reactivos)**:
  - Une varias canciones de una Playlist en un solo archivo continuo `.m4a` con transiciones suaves de 3s, 5s, 8s o 10s. Mientras suena el Mixtape, Aura Music cambia automáticamente el título, la portada, el Video Canvas y la letra sincronizada en cada capítulo.
- **Recorte Automático de Silencios (`AudioSilenceTrimmer`)**:
  - Interruptor disponible antes de cada importación o descarga para detectar y recortar los silencios al inicio y al final de la pista (`-42 dB` RMS).

</details>

<details>
<summary><b>🎤 5. Karaoke Sincronizado (.LRC / .TXT), Personalización OLED y Control de Almacenamiento</b></summary>
<br/>

- **Letras Estilo Karaoke con Descarga Automática (`LRCLIB`)**:
  - Descarga letras sincronizadas automáticamente sin registro, o usa el **Buscador Interactivo** que destaca la versión `⭐ OFICIAL / RECOMENDADA` en primer lugar y te permite comparar variantes comunitarias o importar tus propios archivos `.lrc` y `.txt`.
  - **Modo Karaoke a Pantalla Completa**: Vista inmersiva estilo *Apple Music Sing / Spotify* con letra grande iluminada en neón, auto-scroll y salto de tiempo al tocar cualquier verso.
- **Temas OLED, Material You, Collages, Widgets y Android Auto**:
  - 5 paletas de acento neón (*Material You dinámico, Nebula Violet, Cyber Mint, Sunset Ember, Ocean Abyss*), Playlists con portada personalizada o collage automático de 1 a 4 fotos, Widget redimensionable para la pantalla de inicio con insignia Bit-Perfect y soporte para **Android Auto**.
- **Transparencia Total de Almacenamiento**:
  - En **Ajustes > Medios & Almacenamiento** puedes inspeccionar cada portada WebP y cada Video Canvas MP4 guardado, ver cuántos KB o MB ocupa y borrar individualmente los que no necesites para liberar espacio al instante.

</details>

---

## 📲 Descarga e Instalación (Guía de APKs)

En la sección de [**Releases Oficiales**](../../releases) encontrarás **3 variantes del instalador APK** optimizadas exclusivamente para dispositivos móviles. Elige la indicada para tu teléfono:

| Archivo APK | Arquitectura | ¿Para qué teléfonos sirve? | Recomendación |
| :--- | :---: | :--- | :--- |
| **`arm64-v8a`** | **ARM 64-bits** | Casi todos los teléfonos Android modernos (fabricados desde 2018 hasta hoy). | ⭐ **Recomendado** (Más ligero, rápido y eficiente en batería) |
| **`armeabi-v7a`** | **ARM 32-bits** | Teléfonos Android antiguos, básicos o con sistemas de 32 bits. | Úsalo solo si el `arm64-v8a` no se instala en tu celular |
| **`universal`** | **32 + 64 bits** | Compatible con **absolutamente cualquier teléfono Android**. | Ideal si no sabes cuál elegir, para compartir con amigos o tiendas como **Uptodown** |

### Pasos rápidos para instalar y actualizar:
1. Ve a [**Releases**](../../releases) y descarga el archivo `.apk` (preferiblemente **`arm64-v8a`**).
2. Ábrelo desde las descargas de tu navegador o gestor de archivos y autoriza la instalación si Android te lo solicita.
3. **Actualizaciones Automáticas sin perder tu música**: Para futuras versiones, puedes buscar, descargar y verificar actualizaciones directamente desde dentro de la app entrando a **Ajustes > Motores Nativos & Diagnóstico**. Todas tus canciones, portadas, videos y playlists guardadas en `Android/data/com.auramusic.beta/files/` se conservarán intactas.

---

## 🛠️ Stack Tecnológico y Documentación de Ingeniería

Aura Music está construida siguiendo principios de **Clean Architecture + MVVM** con desarrollo modular estricto:

- **Interfaz y Arquitectura**: `Kotlin`, `Jetpack Compose (Material 3)`, `Coroutines & StateFlow`, `Room Database`, `Storage Access Framework (SAF)`.
- **Audio y Multimedia**: `Jetpack Media3 (ExoPlayer & MediaSessionService)`, `Google AAudio (NDK)`, `ISO C++20 DSP (CMake)`, `FFmpeg Nativo`, `CPython 3.11 + QuickJS + yt-dlp OTA`.

> 📄 **¿Quieres revisar la arquitectura interna, el árbol de módulos o los detalles de compilación?**  
> Consulta los documentos técnicos del repositorio:
> - [**`READMEAI.md`**](./READMEAI.md) — Documentación técnica exhaustiva de extremo a extremo.
> - [**`STRUCTURE.md`**](./STRUCTURE.md) — Árbol de directorios, coordinadores MVVM y blindaje de seguridad.
> - [**`ROADMAP.md`**](./ROADMAP.md) — Historial de fases de ingeniería completadas.
> - [**`chanelog-beta.md`**](./chanelog-beta.md) — Notas de lanzamiento detalladas de `Aura Beta (Nebula)`.

## 👨‍💻 Detrás del Proyecto (Creador & Contribuidores)

- 🌟 **Creador, Fundador & Propietario del Proyecto**: [**Luis Alejandro Sosa Camacho (`@LuisAlejandro544`)**](https://github.com/LuisAlejandro544)
- 🤝 **Contribuidor Oficial**: [**`@thelandy03-boop`**](https://github.com/thelandy03-boop)

*(Puedes consultar sus perfiles directamente dentro de la app entrando a **Ajustes > Detrás del Proyecto**; las fotos de perfil de GitHub se descargan una única vez y se guardan permanentemente en formato WebP sin pérdida para funcionar incluso sin internet).*

---

## ⚖️ Licencia y Derechos de Autor

**Copyright © 2026 Luis Alejandro Sosa Camacho. Todos los derechos reservados.**

Este proyecto se publica bajo una **Licencia Propietaria de Código Visible (*Source-Available — Viewing Only*)**.  
**Luis Alejandro Sosa Camacho** es el único propietario y titular exclusivo de los derechos del proyecto, otorgando reconocimiento y crédito oficial a los colaboradores autorizados. El código fuente se encuentra disponible en este repositorio **únicamente para su visualización, lectura y consulta**. **Queda estrictamente prohibida** la copia, modificación por terceros no autorizados, compilación externa, creación de obras derivadas, redistribución o uso comercial del código fuente o de sus binarios sin la autorización previa y por escrito del autor.

Consulta el archivo [**`LICENSE`**](./LICENSE) para leer los términos legales completos.
