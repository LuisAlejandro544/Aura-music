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

## ⚡ Fase 2: Motor DSP Nativo en C++20 y Almacenamiento Organizado (Completada ✅)

- [x] **Ecualizador Paramétrico de 10 Bandas en C++20**:
  - Implementación de filtros IIR bi-cuadráticos (*Peaking Biquads*) de coma flotante de 64 bits (`double`).
  - Frecuencias ISO estándar: `31Hz, 62Hz, 125Hz, 250Hz, 500Hz, 1kHz, 2kHz, 4kHz, 8kHz, 16kHz`.
  - Ganancia de -15 dB a +15 dB.
- [x] **Limitador / Saturador Suave en C++**:
  - Algoritmo anti-clipping para evitar distorsión armónica indeseada.
- [x] **Procesador de Audio Media3 (`NativeAudioProcessor`)**:
  - Canalización de buffers PCM hacia el motor DSP.
- [x] **Almacenamiento Estructurado en `Android/data/com.nuestraapp/files/`**:
  - `images/`: Carátulas en formato WebP con compresión sin pérdida (Lossless).
  - `songs/`: Archivos de audio locales.
  - `lyrics/`: Letras de canciones sincronizadas (.lrc).
  - `metadata/`: Información de canciones en formato JSON (autor, artista, álbum, etc.).
- [x] **Generador de Carátulas Procedurales**:
  - Creación matemática de obras geométricas y gradientes vectoriales para canciones sin portada.
- [x] **Edición de Metadatos**:
  - Modificación de título, artista y álbum con persistencia en Room y almacenamiento JSON.
- [x] **Acceso Directo al Ecualizador**:
  - Integrado de forma lógica y directa en el Mini Reproductor y Now Playing, liberando la barra de navegación principal.
- [x] **Eliminación del Sangrado Visual**:
  - Fondos 100% opacos OLED en el reproductor completo y mini-reproductor.

---

## 🌈 Fase 3: Visualizador FFT en Tiempo Real y Letras Sincronizadas (En Progreso 🔄)

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
