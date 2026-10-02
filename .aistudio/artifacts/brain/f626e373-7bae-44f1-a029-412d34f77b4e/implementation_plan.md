# Plan de Integración C++20, Carátulas Personalizadas, Playlists Reactivas, Animaciones y GitHub Actions

Plan de ingeniería para completar la integración nativa del motor DSP C++20 en el APK final, incorporar el selector de carátulas personalizadas desde galería con eliminación física del arte previo, habilitar la gestión de playlists sincronizadas con persistencia Room en la Biblioteca (incluyendo lista automática de canciones favoritas), perfeccionar las animaciones de interfaz y configurar un flujo de integración continua en GitHub Actions de ejecución manual con firma autónoma y script shell.

---

## Decisiones Críticas y Resumen de Alcance

> [!IMPORTANT]
> Este plan consolida las 5 áreas solicitadas respetando estrictamente el manual de operaciones `AGENTS.md`, la versión mínima `minSdk = 26`, la autonomía total sin archivos `.env` y el diseño con fondos OLED 100% opacos:
>
> 1. **C++20 NDK Integrado en el APK**: Se añade la configuración `externalNativeBuild` con `cmake` y filtros ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) en `app/build.gradle.kts` para empaquetar la librería `libauramusic_dsp.so` real en el APK, manteniendo intacto el respaldo matemático en Kotlin en caso de fallos de entorno.
> 2. **Carátulas Personalizadas desde Galería**: Implementación del selector moderno Android Photo Picker (`ActivityResultContracts.PickVisualMedia`). Al elegir una nueva imagen, se convierte a WebP sin pérdida en `Dispatchers.IO`, se elimina físicamente el archivo de carátula WebP anterior en `images/` para evitar acumulación de basura y se actualiza la base de datos Room.
> 3. **Gestión Completa de Playlists en la Biblioteca**:
>    - Pestaña "Playlists" completamente operativa en `LibraryScreen`.
>    - Playlist fija "Tus Me Gusta" / Favoritos que sincroniza dinámicamente las canciones marcadas con corazón.
>    - Creación y edición de nombres de playlists personalizadas con diálogo estilizado.
>    - Modal contextual para añadir/quitar canciones a playlists desde cualquier canción o desde la vista de detalle.
> 4. **Pulido de Animaciones del Sistema**:
>    - Transiciones de pantalla suaves con interpolación `tween` / `spring` en Compose.
>    - Expansión/colapso continuo del mini reproductor hacia Now Playing.
>    - Animación fluida de la barra de progreso y barras del ecualizador.
> 5. **GitHub Actions y Script Shell Autónomo**:
>    - Archivo `.github/workflows/build-debug-apk.yml` con trigger manual (`workflow_dispatch`), configuración de NDK/CMake, caché Gradle opcional, generación dinámica de `debug.keystore` en el runner y empaquetado del APK Debug.
>    - Script `scripts/generate_keystore_and_build.sh` 100% ejecutable que crea la firma desde cero con `keytool` y ejecuta la compilación sin requerir credenciales externas.

---

## 1. Experiencia de Usuario y Flujos

### Flujo de Selección de Carátula Personalizada
1. El usuario abre el menú de opciones (tres puntos) de cualquier canción o el botón de edición en `NowPlayingScreen`.
2. En el diálogo `EditTrackDialog`, se visualiza la carátula actual (WebP o procedural) y un botón destacado "Cambiar Carátula".
3. Al pulsarlo, se lanza el Photo Picker nativo del sistema. Al seleccionar una imagen, se muestra una previsualización inmediata.
4. Al presionar "Guardar", un corrutina en `Dispatchers.IO` comprime la imagen a WebP en `Android/data/.../files/images/`, elimina el archivo `.webp` anterior de dicha pista, actualiza el campo `artworkUri` en Room y refresca instantáneamente la interfaz.

### Flujo de Playlists en la Biblioteca
1. En `LibraryScreen`, el usuario selecciona la pestaña "Playlists".
2. Se muestra en primer lugar la tarjeta especial "Tus Me Gusta" con gradiente neón distintivo y contador de canciones favoritas.
3. Se incluye el botón "+ Nueva Playlist" para crear listas con nombre personalizado.
4. Cada canción cuenta con la opción "Añadir a playlist" en su menú desplegable.
5. Al hacer clic en cualquier playlist, se navega a `PlaylistDetailScreen` con reproducción de la lista, edición del título, opción de añadir pistas y reordenación.

### Micro-interacciones y Animaciones Fluidas
* **Transiciones de vista**: Animaciones de desvanecimiento con deslizamiento sutil (`slideInVertically` / `fadeIn`) entre pantallas secundarias y el reproductor principal.
* **Now Playing Expandible**: Apertura fluida desde el mini reproductor con amortiguación natural.
* **Pulsación de Controles**: Feedback táctil con escala elástica en los botones Play/Pause, Shuffle y Like.

