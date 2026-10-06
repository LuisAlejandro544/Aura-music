# Aura Music — Migración 100% Pura de Motores Nativos a Gradle y CMake (Cero Wrappers y Eliminación Total de `.sh`)

Esta actualización elimina de forma definitiva todos los archivos `.sh` del proyecto y traslada el 100% de la lógica de aprovisionamiento, verificación criptográfica y compilación nativa directamente dentro de `build.gradle.kts` y `CMakeLists.txt`, utilizando **exclusivamente motores nativos 100% puros sin ningún wrapper** (CPython puro, FFmpeg CLI/nativo puro, QuickJS C99 original puro y `yt-dlp` oficial puro) para las 4 arquitecturas (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`).

## User Review & Critical Decisions

> [!IMPORTANT]
> Se han aplicado estrictamente tus directrices: **cero wrappers**, **100% motores nativos puros** y **eliminación total e inmediata de todos los archivos `.sh`**.

- **Decisión Confirmada 1 — Cero Wrappers (100% Puro)**: No se utilizará ningún wrapper de terceros ni capas intermedias. Se integran los binarios y fuentes originales puros: **CPython nativo puro** (`libpython.so` + `libpython3.x.so` + `libpython.zip.so` stdlib completa), **FFmpeg CLI/nativo puro** (`libffmpeg.so` + `libavcodec`/`libavfilter`/`libavformat`/`libswscale` en `libffmpeg.zip.so`), **QuickJS C99 puro** compilado desde las fuentes originales de Fabrice Bellard con NDK/CMake (`libqjs.so`) y **`yt-dlp` oficial puro** verificado con SHA-256.
- **Decisión Confirmada 2 — Eliminación Total de todos los archivos `.sh`**: Se borran por completo `scripts/compile_native_deps.sh`, `scripts/generate_keystore_and_build.sh` y todos los scripts de `scripts/native/` (`provision_ytdlp.sh`, `provision_quickjs.sh`, `provision_python.sh`, `provision_ffmpeg.sh`). Toda su lógica pasa a vivir de forma nativa dentro de `app/build.gradle.kts` y `app/src/main/cpp/CMakeLists.txt`.
- **Decisión Confirmada 3 — Erradicación del Error `sh: python3: inaccessible or not found` y de Binarios de 500 KB**: Se elimina cualquier llamada a `/system/bin/sh` (`system("python3")`) y se exige validación estricta del tamaño real en megabytes de los motores puros durante el build de Gradle.

---

## 1. Overview & Core Concept

- **Qué Hace**: Traslada toda la descarga, extracción, compilación NDK/Clang y empaquetado de los 4 motores puros (**CPython**, **FFmpeg**, **QuickJS** y **yt-dlp**) directamente al bloque de construcción de Gradle (`build.gradle.kts`) y CMake (`CMakeLists.txt`), eliminando para siempre los scripts `.sh`.
- **Audiencia Objetivo / Contexto**: Dispositivos Android reales de 32 y 64 bits (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) donde Aura Music ejecuta extracción local con `yt-dlp` sobre CPython puro, descifrado de firmas `n-sig` con QuickJS C99 puro y procesamiento de Video Canvas / Audio con FFmpeg puro.
- **Valor Principal**: Garantiza que los motores nativos empaquetados en el APK tengan su peso y funcionalidad 100% real (decenas de megabytes de código nativo puro por arquitectura, nunca stubs de 500 KB) y que jamás fallen buscando comandos en el shell de Android.

---

## 2. User Experience & Visual Design

- **Flujos de Usuario Clave**:
  1. **Extracción Directa con `yt-dlp` + CPython Puro + QuickJS Puro**: Al pegar un enlace de YouTube, TikTok o web, `YtDlpNativeEngine` invoca directamente el binario PIE `libpython.so` enlazado a `libpython3.so` y su `stdlib` completa junto con `libqjs.so` (QuickJS puro), resolviendo el JSON y las firmas `n-sig` sin tocar `/system/bin/sh`.
  2. **Procesamiento de Video Canvas y Audio con FFmpeg Puro**: `FFmpegNativeEngine` ejecuta directamente el binario puro `libffmpeg.so` con sus librerías nativas (`libavcodec`, `libavfilter`, `libavformat`, `libswscale`) para generar el lienzo vertical 9:16 con fondo difuminado, bucles `xfade` Seamless Loop, efecto `Boomerang` (`reverse` + `concat`), GOP corto a 30fps y eliminación de pista de audio (`-an`).
  3. **Transparencia en Aura Monitor**: Desaparece por completo el warning `sh: python3: inaccessible or not found` en dispositivos reales como el TECNO KL5.
- **Identidad Visual y Tema (Sin Cambios de Diseño)**:
  - Se respeta al 100% el diseño actual **Dark Luxury Neo-Glass OLED**, iconos, colores y pantallas de la aplicación sin modificar ningún elemento visual.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisión 1: Toda la Lógica de Aprovisionamiento y Compilación Nativa Dentro de `build.gradle.kts` y `CMakeLists.txt`**
  - *Enfoque Elegido*: Implementar tareas nativas en Kotlin DSL dentro de `app/build.gradle.kts` (junto con configuraciones Maven de paquetes nativos puros precompilados multi-ABI y compilación C/C++20 en CMake/NDK) que descargan, verifican por SHA-256 y tamaño mínimo en MB, extraen las librerías `.so` puras para las 4 ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`), compilan QuickJS C99 puro y el lanzador PIE de CPython/FFmpeg con el NDK Toolchain, y aplican `llvm-strip`.
  - *Por Qué*: Elimina al 100% la fragilidad de los scripts `.sh`, unifica el ciclo de vida en `preBuild` de Gradle y asegura que tanto localmente como en GitHub Actions se construya exactamente igual.
