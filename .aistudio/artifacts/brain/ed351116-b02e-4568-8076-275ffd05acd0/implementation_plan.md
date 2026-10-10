# Carpeta Personalizada de Usuario & Exportación de Vídeos con Audio en Aura Music

Este plan detalla la incorporación de un sistema de **Carpeta Pública / Personalizada elegida por el usuario** dentro de **Ajustes > Medios & Almacenamiento**, permitiendo que cualquier usuario elija (o cree desde el selector de su teléfono) su propia carpeta libre de restricciones (`Android/data/`), visualice en todo momento la ruta exacta donde se guardan sus archivos y exporte sus vídeos descargados en formato `.mp4` **con su pista de audio completa incluida** mediante fusión instantánea en FFmpeg.

---

## Revisión del Usuario y Decisiones Confirmadas

> [!IMPORTANT]
> **Decisiones confirmadas por el usuario en la fase de planificación:**
> - **Ubicación de la Carpeta**: El propio usuario crea y selecciona la carpeta que desee en el almacenamiento de su teléfono (mediante el selector nativo de carpetas de Android `OpenDocumentTree` con permisos persistentes), sin estar encerrado en rutas restringidas del sistema.
> - **Formato del Vídeo Entregado**: **Vídeo completo con su audio incluido** (uniendo la pista de vídeo y la pista de audio de la canción mediante `FFmpeg` de forma rápida con `-c copy`), para que pueda reproducirlo con sonido en su galería, reproductor externo o compartirlo en redes sociales.
> - **Punto de Acceso e Interfaz**: Integrado directamente en **Ajustes > Medios & Almacenamiento**, con visualización clara de la ruta exacta configurada, selector/cambiador de carpeta y botón en cada tarjeta de canción con vídeo para guardarlo en dicha carpeta (además de preparación arquitectónica para habilitar imágenes WebP/PNG en el futuro).

---

## 1. Visión General y Concepto Principal

- **Qué hace**:
  1. Añade en **Ajustes > Medios & Almacenamiento** un panel interactivo de **"Carpeta de Descargas del Usuario (Acceso Libre)"** donde el usuario puede pulsar **"Elegir / Crear Carpeta"** usando el explorador nativo de su teléfono.
  2. Dentro de la carpeta elegida por el usuario, Aura Music organiza automáticamente una subcarpeta **`Videos/`** (dejando lista la arquitectura para añadir `Imagenes/` próximamente sin romper nada).
  3. Muestra de forma transparente la **ruta legible exacta** de la carpeta seleccionada (por ejemplo: `Almacenamiento interno > Mi Música Aura > Videos`) y el estado de los vídeos exportados.
  4. En cada canción listada en **Ajustes > Medios & Almacenamiento** que tenga un vídeo descargado, incorpora un botón de **"Guardar vídeo en mi carpeta"** (además de opción de exportar todos los vídeos de un toque) que combina instantáneamente en segundo plano el flujo de vídeo con el audio de alta fidelidad de la canción usando `FFmpeg` y deposita el archivo `.mp4` con nombre limpio (`Artista - Título.mp4`) en la carpeta del usuario.
- **Por qué no afecta al reproductor**:
  - El archivo interno de Video Canvas (`Android/data/.../files/videos/`) permanece intacto y sin audio (`-an`) para que el reproductor principal, el modo Cinemático y el ecualizador C++20 sigan funcionando a 0ms sin interferencias de doble sonido. La copia que se entrega en la carpeta elegida por el usuario es un archivo `.mp4` completo e independiente con sonido.

---

## 2. Experiencia de Usuario y Diseño Visual

### Flujo Paso a Paso del Usuario
1. **Configuración de la Carpeta Personalizada**:
   - El usuario entra en **Ajustes > Medios & Almacenamiento**.
   - En la parte superior (debajo del resumen de espacio ocupado), visualiza la nueva tarjeta **"Carpeta Pública de Usuario (Vídeos)"**.
   - Si aún no ha seleccionado una carpeta, ve un indicador claro y el botón **"Crear o Seleccionar Carpeta"**. Al tocarlo, se abre el selector oficial de carpetas de Android donde el usuario puede crear una carpeta nueva en su teléfono o tocar cualquiera existente y darle a *"Usar esta carpeta"*.
   - Una vez seleccionada, la tarjeta muestra la **ruta exacta en texto claro** (ej. `Memoria interna / Aura Videos / Videos`) y un botón para **"Cambiar carpeta"** cuando quiera.
2. **Exportación de Vídeos con Audio Completo**:
   - En la lista de canciones de **Ajustes > Medios & Almacenamiento** (o filtrando por el chip *"Videos"*), cada tarjeta de canción que posee un vídeo muestra ahora, junto al botón de borrar, la acción **"Guardar en carpeta"** (o **"Guardado ✓"** si ya fue exportado a la carpeta del usuario).
   - Si el usuario pulsa **"Guardar en carpeta"** sin haber elegido aún una carpeta destino, la app abre automáticamente el selector de carpetas primero y luego guarda el vídeo inmediatamente.
   - Durante el segundo que toma unir el vídeo y el audio en FFmpeg, el botón muestra un indicador de progreso en vivo y al terminar confirma la ruta exacta donde quedó disponible el `.mp4`.