---

## 2. Decisiones Técnicas y Arquitectura

```
┌─────────────────────────────────────────────────────────────────┐
│                       Aura Music Compose UI                     │
│  HomeScreen  │  LibraryScreen (Playlists)  │  NowPlayingScreen  │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                     ┌───────────▼───────────┐
                     │     MusicViewModel    │
                     └─────┬───────────┬─────┘
                           │           │
            ┌──────────────▼─────┐  ┌──▼─────────────────────────┐
            │  MusicRepository   │  │   AuraAudioPlayer (Media3) │
            └──────┬───────┬─────┘  └──┬─────────────────────────┘
                   │       │           │
     ┌─────────────▼─┐  ┌──▼─────────┐ │
     │  Room Database│  │ AppStorage │ │
     │ (Tracks &     │  │ Manager    │ │
     │  Playlists)   │  │ (WebP/Loss)│ │
     └───────────────┘  └────────────┘ │
                                       │ PCM Audio Buffer
                        ┌──────────────▼─────────────┐
                        │ NativeAudioProcessor (JNI) │
                        └──────────────┬─────────────┘
                                       │ std::span
                        ┌──────────────▼─────────────┐
                        │   auramusic_dsp.cpp C++20  │
                        │ (10-Band Biquads + Limiter)│
                        └────────────────────────────┘
```

### Integración NDK en `app/build.gradle.kts`
Se declara el enlace nativo para asegurar que CMake compile el módulo `auramusic_dsp` para las arquitecturas de 32 y 64 bits:
```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"))
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

### Gestión de Carátulas en `AppStorageManager`
* Función `saveCustomArtwork(trackId: Long, sourceUri: Uri): String?`: decodifica la imagen de la galería, la comprime a WebP sin pérdida en `images/track_{id}_{timestamp}.webp` y elimina el archivo anterior si existía.

### Modelo de Playlists en Room
* `PlaylistEntity`: `id`, `name`, `createdAt`, `isFavorites`.
* `PlaylistTrackCrossRef`: asociación muchos a muchos entre pistas y listas.
* Sincronización continua de la playlist de "Me Gusta" con la propiedad `isFavorite` de `TrackEntity`.

---

## 3. GitHub Actions & Automatización de Compilación

### Workflow: `.github/workflows/build-debug-apk.yml`
* **Activación**: Únicamente manual (`workflow_dispatch`).
* **Etapas**:
  1. *Checkout* del repositorio completo.
  2. Configuración de JDK 17.
  3. Instalación de Android NDK y CMake para compilar las bibliotecas C++20.
  4. Caché de dependencias Gradle para acelerar ejecuciones posteriores.
  5. Generación autónoma de un `debug.keystore` si no existe en el repositorio mediante `keytool`.
  6. Compilación de `gradle :app:assembleDebug`.
  7. Publicación del artefacto APK Debug generado para descarga directa.

### Script Shell: `scripts/generate_keystore_and_build.sh`
* Script en Bash que regenera de forma limpia una clave debug RSA de 2048 bits con validez de 10.000 días y ejecuta la compilación nativa completa localmente o dentro del CI.

---

## 4. Plan de Ejecución Paso a Paso

1. **Paso 1: Configuración Gradle C++20**: Modificar `app/build.gradle.kts` para incluir el bloque `externalNativeBuild` con CMake y las ABI filters correspondientes.
2. **Paso 2: Almacenamiento y Carátulas Personalizadas**: Implementar la lógica de reemplazo WebP y borrado seguro de la carátula previa en `AppStorageManager` y conectar el Photo Picker en `EditTrackDialog`.
3. **Paso 3: Sincronización de Playlists en Room y ViewModel**: Extender `PlaylistDao`, `MusicRepository` y `MusicViewModel` con flujos reactivos para crear playlists, añadir pistas y mantener la lista fija de "Tus Me Gusta".
4. **Paso 4: Pantallas de Biblioteca y Detalle de Playlist**: Actualizar `LibraryScreen` para listar las playlists con diseño de tarjetas neón y afinar `PlaylistDetailScreen`.
5. **Paso 5: Pulido de Animaciones**: Incorporar transiciones animadas consistentes en la navegación, el mini reproductor y los botones de control.
6. **Paso 6: Workflow de GitHub Actions y Script Shell**: Crear `.github/workflows/build-debug-apk.yml` y `scripts/generate_keystore_and_build.sh`.
7. **Paso 7: Actualización de Documentación**: Reflejar los cambios en `README.md`, `ROADMAP.md` y `STRUCTURE.md`.
8. **Paso 8: Verificación**: Compilar el proyecto con `compile_applet` para asegurar cero errores de compilación.
