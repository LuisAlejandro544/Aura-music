# Auditoría de Seguridad y Plan de Remediación de Vulnerabilidades — Aura Music 🛡️

Siguiendo la **Fase 04 (El Crítico - Code Review & Seguridad)** del protocolo de ingeniería de **Aura Music**, he auditado de principio a fin las capas de red, IPC (comunicación entre procesos de Android), motores de extracción, sistema de archivos y lanzadores nativos C/C++20.

Tal como solicitaste, **se mantiene intacto el comportamiento actual de `SHA256SUMS.txt` en `AppReleaseUpdater`** (opcional cuando el Release aún no lo adjunta), y se corrigen todas las demás vulnerabilidades **Críticas, Altas y Medias** sin restar rendimiento ni alterar ninguna función ni el diseño de la aplicación.

---

## 🔍 Diagnóstico de las 7 Vulnerabilidades Detectadas y Cómo Arreglarlas

### 1. [ALTA] Exposición de Archivos Locales en `HeadlessWebViewExtractor` (`file://` / `content://` Access)
* **Dónde ocurre**: `app/src/main/java/com/example/data/importer/extractors/HeadlessWebViewExtractor.kt` (líneas 58–67 y 265).
* **Por qué es vulnerable**:
  - Aunque la versión de Android establece valores por defecto según el SDK, `WebSettings` tiene `javaScriptEnabled = true` y `domStorageEnabled = true`, pero **no deshabilita explícitamente** `allowFileAccess`, `allowContentAccess`, `allowFileAccessFromFileURLs` ni `allowUniversalAccessFromFileURLs`.
  - Además, `videoId` se interpola directamente en `https://m.youtube.com/watch?v=$videoId` sin verificar con expresión regular estricta (`^[a-zA-Z0-9_-]{11}$`) dentro de `HeadlessWebViewExtractor.resolve()`, y el `WebViewClient` no bloquea navegaciones/redirecciones hacia esquemas `file://`, `content://` o `javascript:`.
* **Cómo se arregla**:
  - Desactivar explícitamente `allowFileAccess = false` y `allowContentAccess = false` en `WebSettings`.
  - Validar `videoId` con `Regex("^[a-zA-Z0-9_-]{11}$")` antes de construir la URL.
  - Implementar `shouldOverrideUrlLoading` en el `WebViewClient` para permitir exclusivamente esquemas `https://` hacia dominios legítimos de YouTube/Google.

---

### 2. [ALTA] Fuga de Cookies y SSRF por Redirección HTTP en `ChunkedStreamDownloader` y `OnlineVideoAudioImporter`
* **Dónde ocurre**:
  - `app/src/main/java/com/example/data/importer/download/ChunkedStreamDownloader.kt` (líneas 22–27 y 102–112).
  - `app/src/main/java/com/example/data/importer/extractors/OnlineVideoAudioImporter.kt` (líneas 68–85).
* **Por qué es vulnerable**:
  1. **Bypass de validación de host por substring**: En `ChunkedStreamDownloader.kt`, la condición `url.contains("googlevideo.com") || url.contains("youtube.com")` puede ser engañada con una URL maliciosa tipo `https://attacker.com/?q=googlevideo.com`. Si un usuario comparte un enlace directo manipulado, el descargador **adjuntaría las cookies del `CookieManager` de `https://m.youtube.com` y las enviaría al servidor externo**.
  2. **SSRF / Redirección hacia red local (LAN / Loopback)**: `OnlineVideoAudioImporter` acepta cualquier enlace terminado en `.mp4/.m4a/.mp3/.webm` sin pasar por un filtro anti-IP privada (`127.0.0.1`, `192.168.x.x`, `10.x.x.x`, `169.254.x.x`, `localhost`), y `OkHttpClient` sigue redirecciones automáticamente (`followRedirects(true)`) sin un interceptor que verifique que el destino redirigido no apunte a una IP privada/local (DNS Rebinding / Open Redirect a LAN).
