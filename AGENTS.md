# AGENTS.md - Manual de Operaciones para Agentes de Desarrollo 🛠️

Este documento define el protocolo de trabajo para los agentes de software y desarrolladores que operan sobre **Aura Music**, alineado con las 7 fases del ciclo real de ingeniería de software.

---

## 🎭 Roles y Fases de Desarrollo

### 1. El Arquitecto (Planificación y Diseño)
* **Objetivo**: Antes de escribir una sola línea de código, definir la estructura, entidades y flujo técnico.
* **Protocolo en Aura Music**:
  - Verificar que las nuevas funciones encajen en el flujo MVVM (Model - Room Database - Repository - Storage - ViewModel - Compose UI).
  - Diseñar pantallas modulares en `ui/screens/` evitando saturar interfaces.
  - El almacenamiento de datos de usuario se organiza exclusivamente en `Android/data/com.aistudio.musicplayer.aurasound/files/` en subcarpetas (`images/`, `songs/`, `lyrics/`, `metadata/`, `videos/`).
  - Asegurar compatibilidad arquitectónica con arquitecturas de 64 bits (`arm64-v8a`, `x86_64`) y 32 bits (`armeabi-v7a`, `x86`).

### 2. El Constructor (Generación de Código)
* **Objetivo**: Escribir código limpio, tipado, modular y con manejo exhaustivo de excepciones.
* **Protocolo en Aura Music**:
  - Utilizar Kotlin con Jetpack Compose y C++20 para código nativo DSP de 10 bandas.
  - El ecualizador paramétrico debe integrarse en la hoja modal unificada de efectos de audio accesible tanto desde el Mini Reproductor como desde Now Playing, **sin ocupar un apartado de pantalla completa aparte** que interrumpa la navegación del usuario.
  - La compresión de carátulas a WebP debe realizarse en un hilo secundario sin pérdida de calidad.
  - Toda canción sin carátula debe generarse proceduralmente mediante Canvas/matemáticas, evitando imágenes fijas genéricas.
  - Comentar cada archivo explicando la lógica que contiene y su rol arquitectónico.
  - Mantener los tamaños de archivo por debajo de 500 líneas cuando sea posible.

### 3. El Detective (Debugging)
* **Objetivo**: Diagnosticar y resolver errores de forma metódica con razonamiento paso a paso (*Chain of Thought*).
* **Protocolo en Aura Music**:
  - Formular 3 hipótesis antes de tocar código.
  - Evitar fondos translúcidos o contenedores de `AnimatedVisibility` con fondos negros residuales que provoquen parpadeos o capas negras al cerrar el reproductor.
  - En la modulación rápida de parámetros de reproducción (velocidad y tono/voz), aplicar *throttling* con corrutinas y mecanismos de recuperación en `onPlayerError` para que ExoPlayer nunca detenga la reproducción de forma imprevista.
  - Apoyarse en la suite **Aura Monitor** (`DebugMonitorActivity`) y **LeakCanary** para rastrear en caliente excepciones no controladas, warnings de memoria y fugas en la JVM.

### 4. El Crítico (Code Review)
* **Objetivo**: Inspeccionar seguridad, rendimiento y buenas prácticas como si fuera un Pull Request profesional.
* **Protocolo en Aura Music**:
  - Verificar que no se bloquee el hilo principal (`Dispatchers.Main`) y que la compresión WebP y E/S de archivos ocurra en `Dispatchers.IO`.
  - Auditar que no existan accesos globales al disco que violen la privacidad del usuario.
  - Confirmar que ningún componente interactivo tenga un área táctil menor a 48.dp.
  - Garantizar que la app compile de manera autónoma sin requerir archivos `.env`.

### 5. El Optimizador (Refactoring y Rendimiento)
* **Objetivo**: Mejorar velocidad, legibilidad y consumo de batería sin alterar el comportamiento observable.
* **Protocolo en Aura Music**:
  - Usar `remember` y `derivedStateOf` para evitar recomposiciones innecesarias en Compose.
  - En C++20, utilizar `std::span` para pasar buffers PCM de audio por referencia sin asignaciones dinámicas de memoria.
  - Usar compresión WebP Lossless para optimizar drásticamente el espacio de almacenamiento y tiempos de renderizado con Coil.

### 6. El Escudo (Testing y Calidad)
* **Objetivo**: Garantizar estabilidad mediante pruebas unitarias y de integración.
* **Protocolo en Aura Music**:
  - Probar flujos locales con Robolectric (`gradle :app:testDebugUnitTest`).
  - Cubrir casos límite: archivos de audio corruptos, URIs no disponibles, listas vacías y manipulación rápida de parámetros de audio.

### 7. El Narrador (Documentación)
* **Objetivo**: Documentar claramente cada cambio y módulo técnico.
* **Protocolo en Aura Music**:
  - Mantener sincronizados `README.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md` y `AGENTS.md`.
  - Asegurar que la información técnica y documentación esté siempre redactada en **español**.

---

## 📋 Lista de Verificación Pre-Entrega (Checklist Obligatorio)

