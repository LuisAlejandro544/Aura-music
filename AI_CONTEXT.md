# AI_CONTEXT.md - Contexto de Inteligencia Artificial 🤖

Este archivo define el contexto de diseño, directrices técnicas y restricciones inviolables para cualquier modelo de lenguaje o agente de IA que interactúe, modifique o extienda la base de código de **Aura Music**.

---

## 🎯 Propósito del Proyecto
**Aura Music** es un reproductor de audio local fuera de línea para Android con una experiencia estética inspirada en Spotify, pero con diseño moderno, colores vibrantes de neón, fondos 100% opacos, ecualización paramétrica de **10 bandas impulsada por C++20**, compresión WebP sin pérdida y almacenamiento de datos organizado.

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

5. **Carátulas Procedurales & WebP**:
   - Las canciones sin carátula deben usar el generador procedural vectorial, no imágenes genéricas estáticas o de IA fijas.
   - Toda compresión de imágenes debe realizarse en hilos de fondo (`Dispatchers.IO`) en formato WebP con calidad máxima.

6. **Motor de Audio y C++20**:
   - Se debe utilizar **Jetpack Media3 (ExoPlayer)** (`androidx.media3:media3-*`).
   - El ecualizador paramétrico cuenta con **10 bandas ISO** y limitador anti-clipping en **ISO C++20** con filtros biquad de doble precisión.
   - La entrada al ecualizador se realiza desde el **Mini Reproductor** y la pantalla **Now Playing**, manteniendo despejada la barra inferior de navegación.

7. **Diseño de Interfaz (Jetpack Compose & M3)**:
   - Fondos 100% opacos OLED: evitar transparencias que permitan que las listas o cabeceras se filtren por detrás del reproductor o mini reproductor.
   - Todos los elementos interactivos deben cumplir con un tamaño mínimo de toque de **48.dp**.
   - Modularidad en pantallas: `Home`, `Library`, `Import`, `Equalizer`, `NowPlaying`, `Settings`, `PlaylistDetail`.

8. **Idioma de Comunicación**:
   - La documentación, comentarios en código, cadenas de usuario (`strings.xml`) y mensajes de commit deben redactarse en **español**.