* **Cómo se arregla**:
  - Verificar el **host real parseado (`Uri.parse(url).host`)** (que sea exactamente `youtube.com`, `.youtube.com` o `.googlevideo.com`) antes de adjuntar cabeceras `Cookie` o `Referer`.
  - Añadir un validador de red segura (`NetworkSecurityValidator`) e interceptor en `ChunkedStreamDownloader.defaultHttpClient` que bloquee cualquier petición o redirección hacia direcciones loopback, link-local o rangos privados RFC1918 (`127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, `::1`), garantizando 0 ms de sobrecarga en descargas reales.

---

### 3. [MEDIA-ALTA] Inyección de Ruta / Path Traversal en el Slug de Repositorio (`AppReleaseUpdater`)
* **Dónde ocurre**: `app/src/main/java/com/example/data/updater/AppReleaseUpdater.kt` (líneas 206–219 y 262).
* **Por qué es vulnerable**:
  - `normalizeRepoSlug(input)` divide por `/` y toma `parts[0]/parts[1]`, pero no valida que `owner` y `repo` contengan únicamente caracteres válidos de GitHub (`[a-zA-Z0-9._-]`) ni bloquea secuencias `..` o parámetros de query/fragmento (`?`, `#`, `%`).
  - Al construir `URL("https://api.github.com/repos/$repoSlug/releases?per_page=15")`, un slug manipulado con `..` o `?` permitiría consultar otros endpoints arbitrarios dentro de `api.github.com`.
  - Nota: **Se preserva intacta la lógica actual de `SHA256SUMS.txt` opcional** tal como indicaste.
* **Cómo se arregla**:
  - Validar en `normalizeRepoSlug` que tanto `owner` como `repo` cumplan estrictamente el patrón `^[a-zA-Z0-9._-]+$` y no sean `.` ni `..`.

---

### 4. [MEDIA] Control No Autorizado de Sesión Multimedia y Comandos Falsificados en `AuraMediaPlaybackService`, `MainActivity` y `AuraMusicWidgetProvider`
* **Dónde ocurre**:
  - `app/src/main/java/com/example/playback/AuraMediaPlaybackService.kt` (líneas 58–64).
  - `app/src/main/java/com/example/MainActivity.kt` (líneas 44–65).
* **Por qué es vulnerable**:
  1. `AuraMediaPlaybackService` está exportado (`android:exported="true"`) para permitir Android Auto y System Media Controls, pero `onGetSession(controllerInfo: MediaSession.ControllerInfo)` devuelve la `MediaSession` activa a **cualquier paquete de terceros sin verificar** si el llamador es el propio paquete de la app, el sistema Android (`android`, `com.android.systemui`), el controlador de medios o Android Auto.
  2. `MainActivity` (también exportada) procesa `EXTRA_PLAY_DOWNLOADED_TRACK_ID` y `EXTRA_WIDGET_COMMAND` desde cualquier `Intent` externo sin distinguir si la acción vino de una notificación/widget legítimo o de una app externa que fuerza la reproducción de cualquier ID.
* **Cómo se arregla**:
  - En `AuraMediaPlaybackService.onGetSession()`, permitir conexiones de la propia app, de clientes de confianza del sistema (`isTrusted` / UID de sistema / SystemUI / Bluetooth / Android Auto / Launcher) y rechazar apps de terceros desconocidas sin permisos.
  - En `AuraMusicWidgetProvider` y `AuraDownloadService`, adjuntar un token de sesión en memoria de proceso (`internalAuthToken`) a los `PendingIntent` internos para que `MainActivity` verifique que los comandos `EXTRA_WIDGET_COMMAND` y `EXTRA_PLAY_DOWNLOADED_TRACK_ID` provienen exclusivamente de la propia instancia de Aura Music.

---

### 5. [MEDIA] Lectura de Archivos Internos mediante `Track.uriString` / Archivos Hermanos en `MixtapeEngine` y `LyricsManager`
* **Dónde ocurre**:
  - `app/src/main/java/com/example/data/importer/mixtape/MixtapeEngine.kt` (líneas 215–229).
  - `app/src/main/java/com/example/data/importer/lyrics/LyricsManager.kt` (líneas 504–529 y 465–485).
* **Por qué es vulnerable**:
  - En `LyricsManager.importLyricsFromUri`, se abre el `uri` directamente con `contentResolver.openInputStream(uri)` sin pasar primero por `IncomingMediaHandler.isSafeExternalUri(context, uri)` y sin límite de tamaño (un archivo gigante podría causar un `OutOfMemoryError` al hacer `.readText()`).
  - En `LyricsManager.autoDetectAndAssociateLyrics` y `MixtapeEngine.prepareAudioInputFile`, cuando una pista tiene esquema `file://`, no se verifica que la ruta canónica del archivo (o de su `.lrc`/`.txt` hermano) pertenezca a las carpetas permitidas (`songs/`, almacenamiento externo compartido) y no apunte mediante un symlink a bases de datos privadas (`databases/`, `shared_prefs/`).
* **Cómo se arregla**:
  - Validar `IncomingMediaHandler.isSafeExternalUri(context, uri)` en `LyricsManager.importLyricsFromUri` y limitar la lectura de archivos `.lrc`/`.txt` a un máximo seguro de 512 KB.
  - Verificar rutas canónicas (`canonicalFile`) en `LyricsManager` y `MixtapeEngine` para bloquear el acceso a `databases/`, `shared_prefs/` y `files/bin/`.

---

### 6. [MEDIA] Validación de Bibliotecas en `FFMPEG_LIB_DIR` y `LD_LIBRARY_PATH` en Lanzadores Nativos C (`native_ffmpeg_launcher.c`)
* **Dónde ocurre**: `app/src/main/cpp/native_ffmpeg_launcher.c` (líneas 42–56).
* **Por qué es vulnerable**:
  - `try_dlopen_in_dir` lee la variable de entorno `FFMPEG_LIB_DIR` y construye `snprintf(full_path, sizeof(full_path), "%s/%s", env_dir, lib_name)` sin validar que `env_dir` sea una ruta absoluta limpia libre de `..` (Path Traversal) ni verificar que el archivo no sea escribible por otros usuarios (`S_IWOTH`) antes de hacer `dlopen()`.
* **Cómo se arregla**:
  - Añadir validación en C (`stat()` y chequeo de ruta absoluta sin `..` ni permisos `S_IWOTH`) tanto en `native_ffmpeg_launcher.c` como en `native_python_launcher.c` antes de invocar `dlopen()`.

---

### 7. [BAJA-MEDIA] Respaldo de Resguardo en `allowBackup` para Archivos Ejecutables y Preferencias Internas
* **Dónde ocurre**: `app/src/main/res/xml/backup_rules.xml` y `app/src/main/res/xml/data_extraction_rules.xml`.
* **Por qué es vulnerable**:
  - `android:allowBackup="true"` está habilitado en el `AndroidManifest.xml`. Si las reglas de extracción/backup incluyen la carpeta `files/bin/` o `files/env/`, un respaldo ADB o transferencia de dispositivo podría extraer o inyectar binarios OTA (`yt-dlp`) o entornos Python entre dispositivos con distintas firmas/arquitecturas.
* **Cómo se arregla**:
  - Excluir explícitamente `bin/` y `env/` en `backup_rules.xml` y `data_extraction_rules.xml`, conservando el respaldo de las preferencias del usuario y base de datos musical.

---

## 🛠️ Archivos que se Modificarán

1. `app/src/main/java/com/example/data/importer/extractors/HeadlessWebViewExtractor.kt`: Blindaje de `WebSettings`, validación estricta de `videoId` y `shouldOverrideUrlLoading`.
2. `app/src/main/java/com/example/data/importer/download/ChunkedStreamDownloader.kt`: Validación estricta del host parseado para envío de cookies, bloqueo de IPs privadas/loopback (anti-SSRF y anti-DNS Rebinding en redirecciones).
3. `app/src/main/java/com/example/data/importer/extractors/OnlineVideoAudioImporter.kt`: Validación de URLs directas contra rangos internos/loopback.
4. `app/src/main/java/com/example/data/updater/AppReleaseUpdater.kt`: Sanitización estricta de `normalizeRepoSlug` contra Path Traversal / Query Injection (manteniendo intacta la lógica actual de `SHA256SUMS.txt`).
5. `app/src/main/java/com/example/playback/AuraMediaPlaybackService.kt`, `AuraMusicWidgetProvider.kt`, `AuraDownloadService.kt` y `MainActivity.kt`: Validación de llamadores IPC y token de autenticidad interno para intents de control/reproducción.
6. `app/src/main/java/com/example/data/importer/lyrics/LyricsManager.kt` y `app/src/main/java/com/example/data/importer/mixtape/MixtapeEngine.kt`: Validación canónica de rutas y límite de tamaño en lectura de letras.
7. `app/src/main/cpp/native_ffmpeg_launcher.c` y `app/src/main/cpp/native_python_launcher.c`: Verificación de rutas seguras y permisos con `stat()` antes de `dlopen()`.
8. `app/src/main/res/xml/backup_rules.xml` y `app/src/main/res/xml/data_extraction_rules.xml`: Exclusión de `bin/` y `env/` en respaldos del sistema.