- [x] ¿El proyecto compila sin errores (`compile_applet`)?
- [x] ¿`minSdk` se mantiene en 26 (Android 8.0)?
- [x] ¿Se eliminó completamente la necesidad de archivos `.env`?
- [x] ¿El estándar de C++ está fijado en C++20 con soporte multi-arquitectura y empaquetado del .so en el APK final?
- [x] ¿El ecualizador de 10 bandas funciona y está integrado en modal sin un apartado de pantalla completa separado?
- [x] ¿El motor de Audio 8D Espacial en C++20 está implementado y configurable desde Now Playing?
- [x] ¿El temporizador de apagado personalizable con atenuación de 10s (fade-out) funciona correctamente?
- [x] ¿La modulación de velocidad (0.5x-2.0x) y tono musical cuenta con protección contra pausas accidentales?
- [x] ¿Se eliminó el fondo negro residual y el parpadeo al cerrar o minimizar la pantalla de reproducción?
- [x] ¿Aura Monitor está integrado y accesible con su propio icono en el cajón de aplicaciones del teléfono?
- [x] ¿LeakCanary está añadido y funcional en dependencias de depuración?
- [x] ¿El almacenamiento estructurado (`images/`, `songs/`, `lyrics/`, `metadata/`) está activo?
- [x] ¿Las carátulas se procesan como WebP sin pérdida en segundo plano y las faltantes se generan proceduralmente?
- [x] ¿El Video Canvas de fondo soporta loops cortos (≤20s), videos largos sincronizados con el audio y armonización cromática sin interferencias de carátula?
- [x] ¿El video de fondo soporta los 3 modos (Fondo Completo con carátula al frente, Lienzo en carátula y Solo Carátula) seleccionables por el usuario mediante diálogo modal?
- [x] ¿El corazón en Now Playing se refleja inmediatamente en rojo y se sincroniza reactivamente con Room y ExoPlayer?
- [x] ¿La sección 'Populares en tu biblioteca' de la pantalla de inicio permanece fija sin renombrarse a 'Tus favoritos' ni ocultar canciones al marcar favoritos?
- [x] ¿El evento TRIM_MEMORY_UI_HIDDEN (nivel 20) está clasificado como información normal en lugar de advertencia para evitar falsas alarmas de memoria?
- [x] ¿La función 'Video a Música' extrae el audio a .m4a, genera carátula en WebP y vincula el Video Canvas automáticamente?
- [x] ¿Se pueden modificar los metadatos de las canciones (título, artista, álbum)?
- [x] ¿La pestaña 'Auriculares' está implementada con apartados de Acústica DSP, Seguridad y Gestos?
- [x] ¿El filtro Crossfeed (Bauer / Chu Moy en C++20) se activa exclusivamente al detectar auriculares conectados?
- [x] ¿El Balance Estéreo Fino (L/R) se puede ajustar en tiempo real en Now Playing y hoja modal?
- [x] ¿La protección contra desconexiones ("Becoming Noisy" Guard) y Fade-In suave están activos?
- [x] ¿La memoria de volumen dedicada y el control avanzado por botones de audífonos son configurables?
- [x] ¿La notificación nativa del reproductor de Android (System Media Controls con MediaSessionService, ForwardingPlayer completo con Anterior/Play/Siguiente y MediaStyle retrocompatible) está integrada y funcional?
- [x] ¿Las 10 bandas del ecualizador cuentan con etiquetas acústicas intuitivas (Subgraves, Bajos, Graves, Voces, Claridad, Brillo, Aire) junto a los Hz?
- [x] ¿El botón de modo de video en Now Playing tiene formato de cápsula flotante elevada visible y ergonómica de alto contraste (área táctil >= 48dp) al pie de la carátula sin elementos pequeños o apretados?
- [x] ¿La reacción dinámica por intensidad de la música está ligada directamente al motor en C++20 (cálculo RMS y envolvente espectral) haciendo respirar la carátula, halo ambiental y visualizador?
- [x] ¿Las letras sincronizadas (.LRC) estilo Karaoke con descarga automática libre desde LRCLIB, auto-scroll y salto táctil están integradas y persistidas en `lyrics/`?
- [x] ¿El temporizador de apagado ejecuta su atenuación suave y pausa en el hilo principal (Main Thread) previniendo excepciones de ExoPlayer?
- [x] ¿La función de descarga desde TikTok y enlaces web extrae la música de cualquier duración, genera carátula en WebP y vincula el Video Canvas de fondo automáticamente?
- [x] ¿La descarga desde YouTube / Video Web con arquitectura dual (Motor nativo InnerTube + Headless WebView reparado con bypass de gestos) y selector interactivo está integrada y operativa?
- [x] ¿La opción de paleta dinámica 'Material You' está disponible y operativa en ajustes para Android 12+?
- [x] ¿La escala tipográfica está fijada a un valor cómodo (fontScale = 1.0f) para evitar que los ajustes de texto del sistema desborden la interfaz?
- [x] ¿La reproducción continua automática al finalizar una pista pasa fluidamente a la siguiente canción de la biblioteca sin detenerse?
- [x] ¿El fundido de transición (fade-out) y entrada progresiva (fade-in) restauran suavemente el volumen original sin quedarse atrapados en volumen bajo?
- [x] ¿Los 5 archivos Markdown (`README.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md`, `AGENTS.md`) están actualizados y en español?
