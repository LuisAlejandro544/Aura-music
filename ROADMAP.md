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

## 🎧 Fase 3: Audio 8D Espacial en C++20, Temporizador y Control de Reproducción (Completada ✅)

- [x] **Motor Nativo de Audio 8D Binaural para Auriculares (ISO C++20)**:
  - Algoritmo de rotación orbital continua de potencia constante con control de velocidad (4s a 30s por vuelta).
  - Simulación de sombra acústica de la cabeza (*Head Shadow Effect*) al transitar el sonido detrás del oyente.
  - Reverberación acústica binaural y ajuste de profundidad de sala sin modelos 3D invasivos.
- [x] **Temporizador de Apagado (Sleep Timer) con Fade-Out Suave**:
  - Configuración libre de minutos personalizados y chips rápidos (15m, 30m, 45m, 60m).
  - Contador regresivo en tiempo real (`mm:ss`) con opción de añadir +5 minutos.
  - Atenuación progresiva lineal del volumen en los últimos 10 segundos antes de pausar la reproducción.
- [x] **Control de Velocidad y Tono (Playback Parameters)**:
  - Modulación en tiempo real de velocidad (0.50x a 2.00x) y tono musical (Pitch Shift).
  - Botón de restablecimiento rápido a 1.0x.
- [x] **Transiciones Suaves (Crossfade) y Reproducción Gapless**:
  - Fundido cruzado ajustable de 0 a 12 segundos con desvanecimiento de entrada y salida entre pistas.
  - Conmutador de reproducción Gapless continua sin silencios intermedios.
- [x] **Hoja Modal de Efectos en Now Playing (`AudioEffectsBottomSheet`)**:
  - Componente accesible desde la barra superior de Now Playing con indicador de insignia activa.

---

## 🛠️ Fase 4: Estabilidad Crítica, Diagnóstico Autónomo y UX Fluida (Completada ✅)

- [x] **Suite de Diagnóstico en Caliente "Aura Monitor"**:
  - Aplicación de depuración conectada pero con icono propio en el cajón de apps del teléfono móvil.
  - Captura y persistencia de excepciones fatales no controladas (`UncaughtExceptionHandler` -> CRASH).
  - Registro de incidentes clasificados por severidad (`CRASH`, `CRÍTICO`, `ERROR`, `WARNING`, `INFO`).
  - Telemetría técnica del teléfono: Fabricante, Modelo, Versión de Android / SDK API, CPU ABI, RAM y almacenamiento libre.
  - Visualizador de Stack Trace en crudo y exportación de informe completo al portapapeles.
- [x] **Integración de LeakCanary**:
  - Detección automática en tiempo real de fugas de memoria en builds de depuración.
- [x] **Ecualizador C++20 Integrado en Modal (Cero Apartados Aislados)**:
  - Eliminación de la pantalla completa separada para el ecualizador; integración nativa en pestaña modal accesible instantáneamente desde el Mini Reproductor y Now Playing.
- [x] **Estabilidad de Velocidad y Tono (Anti-Pausa Accidental)**:
  - Implementación de *throttling* y *debouncing* en `AuraAudioPlayer` para evitar saturación de `SonicAudioProcessor` en ExoPlayer.
  - Mecanismo de recuperación automática que reanuda la reproducción si el procesador de audio sufre una perturbación transitoria.
- [x] **Transición Fluida de Salida en Now Playing**:
  - Eliminación de capas negras residuales y parpadeos al cerrar o deslizar hacia abajo el reproductor mediante deslizamiento suave instantáneo.
- [x] **Video Canvas y Fondo Completo (3 Modos Seleccionables)**:
  - Soporte para asociar videos desde la galería a canciones individuales con Photo/Media Picker.
  - **3 Modos de Visualización**: Fondo Completo (video detrás de toda la pantalla con velo oscuro y carátula flotando al frente), Lienzo en Carátula (recuadro central 1:1) y Solo Carátula.
  - Selector modal interactivo accesible desde la barra superior y badge dinámico en la carátula.
  - Loops automáticos de Canvas (≤ 10s - 20s) en bucle continuo y videos sincronizados (> 20s) con selector de modo en edición.
  - **Armonización Cromática Reactiva**: Extracción dinámica de color desde fotogramas clave de video en tiempo real, evitando que el color de la carátula estática interfiera con el video.
  - Sincronización temporal reactiva con la música (`currentPositionMs` y `seekTo`) para videos largos.
  - Almacenamiento organizado en subcarpeta `videos/` con eliminación de archivos obsoletos.