- **Decisión 2: Motores 100% Puros sin Wrappers ni Llamadas a `system("python3")` / `system("ffmpeg")`**
  - *Enfoque Elegido*: Enlazar directamente contra las librerías nativas reales (`libpython3.x.so` / `Py_BytesMain` y las librerías nativas de FFmpeg / ejecutable ELF estático/PIE puro) en `nativeLibraryDir` y `filesDir/env/`, prohibiendo cualquier fallback que llame a `sh` o genere binarios falsos de 500 KB.
  - *Por Qué*: Cumple tu regla estricta de usar motores 100% puros sin wrappers y resuelve de raíz el fallo en Android 14.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│               APP/BUILD.GRADLE.KTS + CMAKELISTS.TXT (CERO .SH)             │
├──────────────────────────────────────┬─────────────────────────────────────┤
│  Tareas Kotlin DSL en build.gradle   │  Compilación NDK / CMake (4 ABIs)   │
│  • Aprovisionamiento yt-dlp SHA-256  │  • Motor DSP Audio C++20 (10 Bandas)│
│  • Extracción CPython Puro Multi-ABI │  • QuickJS C99 Puro (libqjs.so PIE) │
│  • Extracción FFmpeg Puro Multi-ABI  │  • Lanzadores PIE Puros CPython/FF  │
│  • Validación Estricta de Tamaño MB  │  • Optimización automática llvm-strip│
└──────────────────┬───────────────────┴──────────────────┬──────────────────┘
                   │                                      │
                   ▼                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│        APK FINAL MULTI-ARQUITECTURA (arm64-v8a, armeabi-v7a, x86_64, x86)  │
├──────────────────────────┬──────────────────────────┬──────────────────────┤
│  libpython.so +          │  libffmpeg.so +          │  libqjs.so +         │
│  libpython.zip.so (Puro) │  libffmpeg.zip.so (Puro) │  assets/bin/yt-dlp   │
│  • Intérprete CPython    │  • Binario FFmpeg Puro   │  • Intérprete C99    │
│  • Stdlib + OpenSSL/TLS  │  • Filtros xfade/reverse │  • Descifrado n-sig  │
└──────────────────────────┴─────────────┬────────────┴───────────┬──────────┘
                                         │                        │
                                         ▼                        ▼
┌────────────────────────────────────────────────────────────────────────────┐
│              EJECUCIÓN EN EL TELÉFONO (YtDlpNativeEngine / FFmpeg)         │
│  • Ejecución nativa directa desde nativeLibraryDir (Cero llamadas a sh)    │
│  • Extracción YouTube/TikTok -> Audio .m4a + Carátula WebP + Canvas 480p   │
└────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State
- **Orquestación en `build.gradle.kts`**:
  - Tarea `provisionNativeDeps` escrita íntegramente en Kotlin dentro de `app/build.gradle.kts` que gestiona:
    1. **Motor `yt-dlp` Puro**: Descarga verificada con `SHA2-256SUMS` en `src/main/assets/bin/yt-dlp`.
    2. **Motor QuickJS C99 Puro**: Descarga de las fuentes C99 oficiales de QuickJS y compilación con el NDK Clang Toolchain a `libqjs.so` (PIE) para `arm64-v8a`, `armeabi-v7a`, `x86_64` y `x86`.
    3. **Motor CPython Puro Multi-ABI**: Obtención y empaquetado de las librerías compartidas reales de CPython (`libpython3.so` / `libpython3.11.so`, `libcrypto.so`, `libssl.so`) y su librería estándar completa (`libpython.zip.so`) junto con su ejecutable PIE nativo `libpython.so` enlazado por `dlopen`/`Py_BytesMain` sin invocar jamás `system("python3")`.
    4. **Motor FFmpeg Puro Multi-ABI**: Obtención y empaquetado del binario ejecutable nativo `libffmpeg.so` y sus bibliotecas compartidas (`libffmpeg.zip.so`) para las 4 arquitecturas sin wrappers.
    5. **Generación de Firma `debug.keystore`**: Tarea integrada en Gradle para restaurar desde `debug.keystore.base64` o generar la firma con `keytool` automáticamente sin necesitar `generate_keystore_and_build.sh`.

### Interactive Component & State Mapping
- **Eliminación de Archivos `.sh` y Limpieza del Repositorio**:
  - Se eliminan todos los archivos `.sh` y el directorio `scripts/`.
  - Se actualiza `.github/workflows/build-debug-apk.yml` para que invoque directamente las tareas de Gradle (`./gradlew :app:assembleDebug`) y cachee los binarios en función de `app/build.gradle.kts` y `app/src/main/cpp/CMakeLists.txt`.
  - Se actualizan los 5 documentos técnicos (`README.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md`, `AGENTS.md`) reflejando la eliminación de los `.sh` y la nueva arquitectura 100% pura en Gradle y CMake.