### Identidad Visual y Estética
- **Estética**: Mantiene fielmente el estilo **Dark Luxury Neo-Glass OLED** de Aura Music (`SurfaceCard`, bordes `CardBorder` de `1.dp`, superficies 100% opacas y acentos reactivos del tema activo).
- **Ergonomía Móvil**: Botones con altura y área táctil mínima garantizada de `48.dp`, textos con `fontScale = 1.0f` y diseño limpio sin saturar ni alterar ninguna otra pantalla de la aplicación.

---

## 3. Decisiones Clave de Producto e Ingeniería

- **Decisión 1: Uso de `Storage Access Framework (OpenDocumentTree)` con Permisos Persistentes**
  - *Enfoque Elegido*: Usar `ActivityResultContracts.OpenDocumentTree()` y registrar el permiso persistente de lectura/escritura con `takePersistableUriPermission`.
  - *Por qué*: Cumple al 100% con las políticas de almacenamiento de Android 8.0 a Android 15+ sin pedir permisos invasivos de acceso total al disco (`MANAGE_EXTERNAL_STORAGE`), permite que el usuario cree la carpeta donde él quiera en su teléfono y los archivos resultantes son visibles inmediatamente en cualquier gestor de archivos o galería.
- **Decisión 2: Ensamblaje de Vídeo + Audio con FFmpeg (`mergeVideoCanvasWithTrackAudio`)**
  - *Enfoque Elegido*: Como el Video Canvas interno se almacena sin audio (`-an`) y a veces como un bucle corto (`<= 20s`), cuando el vídeo tiene duración completa se hace un *Direct Stream Copy* (`-c:v copy -c:a copy -shortest`) ultrarrápido (~1 segundo); y si el Video Canvas es un loop corto mientras la canción dura 3 minutos, se ofrece unión directa o repetición cíclica (`-stream_loop -1 -i video.mp4 -i audio.m4a -shortest -c:v copy -c:a aac`) para que el `.mp4` tenga sonido de principio a fin sin cortes.
  - *Por qué*: Garantiza que el usuario nunca reciba un vídeo mudo al abrirlo desde sus archivos.
- **Decisión 3: Estructura Escalable (`Videos/` hoy, `Imagenes/` mañana)**
  - *Enfoque Elegido*: El nuevo gestor modular `UserPublicMediaExporter` gestiona subdirectorios por categoría (`Videos` activo ahora, y preparado para `Images` en el futuro) usando `DocumentFile` en `Dispatchers.IO`.

---

## 4. Arquitectura Técnica y Estrategia de Datos

### Diagrama de Arquitectura y Flujo de Datos

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                UI: Ajustes > Medios & Almacenamiento                    │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │ Tarjeta: Carpeta de Usuario (Muestra ruta exacta + Botón Elegir)  │  │
│  └───────────────────────────────┬───────────────────────────────────┘  │
│                                  │                                      │
│  ┌───────────────────────────────▼───────────────────────────────────┐  │
│  │ Lista de Canciones (StoredMediaTrackCard)                         │  │
│  │  • Muestra peso de Carátula WebP y Video Canvas MP4               │  │
│  │  • [Nuevo] Botón "Guardar vídeo con audio en mi carpeta"          │  │
│  └───────────────────────────────┬───────────────────────────────────┘  │
└──────────────────────────────────┼──────────────────────────────────────┘
                                   │ Eventos Compose
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│             ViewModel & Coordinador (TrackLibraryCoordinator)           │
│  • Persiste URI de carpeta elegida en SharedPreferences                 │
│  • Expone ruta legible humana y estado de exportación por canción       │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │ Coroutines (Dispatchers.IO)
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│          Nuevo Módulo: UserPublicMediaExporter (Storage Layer)          │
│  1. Verifica/crea subcarpeta "Videos" dentro del DocumentTree elegido   │
│  2. Invoca FFmpegNativeEngine.mergeAudioAndVideo() en archivo temporal  │
│     combinando el Video Canvas (.mp4) + el Audio de la pista (.m4a)     │
│  3. Escribe el MP4 final ("Artista - Titulo.mp4") en la carpeta SAF     │
│     del usuario e indexa en MediaStore para que aparezca en Galería     │
└─────────────────────────────────────────────────────────────────────────┘
```

### Mapeo de Componentes Interactivos y Estado
- **Selector de Carpeta (`OpenDocumentTree`)**:
  - Al seleccionar la carpeta, se invoca `contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)`.
  - Un convertidor de URI SAF traduce el identificador del documento (ej. `primary:MiCarpeta/Aura`) a una ruta amigable para el usuario (`Almacenamiento interno / MiCarpeta / Aura / Videos`).
- **Exportación Individual y Masiva en Segundo Plano (`Dispatchers.IO`)**:
  - Controla estados de carga (`Exporting`, `Success`, `Error`) por `track.id` para mostrar feedback visual inmediato en la tarjeta sin congelar la interfaz.
  - Sanitiza los nombres de archivo (`Artista - Título.mp4`) eliminando caracteres reservados para que sean 100% compatibles con el sistema de archivos FAT32/exFAT/ext4 de cualquier teléfono Android.
