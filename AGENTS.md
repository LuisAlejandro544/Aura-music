# AGENTS.md - Manual de Operaciones para Agentes de Desarrollo 🛠️

Este documento define el protocolo de trabajo para los agentes de software y desarrolladores que operan sobre **Aura Music**, alineado con las 7 fases del ciclo real de ingeniería de software.

---

## 🎭 Roles y Fases de Desarrollo

### 1. El Arquitecto (Planificación y Diseño)
* **Objetivo**: Antes de escribir una sola línea de código, definir la estructura, entidades y flujo técnico.
* **Protocolo en Aura Music**:
  - Verificar que las nuevas funciones encajen en el flujo MVVM (Model - Room Database - Repository - Storage - ViewModel - Compose UI).
  - Diseñar pantallas separadas y modulares en `ui/screens/` en lugar de saturar una única pantalla.
  - El almacenamiento de datos de usuario se organiza exclusivamente en `Android/data/com.aistudio.musicplayer.aurasound/files/` en subcarpetas (`images/`, `songs/`, `lyrics/`, `metadata/`).
  - Asegurar compatibilidad arquitectónica con arquitecturas de 64 bits (`arm64-v8a`, `x86_64`) y 32 bits (`armeabi-v7a`, `x86`).

### 2. El Constructor (Generación de Código)
* **Objetivo**: Escribir código limpio, tipado, modular y con manejo exhaustivo de excepciones.
* **Protocolo en Aura Music**:
  - Utilizar Kotlin con Jetpack Compose y C++20 para código nativo DSP de 10 bandas.
  - El ecualizador paramétrico debe ser accesible desde el Mini Reproductor y Now Playing, sin ocupar espacio innecesario en la barra inferior.
  - La compresión de carátulas a WebP debe realizarse en un hilo secundario sin pérdida de calidad.
  - Toda canción sin carátula debe generarse proceduralmente mediante Canvas/matemáticas, evitando imágenes fijas genéricas.
  - Comentar cada archivo explicando la lógica que contiene y su rol arquitectónico.
  - Mantener los tamaños de archivo por debajo de 500 líneas cuando sea posible.

### 3. El Detective (Debugging)
* **Objetivo**: Diagnosticar y resolver errores de forma metódica con razonamiento paso a paso (*Chain of Thought*).
* **Protocolo en Aura Music**:
  - Formular 3 hipótesis antes de tocar código.
  - Evitar fondos translúcidos que provoquen filtraciones visuales o sangrado de elementos de fondo en los reproductores.
  - Revisar registros de Gradle y trazas de excepciones de Media3, Room o JNI.
  - Comprobar que no haya llamadas a `@Composable` dentro de bloques `try-catch` o dentro de bloques directos de `LazyListScope`.

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
  - Cubrir casos límite: archivos de audio corruptos, URIs no disponibles, listas vacías y edición de metadatos.

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
- [x] ¿El estándar de C++ está fijado en C++20 con soporte multi-arquitectura?
- [x] ¿El ecualizador de 10 bandas funciona y es accesible desde el mini reproductor?
- [x] ¿El almacenamiento estructurado (`images/`, `songs/`, `lyrics/`, `metadata/`) está activo?
- [x] ¿Las carátulas se procesan como WebP sin pérdida en segundo plano y las faltantes se generan proceduralmente?
- [x] ¿Se pueden modificar los metadatos de las canciones (título, artista, álbum)?
- [x] ¿Se eliminó el sangrado visual detrás del mini reproductor y de Now Playing?
- [x] ¿Los 5 archivos Markdown (`README.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md`, `AGENTS.md`) están actualizados y en español?
