# Restauración de Motores Nativos Puros (FFmpeg C99/C11 & CPython) y Desbloqueo Resiliente de Videos Oficiales VEVO

Este plan define la arquitectura técnica para eliminar la dependencia del wrapper `libffmpegkit.so` compilando un motor CLI de **FFmpeg 100% puro en C** enlazado directamente contra las librerías nativas (`libavformat`, `libavcodec`, `libavfilter`, `libswscale`, `libswresample`, `libavutil`, `libavdevice`), restaurar todos los binarios y módulos nativos necesarios (`_sqlite3`, `libsqlite3`, `libavdevice`) sin podas agresivas que rompan dependencias ELF o `yt-dlp`, y resolver definitivamente el bloqueo `PLAYABILITY_ERROR` / `HTTP 403 Forbidden` en videos musicales oficiales VEVO (incluyendo la verificación en vivo con `https://youtu.be/1_zgKRBrT0Y?si=ZgEkSvcggTh-JMmk`).

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Decisiones confirmadas por el usuario en la Fase 1:**
> - **Motor FFmpeg Puro sin Wrapper (`libffmpegkit.so` eliminado)**: Se compilará el ejecutable PIE nativo (`libffmpeg.so`) en C directamente contra las APIs públicas de las librerías base de FFmpeg (`avformat_*`, `avcodec_*`, `avfilter_*`, `swresample_*`, `swscale_*`, `avdevice_*`) y se conservará `libavdevice.so` en todas las arquitecturas (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`), eliminando por completo `libffmpegkit.so` y `libffmpegkit_abidetect.so`.
> - **Poda Quirúrgica Inteligente (Restauración de Binarios Necesarios)**: Se restauran todos los binarios y módulos que `yt-dlp`, `CPython 3.11` y `FFmpeg` requieren para operar al 100% (`_sqlite3.so`, `libsqlite3_chaquopy.so`, `libavdevice.so`, `sqlite3/*`, `xml/*`, `http/*`, `urllib/*`, `email/*`, `ctypes/*`, `mmap`, `termios`, `_zoneinfo`), podando **únicamente** suites de test (`test/`, `unittest/`), interfaces gráficas de escritorio (`tkinter/`, `idlelib/`, `turtle`) y el wrapper obsoleto `libffmpegkit.so`.
> - **Estrategia Multi-Capa con Fallbacks para Videos Oficiales VEVO**: Se implementará una liga combinada en cascada:
>   1. **Clientes sin PoToken ni bloqueo VEVO (`TVHTML5` / `tv`, `WEB_EMBEDDED_PLAYER` / `web_embedded`, `MWEB`, `ANDROID_VR`, `IOS`)** tanto en `yt-dlp` (con `QuickJS C99` nativo descifrando `signatureCipher` y `n-sig`) como en `InnerTubeClient`.
>   2. **Invidious con Proxy de Flujo (`local=true`)**: Evita el bloqueo `HTTP 403 Forbidden` por IP-binding de `googlevideo.com` enrutando la descarga a través del propio nodo que firmó el stream, además de probar múltiples instancias actualizadas y APIs Piped de respaldo.
>   3. **Verificación Empírica en Vivo**: Antes de finalizar la entrega, se ejecutará la cadena completa sobre `https://youtu.be/1_zgKRBrT0Y?si=ZgEkSvcggTh-JMmk` (*Pedro Capó, Farruko - Calma (Remix - Official Video)*) verificando resolución, descarga real de bytes (>0 KB) y procesamiento FFmpeg.

---

## 1. Overview & Core Concept

- **Qué soluciona**:
  1. **Ruptura de FFmpeg por `libffmpegkit.so` y `libavdevice.so`**: Anteriormente, `libffmpeg.so` intentaba hacer `dlopen("libffmpegkit.so")`, el cual exigía `libavdevice.so` en su cabecera ELF (`NEEDED`), provocando un fallo inmediato al haber sido podado. Además, el repositorio original de `ffmpeg-kit` fue archivado. Ahora `libffmpeg.so` será un motor CLI en C puro que invoca directamente las funciones nativas de `libavformat.so`, `libavcodec.so`, `libavfilter.so`, `libswresample.so`, `libswscale.so`, `libavutil.so` y `libavdevice.so` sin pasar por `libffmpegkit.so`.
  2. **Ruptura de `yt-dlp` en Videos VEVO y Poda de `_sqlite3`**: Los videos oficiales VEVO (como *Calma Remix*) bloquean los clientes `android_vr` e `ios` sin `PoToken`, y `yt-dlp` requiere `_sqlite3` (para cookies/caché) además de los clientes `tv`, `web_embedded` y `mweb` junto con el motor `QuickJS C99` para descifrar las firmas `s` y `n` de `base.js`.
  3. **Bloqueo HTTP 403 en Invidious**: Las URLs crudas de `googlevideo.com` devueltas por Invidious llevan embebida la IP del servidor de Invidious (`ip=...`). Al descargarlas desde la IP del teléfono o contenedor, Google responde `HTTP 403 Forbidden`. Activando `local=true` (o reescribiendo al endpoint `/latest_version?id=...&itag=...&local=true` cuando la URL tiene restricción de IP), el flujo se descarga a máxima velocidad sin error 403.

---

## 2. User Experience & Visual Design

- **Estabilidad Transparente**: El diseño visual, iconografía y disposición de pantallas de **Aura Music** se mantienen intactos conforme a las reglas del proyecto.
- **Experiencia en el Modal de Descarga (`DownloadFromLinkDialog`)**:
  - Al pegar `https://youtu.be/1_zgKRBrT0Y?si=ZgEkSvcggTh-JMmk`, el selector de motores resuelve los metadatos, miniatura oficial en alta resolución y flujos de audio/video en pocos segundos sin caer en errores `HTTP 403` ni bucles de espera.
  - La extracción de audio (`.m4a`), recorte de silencios, generación de Video Canvas (480p H.264 sin audio `-an`) y Mixtape Crossfade se ejecutan directamente sobre el nuevo motor **FFmpeg C Puro**.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisión 1: Compilación de CLI FFmpeg Puro en C (`native_ffmpeg_launcher.c`) sin `libffmpegkit.so`**
  - *Enfoque elegido*: Implementar el transcodificador/muxer/filtro CLI completo en C puro dentro de `native_ffmpeg_launcher.c` utilizando la API pública de `libavformat`, `libavcodec`, `libavfilter` (`abuffer`, `abuffersink`, `buffer`, `buffersink`, `avfilter_graph_parse_ptr` para soportar `-af`, `-vf` y `-filter_complex` como `xfade`, `acrossfade`, `scale`, `crop`, `pad`, `reverse`, `concat`), `libswresample` y `libswscale`, cargando directamente `libavutil.so`, `libswresample.so`, `libswscale.so`, `libavcodec.so`, `libavformat.so`, `libavfilter.so` y `libavdevice.so`.
  - *Por qué*: Elimina para siempre la dependencia del wrapper `libffmpegkit.so`, garantiza compatibilidad en las 4 arquitecturas (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) y soporta todos los comandos que ejecuta `FFmpegNativeEngine` (extracción `-vn -c:a copy` o recodificación AAC, recorte `-ss`/`-to`, Video Canvas `-an -c:v libx264`/`mpeg4`, Boomerang y Mixtape `acrossfade`).

- **Decisión 2: Restauración de `_sqlite3`, `libsqlite3_chaquopy.so` y `libavdevice.so`**
  - *Enfoque elegido*: Conservar en `build.gradle.kts` (`ProvisionNativeDepsTask`) las librerías `libsqlite3_chaquopy.so`, `libpymod__sqlite3.so`, `libavdevice.so`, y todos los módulos de runtime necesarios para `yt-dlp`, podando exclusivamente `libffmpegkit.so`, `libffmpegkit_abidetect.so` y paquetes de prueba/GUI (`test/`, `unittest/`, `tkinter/`, `idlelib/`, `turtledemo/`).
  - *Por qué*: Evita errores de `ImportError: No module named '_sqlite3'` en `yt-dlp` y errores de tabla `NEEDED` en el enlazador dinámico de Android (`bionic`).

- **Decisión 3: Liga de Extracción Multi-Nivel para Videos VEVO (`tv`, `web_embedded`, `mweb` + QuickJS + Invidious `local=true` + Piped)**
  - *Enfoque elegido*:
    1. En `YtDlpNativeEngine`: Configurar `--extractor-args youtube:player_client=tv,web_embedded,mweb,android_vr,ios` junto con `--js-runtimes quickjs:<ruta_libqjs.so>` y asegurar que el symlink `qjs` / `quickjs` esté expuesto en `PATH` y sea invocable por `yt-dlp_ejs`.
    2. En `InnerTubeClient`: Añadir soporte para clientes `TVHTML5_SIMPLY_EMBEDDED_PLAYER` / `WEB_EMBEDDED_PLAYER` con `signatureCipher` resuelto mediante `QuickJS` nativo cuando el video está restringido en clientes sin firma.
    3. En `InvidiousStreamResolver`: Añadir soporte de proxy de flujo (`local=true`) para que las URLs firmadas por el servidor de Invidious no devuelvan `HTTP 403 Forbidden` al descargarse desde la IP del usuario, y ampliar la lista de nodos activos.

---

## 4. Technical Architecture & Data Strategy

### Arquitectura del Subsistema Nativo y Extracción Resiliente

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   AURA MUSIC - FLUJO DE DESCARGA Y FFMPEG PURO              │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
┌───────────────────┐     ┌───────────────────────┐   ┌───────────────────────┐
│   Nivel 1:        │     │   Nivel 2:            │   │   Nivel 3:            │
│ YtDlpNativeEngine │     │ InnerTubeClient + QJS │   │ Invidious / Piped +   │
│ (CPython 3.11 +   │     │ (ANDROID_VR, IOS,     │   │ HeadlessWebView       │
│  _sqlite3 + QJS)  │     │  TV_EMBEDDED + Cipher │   │ (Proxy local=true     │
│ Clients: tv,      │     │  Decryption via       │   │  anti-403 IP-binding) │
│ web_embedded,mweb │     │  libqjs.so)           │   │                       │
└─────────┬─────────┘     └───────────┬───────────┘   └───────────┬───────────┘
          │                           │                           │
          └───────────────────────────┼───────────────────────────┘
                                      ▼
                    ┌───────────────────────────────────┐
                    │     ChunkedStreamDownloader       │
                    │ (Descarga HTTP Range / Fallback   │
                    │  Secuencial Anti-403 con Headers) │
                    └─────────────────┬─────────────────┘
                                      ▼
                    ┌───────────────────────────────────┐
                    │  FFmpegNativeEngine (libffmpeg.so)│
                    │  Motor C Puro (Cero libffmpegkit) │
                    │  ┌─────────────────────────────┐  │
                    │  │ libavformat  │ libavcodec   │  │
                    │  │ libavfilter  │ libswscale   │  │
                    │  │ libswresample│ libavdevice  │  │
                    │  │ libavutil                   │  │
                    │  └─────────────────────────────┘  │
                    └───────────────────────────────────┘
```

### Mapeo de Componentes y Flujo de Datos

1. **Aprovisionamiento Gradle (`ProvisionNativeDepsTask` en `app/build.gradle.kts`)**:
   - Restaura `libsqlite3_chaquopy.so`, `_sqlite3.so` (`libpymod__sqlite3.so`), `mmap.so`, `_zoneinfo.so`, `termios.so` y `libavdevice.so`.
   - Excluye `libffmpegkit.so` y `libffmpegkit_abidetect.so` para garantizar un entorno 100% libre del wrapper.
   - Compila `native_ffmpeg_launcher.c` como `libffmpeg.so` enlazado con `-ldl -lm -pthread` para las 4 ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`).
2. **Motor CLI FFmpeg Puro en C (`native_ffmpeg_launcher.c`)**:
   - Carga dinámicamente `libavutil.so`, `libswresample.so`, `libswscale.so`, `libavcodec.so`, `libavformat.so`, `libavfilter.so` y `libavdevice.so` desde `nativeLibraryDir`.
   - Implementa el parser de argumentos CLI de FFmpeg (`-i`, `-ss`, `-to`, `-t`, `-vn`, `-an`, `-c:v`, `-c:a`, `-b:a`, `-b:v`, `-vf`, `-af`, `-filter_complex`, `-map`, `-movflags`, `-f`, `-y`) operando directamente sobre `AVFormatContext`, `AVCodecContext`, `AVFilterGraph`, `SwrContext` y `SwsContext`.
3. **Desbloqueo VEVO en `YtDlpNativeEngine`, `InnerTubeClient`, `InvidiousStreamResolver` y `ChunkedStreamDownloader`**:
   - En `YtDlpNativeEngine`: Habilita los clientes `tv,web_embedded,mweb,android_vr,ios`, expone `libqjs.so` con nombre y versión estándar para `yt-dlp-ejs`, y asegura `_sqlite3.so` en el directorio de módulos.
   - En `InnerTubeClient`: Añade resolución con cliente `TVHTML5_SIMPLY_EMBEDDED_PLAYER` (`embedUrl` / `thirdParty`) y descifrado de `signatureCipher` + parámetro `n` ejecutando `libqjs.so` sobre el reproductor JS de YouTube cuando el video es VEVO.
   - En `InvidiousStreamResolver`: Reescribe las URLs de stream con `local=true` a través de la instancia que emitió el formato o selecciona flujos compatibles cuando la URL directa de `googlevideo.com` está atada a la IP del nodo Invidious.
   - En `ChunkedStreamDownloader`: Si un bloque Range recibe `HTTP 403` en un stream con proxy o restricción de rango, conmuta automáticamente a descarga secuencial con las cabeceras de origen o solicita refresco al siguiente motor de la cascada.