- [x] **Sincronización Reactiva de Favoritos y Estabilidad en Inicio**:
  - Reflejo instantáneo del estado de favorito en el reproductor (corazón lleno en rojo `Color(0xFFEF4444)` al activar).
  - Estabilidad permanente en la sección "Populares en tu biblioteca", evitando que al pulsar el corazón se cambie la lista o se oculten otras canciones.
- [x] **Conversor Nativo "Video a Música" (Extracción 3 en 1)**:
  - Extracción directa de audio (`.m4a`) desde videos de la galería mediante demuxing sin recodificación en Android.
  - Captura y compresión automática de fotograma clave en WebP sin pérdida como carátula oficial.
  - Vinculación automática opcional como Video Canvas sincronizado o loop con inicio inmediato de reproducción.

---

## 🎧 Fase 5: Suite Acústica de Auriculares, Crossfeed C++20 y Ergonomía (Completada ✅)

- [x] **Filtro Crossfeed Acústico Nativo (ISO C++20)**:
  - Algoritmo acústico Bauer / Chu Moy para auriculares con filtro paso-bajos (~700 Hz) y retardo interaural (ITD).
  - Activación exclusiva cuando se detecta un auricular conectado (`isHeadphoneConnected`), en reposo para altavoces.
  - 3 niveles acústicos: Sutil (Bauer 4.5 dB), Moderado (Chu Moy Classic) e Intenso (Monitores de Estudio).
- [x] **Balance Estéreo Fino (Control Izquierda / Derecha L/R en Tiempo Real)**:
  - Compensación milimétrica de balance L/R con paneo suave y limitador suave anti-clipping en C++20.
  - Barra de ajuste en tiempo real en la pantalla completa Now Playing y en la hoja modal de efectos con botón de centrado.
- [x] **Protección contra Desconexiones ("Becoming Noisy" Guard)**:
  - Intercepción inmediata de `ACTION_AUDIO_BECOMING_NOISY` para pausar la música al desenchufar audífonos.
- [x] **Reanudación con Fade-In Suave (Volumen Progresivo)**:
  - Rampa suave de volumen de ~1 segundo al reanudar la reproducción con audífonos puestos para proteger los oídos.
- [x] **Memoria de Volumen Dedicada para Audífonos**:
  - Almacenamiento independiente de nivel de volumen para auriculares vs altavoz del dispositivo.
- [x] **Control Avanzado de Botones y Gestos de Auriculares (Headset Controls)**:
  - Detección de 1 pulsación, 2 pulsaciones, 3 pulsaciones y pulsación prolongada (hold) con mapeo libre personalizable.
- [x] **Pestaña Dedicada de "Auriculares" en Configuración**:
  - Navegación segmentada en 3 apartados: 1. Acústica & DSP, 2. Seguridad & Conexión, 3. Botones y Gestos.

---

## 🌈 Fase 6: Visualizador FFT en Tiempo Real y Letras Sincronizadas (Siguiente Paso 🔄)

- [ ] **Transformada Rápida de Fourier (FFT)**:
  - Cálculo espectral de 512 / 1024 puntos en C++ a 60/120 FPS sin consumo de Garbage Collector en la JVM.
  - Visualizadores de barras, medidores VU analógicos y espectrograma circular.
- [ ] **Visor de Letras Sincronizadas (.LRC)**:
  - Desplazamiento automático interactivo estilo karaoke leyendo desde `lyrics/`.

---

## 🔊 Fase 7: Modo Bit-Perfect y Salida de Ultra-Baja Latencia

- [ ] **Integración con Google Oboe / AAudio**:
  - Modo exclusivo para saltarse el mezclador del sistema Android (*AudioFlinger*).
  - Reproducción directa hacia DACs USB externos en 24-bit/32-bit a 96 kHz o 192 kHz.
- [ ] **Decodificación Nativa de Formatos Especiales**:
  - Soporte de archivos DSD (.dsf / .dff), Monkey's Audio (.ape) y módulos chiptune (.mod, .xm).
- [ ] **Widgets de Pantalla de Inicio**:
  - Widgets interactivos con Material You y controles de reproducción directa.
