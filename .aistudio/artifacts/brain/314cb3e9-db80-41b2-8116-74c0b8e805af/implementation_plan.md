# Plan de Restauración Integral de `yt-dlp` para Búsqueda y Descarga por URL en Aura Music 🛠️

Este plan define los cambios específicos y quirúrgicos para solucionar el problema reportado: **restaurar la capacidad de `yt-dlp` de encontrar e inspeccionar cualquier video de YouTube por URL**, eliminando los argumentos conflictivos introducidos recientemente y asegurando que las descargas inicien y avancen fluidamente sin quedarse atascadas en 0.0 MB.

---

## 📋 Diagnóstico de la Causa Raíz (¿Qué causó el fallo?)

1. **Parámetro `--format` restrictivo en `--dump-single-json`**:
   - Se añadió un selector de formato rígido (`bestvideo[height<=480][ext=mp4][vcodec^=avc1][protocol^=http]+bestaudio[ext=m4a]...`) directamente en la llamada de inspección. Si el video no tiene exactamente esa combinación disponible (o se sirve bajo protocolos https/dash), `yt-dlp` aborta inmediatamente con:  
     `ERROR: Requested format is not available`.
   - Al fallar el comando, Kotlin no recibe ningún JSON y la app no puede mostrar el título ni la carátula, quedando el botón trabado o fallando.
2. **Priorización de clientes bloqueados (`player_client=tv`)**:
   - Se forzó `--extractor-args youtube:player_client=tv,web_embedded,mweb,android_vr,ios,web`. Los servidores de YouTube actualmente rechazan o bloquean las peticiones sin PO Token del cliente `tv` y `web_embedded`, provocando que `yt-dlp` falle o demore demasiado recorriendo la lista de clientes.
3. **Ausencia de Timeout y Reintentos Lentos**:
   - `process.waitFor()` se ejecuta sin un límite de tiempo razonable. Si `yt-dlp` demora en la red, la interfaz permanece bloqueada indefinidamente en *"Inspeccionando enlace..."*.
4. **Fallo en el inicio de la descarga (Screenshot 1: Pedro Capó 0.0 MB)**:
   - Las URLs de streaming resueltas requieren que las cabeceras coincidan o que, ante cualquier bloqueo HTTP 403, se active de inmediato la descarga directa nativa con `YtDlpNativeEngine.downloadStreamDirectToFile`.

---

## 🎯 Decisiones Confirmadas por el Usuario

- **Prioridad Absoluta a `yt-dlp`**: Si el usuario selecciona el motor `yt-dlp`, la app debe utilizar `yt-dlp` de forma directa y fiel para inspeccionar y resolver el video por URL, tal como funcionaba antes del cambio.

---

## 🛠️ Plan de Cambios Paso a Paso

### 1. Restaurar Invocación Limpia de `yt-dlp` en `YtDlpNativeEngine.kt`
- **Eliminar el filtro restrictivo `--format` en `resolveStream`**:  
  En `--dump-single-json`, `yt-dlp` debe obtener la metadata completa de todos los formatos disponibles. La selección inteligente del mejor audio y del video 480p H.264 ya está implementada y probada en Kotlin dentro de `YtDlpNativeEngine.kt` (líneas 540-600) evaluando los formatos del JSON recibido.
- **Corregir `--extractor-args`**:  
  Remover `tv` y `web_embedded` que causan bloqueos de PO Token. Configurar los clientes limpios que no exigen login ni firma estricta (`android_vr,ios,mweb,android`) o permitir que `yt-dlp` use su configuración óptima.
- **Implementar Timeout Seguro en la Inspección**:  
  Incorporar un timeout controlado (con hilo o corrutina) para que si el proceso de Python no responde en un tiempo prudente, no bloquee la interfaz de usuario.
- **Optimizar `downloadStreamDirectToFile`**:  
  Asegurar que los argumentos de descarga directa usen el selector de formato flexible sin flags rotos.

### 2. Blindar el Flujo de Inspección en `WebStreamExtractor.kt` y `DownloadFromLinkDialog.kt`
- Asegurar que cuando el motor seleccionado sea `YTDLP`, se llame a `YtDlpNativeEngine.resolveStream` de forma prioritaria y limpia.
- Si `yt-dlp` resuelve exitosamente, devolver inmediatamente el `ResolvedMediaInfo` a la pantalla para mostrar el título, la duración, la carátula y el botón de descarga habilitado.

### 3. Garantizar el Avance de la Descarga en `OnlineVideoAudioImporter.kt` / `ChunkedStreamDownloader.kt`
- En `OnlineVideoAudioImporter.downloadAndImport`, si el flujo de audio de YouTube o video devuelve HTTP 403 o no transfiere bytes en `ChunkedStreamDownloader`, activar inmediatamente el método nativo de descarga directa (`YtDlpNativeEngine.downloadStreamDirectToFile`), asegurando que la barra de progreso reporte bytes reales (> 0 MB) y velocidad (> 0 KB/s) hasta el 100%.

---

## 🧪 Plan de Verificación

1. **Compilación**: Ejecutar `compile_applet` para garantizar que no existan errores de sintaxis, imports ni compatibilidad en el proyecto.
2. **Revisión de Casos Límite**:
   - Enlace estándar: `https://youtu.be/1_zgKRBrT0Y` (Pedro Capó - Calma).
   - Enlace con parámetros adicionales: `?si=...`, `?t=...`, `music.youtube.com`.
   - Videos oficiales VEVO y videos regulares.
