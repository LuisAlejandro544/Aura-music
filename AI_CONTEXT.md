# AI_CONTEXT.md - Contexto de Inteligencia Artificial 🤖

Este archivo define el contexto de diseño, directrices técnicas y restricciones inviolables para cualquier modelo de lenguaje o agente de IA que interactúe, modifique o extienda la base de código de **Aura Music**.

---

## 🎯 Propósito del Proyecto
**Aura Music** es un reproductor de audio local fuera de línea para Android con una experiencia estética inspirada en Spotify, pero con diseño moderno, colores vibrantes de neón, fondos 100% opacos, ecualización paramétrica de **10 bandas impulsada por C++20**, compresión WebP sin pérdida, almacenamiento de datos organizado y una suite de diagnóstico propia (**Aura Monitor**).

---

## 🔒 Reglas Técnicas Inviolables

1. **Requisitos de Sistema**:
   - `minSdk = 26` (Android 8.0 Oreo). No bajar esta versión bajo ninguna circunstancia.
   - `targetSdk = 36` y `compileSdk = 36`.

2. **Sin Dependencia de Archivos `.env`**:
   - Este proyecto **NO DEBE requerir `.env`** ni el plugin de secretos para compilar. Debe mantenerse 100% autónomo y reproducible en cualquier entorno.

3. **Privacidad y Modelo de Acceso a Archivos**:
   - **PROHIBIDO** el escaneo masivo automático del almacenamiento del teléfono (`READ_EXTERNAL_STORAGE` o rastreo ciego del disco).
   - Todo archivo debe ser importado **explícitamente por el usuario** mediante el *Storage Access Framework* (SAF) con `OpenMultipleDocuments` u `OpenDocumentTree`, persistiendo permisos de lectura con `takePersistableUriPermission`.

4. **Almacenamiento Estructurado**:
   - El almacenamiento de datos de la app se organiza en `Android/data/com.aistudio.musicplayer.aurasound/files/`:
     - `images/`: Carátulas comprimidas en WebP sin pérdida de calidad.
     - `songs/`: Canciones locales y demos.
     - `lyrics/`: Letras de canciones sincronizadas (.lrc).
     - `metadata/`: Archivos JSON con información técnica y descriptiva de las pistas.
   - Los registros de diagnóstico del monitor se guardan de forma atómica en `filesDir/aura_debug_logs.json`.

5. **Carátulas Procedurales & WebP**:
   - Las canciones sin carátula deben usar el generador procedural vectorial, no imágenes genéricas estáticas o de IA fijas.
   - Toda compresión de imágenes debe realizarse en hilos de fondo (`Dispatchers.IO`) en formato WebP con calidad máxima.

6. **Motor de Audio y C++20**:
   - Se debe utilizar **Jetpack Media3 (ExoPlayer)** (`androidx.media3:media3-*`).
   - El ecualizador paramétrico cuenta con **10 bandas ISO** y limitador anti-clipping en **ISO C++20** con filtros biquad de doble precisión.
   - El motor de **Audio 8D Espacial** está integrado en **ISO C++20** mediante paneo orbital continuo de potencia constante, filtro de sombra de cabeza y reverberación binaural para auriculares.
   - El ecualizador **NO DEBE TENER UN APARTADO APARTE DE PANTALLA COMPLETA**. Se integra y despliega como una hoja modal unificada (`AudioEffectsBottomSheet`) accesible desde el Mini Reproductor y Now Playing con un solo toque.
   - Los cambios de velocidad y tono (*playback parameters*) deben contar con amortiguación (*throttling* con corrutinas) para no saturar ExoPlayer ni pausar la canción accidentalmente.

7. **Diseño de Interfaz (Jetpack Compose & M3)**:
   - Fondos 100% opacos OLED: evitar transparencias que permitan que las listas o cabeceras se filtren por detrás del reproductor o mini reproductor.
   - La animación de salida de Now Playing hacia abajo debe realizarse sin superposiciones de capas negras residuales (`slideOutVertically` limpio sobre fondo nativo).
   - Todos los elementos interactivos deben cumplir con un tamaño mínimo de toque de **48.dp**.
   - Modularidad en pantallas: `Home`, `Library`, `Import`, `NowPlaying`, `Settings`, `PlaylistDetail`.

8. **Suite de Diagnóstico Propia & LeakCanary**:
   - La actividad `DebugMonitorActivity` ("Aura Monitor") debe mantenerse accesible desde el cajón de aplicaciones con su propio icono independiente.
   - Debe interceptar excepciones no controladas a nivel de proceso (`Thread.setDefaultUncaughtExceptionHandler`) y registrar datos del teléfono (modelo, CPU ABI, RAM, almacenamiento, versión de Android) junto con el stacktrace en crudo.
   - LeakCanary debe estar configurado en `debugImplementation` para auditar fugas de memoria en la JVM.

9. **Idioma de Comunicación**:
   - La documentación, comentarios en código, cadenas de usuario (`strings.xml`) y mensajes de commit deben redactarse en **español**.
