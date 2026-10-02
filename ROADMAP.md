# Aura Music - Roadmap de Desarrollo 🚀

Este documento traza las fases de evolución técnica y funcional para convertir a **Aura Music** en el reproductor de audio local más avanzado, visualmente atractivo y de mayor fidelidad en Android.

---

## 📌 Fase 1: Arquitectura Base y Motor Media3 (Completada ✅)

- [x] Interfaz de usuario completa con Jetpack Compose y Material Design 3.
- [x] Diseño estilo Spotify modernizado con 4 paletas de colores vibrantes y acentos de neón.
- [x] Motor de reproducción con **Jetpack Media3 (ExoPlayer)**.
- [x] Persistencia local reactiva con **Room Database**.
- [x] Importación selectiva basada en Storage Access Framework (SAF) respetando la privacidad.
- [x] Generador de pistas sintetizadas WAV para pruebas automáticas y de primer uso.
- [x] Eliminación de dependencias de `.env` para compilación limpia y autónoma.
- [x] Elevación de requisitos mínimos a **Android 8.0 (API 26)**.

---

## ⚡ Fase 2: Motor DSP Nativo en C++20, Carátulas y Playlists (Completada ✅)

- [x] **Integración de C++20 con CMake en el APK Final**:
  - Enlace con `externalNativeBuild` y filtros ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`).
  - Filtros IIR Bi-cuadráticos (*Peaking Biquads*) en C++20 con `std::span` y limitador anti-clipping.
  - Sincronización completa de llamadas JNI con `NativeAudioEngine`.
- [x] **Carátulas Personalizadas desde Galería**:
  - Integración del Android Photo Picker (`ActivityResultContracts.PickVisualMedia`).
  - Conversión a WebP sin pérdida en segundo plano (`Dispatchers.IO`).
  - Eliminación física de la carátula anterior para evitar basura en almacenamiento.
  - Opción de restaurar a arte procedural en cualquier momento.
- [x] **Sistema Completo de Playlists en la Biblioteca**:
  - Pestaña de Playlists completamente funcional.
  - Tarjeta especial sincronizada "Tus Me Gusta" / Favoritos.
  - Creación, nombrado y renombrado libre de playlists.
  - Diálogo modal para añadir canciones rápidamente desde la biblioteca.
- [x] **Pulido de Animaciones del Sistema**:
  - Transiciones de pantalla fluidas con `AnimatedContent` (desvanecimiento y deslizamiento).
  - Expansión y repliegue elástico de la pantalla completa Now Playing.
  - Micro-interacciones y feedback táctil enriquecido.
- [x] **Pipeline de CI/CD GitHub Actions y Script Shell**:
  - Workflow manual (`workflow_dispatch`) con generación forzada de firma `debug.keystore` RSA 2048-bit.
  - Script autónomo `scripts/generate_keystore_and_build.sh`.

---

## 🌈 Fase 3: Visualizador FFT en Tiempo Real y Letras Sincronizadas (Siguiente Paso 🔄)

- [ ] **Transformada Rápida de Fourier (FFT)**:
  - Cálculo espectral de 512 / 1024 puntos en C++ a 60/120 FPS sin consumo de Garbage Collector en la JVM.
  - Visualizadores de barras, medidores VU analógicos y espectrograma circular.
- [ ] **Visor de Letras Sincronizadas (.LRC)**:
  - Desplazamiento automático interactivo estilo karaoke leyendo desde `lyrics/`.
- [ ] **Efecto Crossfeed (Bauer / Chu Moy)**:
  - Reducción de fatiga auditiva con auriculares emulando la escucha en monitores de campo cercano.

---

## 🔊 Fase 4: Modo Bit-Perfect y Salida de Ultra-Baja Latencia

- [ ] **Integración con Google Oboe / AAudio**:
  - Modo exclusivo para saltarse el mezclador del sistema Android (*AudioFlinger*).
  - Reproducción directa hacia DACs USB externos en 24-bit/32-bit a 96 kHz o 192 kHz.
- [ ] **Decodificación Nativa de Formatos Especiales**:
  - Soporte de archivos DSD (.dsf / .dff), Monkey's Audio (.ape) y módulos chiptune (.mod, .xm).

---

## 📑 Fase 5: Experiencia de Usuario Avanzada

- [ ] **Temporizador de Apagado (*Sleep Timer*)**:
  - Atenuación progresiva del volumen al cumplirse el tiempo seleccionado.
- [ ] **Widgets de Pantalla de Inicio**:
  - Widgets interactivos con Material You y controles de reproducción directa.
