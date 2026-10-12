# Plan de Implementación: Corrección de Descargas Directas con yt-dlp y Reorganización Modular

## 🎯 Objetivo
Resolver la falla donde `yt-dlp` inspecciona y resuelve la URL exitosamente pero la descarga se congela en `0.0 MB` (debido al rechazo HTTP 403 de OkHttp con Google Video), e implementar la descarga directa con `yt-dlp` como primer paso prioritario con emisión de progreso en vivo en la interfaz, manteniendo OkHttp como respaldo. Asimismo, reorganizar la integración nativa de `yt-dlp` en submódulos especializados menores a 500 líneas para limpiar el código sin romper compatibilidad.

---

## 🔍 Diagnóstico del Problema Actual
1. **Fallo de Descarga en 0.0 MB:**
   - `YtDlpNativeEngine.resolveMediaInfo` extrae las URLs de `googlevideo.com` usando Python y OpenSSL.
   - `OnlineVideoAudioImporter.downloadAndImport` intenta descargar esas URLs usando `ChunkedStreamDownloader` (OkHttp).
   - Google Video vincula el token (`n-sig`, `sig`, sesión) a la huella TLS de la petición original, rechazando a OkHttp con `HTTP 403 Forbidden` o cerrando el socket.
   - La sonda `Range: bytes=0-0` de OkHttp falla y entra en timeouts repetidos, dejando la barra de progreso en `0.0 MB` y `-- KB/s`.
   - La función nativa `YtDlpNativeEngine.downloadStreamDirectToFile()` estaba relegada al último lugar de la cascada de fallos.
2. **Desorden Arquitectónico ("Todo está revuelto"):**
   - `YtDlpNativeEngine.kt` contiene casi 1,200 líneas mezclando:
     - Configuración del runtime CPython, symlinks de módulos C y certificados TLS de Conscrypt.
     - Extracción JSON de metadatos e inspección de formatos.
     - Descarga CLI y puente con FFmpeg / QuickJS.
   - Esto dificulta el mantenimiento e incumple la directriz de mantener archivos bajo 500 líneas.

---

## 🛠️ Plan de Cambios Propuesto

### 1. Modularización de la Integración yt-dlp (`core/data/importer/engines/`)
Dividir la responsabilidad de `YtDlpNativeEngine` en componentes modulares independientes (< 500 líneas):
- **`YtDlpEnvironmentSetup.kt`**:
  - Detección e inicialización del runtime CPython (`libpython.so`), QuickJS (`libqjs.so`) y FFmpeg.
  - Creación de symlinks de 0 bytes para `libpymod_*.so`.
  - Consolidación y validación de certificados TLS CA de Android 14+ (`usr/etc/tls/cert.pem`).
- **`YtDlpStreamDownloader.kt`**:
  - Ejecución del proceso de descarga directa con `yt-dlp` (`-o`, `--newline`, formato específico).
  - Captura y parseo en tiempo real del progreso de salida de `yt-dlp` (`[download] XX.X% of ... at ... ETA ...`) para emitir `DownloadProgress` hacia la interfaz y la notificación del sistema.
  - Soporte tanto para audio (`bestaudio[ext=m4a]/bestaudio`) como para Video Canvas en 480p (`bestvideo[height<=480][ext=mp4]`).
- **`YtDlpNativeEngine.kt` (Fachada Principal)**:
  - Mantener al 100% su API pública (`init`, `isAvailable`, `resolveStream`, `downloadStreamDirectToFile`, etc.) delegando limpiamente en los submódulos.

### 2. Corrección del Flujo de Descarga en `OnlineVideoAudioImporter.kt`
- Cuando una pista se resuelve con `yt-dlp` (o es un enlace de YouTube y `yt-dlp` está disponible):
  1. **Intento 1 (Directo con yt-dlp):** Descargar el audio directamente usando `YtDlpNativeEngine.downloadStreamDirectToFile` con progreso en vivo.
  2. **Video Canvas con yt-dlp:** Si el usuario solicita Video Canvas (`attachAsCanvas = true`), descargar el video en 480p directamente con `yt-dlp` manteniendo la misma sesión segura.
  3. **Respaldo con OkHttp:** Si `yt-dlp` no estuviese disponible o retornase error, activar la descarga acelerada por bloques o cascada de OkHttp (`ChunkedStreamDownloader`).

### 3. Progreso en Vivo en la UI y Notificación
- Conectar la lectura línea a línea del stdout de `yt-dlp` con el callback `onProgressUpdate(DownloadProgress)`.
- Mostrar porcentaje real, velocidad en MB/s o KB/s y tamaño estimado directamente en `DownloadProgressStatusCard` y en la notificación de `AuraDownloadService`.

---

## 🧪 Plan de Verificación
1. **Compilación del Proyecto:** Ejecutar `compile_applet` para confirmar cero errores de tipos, sintaxis o enlaces nativos.
2. **Revisión de Reglas:** Verificar que no se superen las 500 líneas en los archivos nuevos/modificados y que la compatibilidad hacia atrás permanezca intacta.
