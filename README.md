# Aura Music 🎵

**Aura Music** es un reproductor de música local de alta fidelidad para Android, diseñado con una estética moderna e inmersiva inspirada en Spotify, pero enriquecida con colores vibrantes, gradientes de neón, fondos oscuros OLED 100% opacos y procesamiento de audio avanzado.

Está construido con las tecnologías más modernas del ecosistema Android: **Jetpack Compose (Material 3)**, **Jetpack Media3 (ExoPlayer)**, **Room Persistence**, **Coroutines / StateFlow**, un **motor DSP nativo en ISO C++20** y almacenamiento estructurado de datos.

---

## 🌟 Características Principales

### 1. Interfaz Visual & Temas Personalizables
- **Estética Dark Luxury OLED**: Superficies opacas de alto contraste sin transparencias indeseadas ni filtraciones de fondo.
- **4 Paletas de Acentos Vibrantes**:
  - 🌌 **Nebula Violet**: Violeta eléctrico y cyan neón futurista.
  - 🍃 **Cyber Mint**: Esmeralda brillante y menta líquida.
  - 🔥 **Sunset Ember**: Coral cálido, naranja fuego y destellos dorados.
  - 🌊 **Ocean Abyss**: Azul zafiro profundo y agua bioluminiscente.
- **Mini Reproductor Flotante**:
  - Barra persistente con barra de progreso delgada y controles táctiles.
  - **Acceso Directo al Ecualizador C++20** con un solo toque.
  - Fondo completamente opaco para una integración visual impecable.
- **Pantalla Completa Now Playing**:
  - Carátula con halo ambiental y sombra dinámica flotante.
  - **Visualizador de ondas de audio animado** en tiempo real.
  - Deslizador de búsqueda interactivo con formato de tiempo `mm:ss`.
  - Controles de reproducción: Aleatorio (*Shuffle*), Anterior, Gran botón Play/Pause luminoso, Siguiente y Repetición cíclica (*Off*, *All*, *One*).
  - Hoja de especificaciones de audio (FLAC, MP3, WAV, bitrate, peso y ruta) con opción para **editar metadatos**.
  - Hoja desplegable con la **Cola de Reproducción (*Up Next*)**.

### 2. Carátulas Procedurales y Compresión WebP Lossless
- **Conversión a WebP en Segundo Plano**: Las carátulas incrustadas se extraen y comprimen automáticamente a formato **WebP sin pérdida de calidad (Lossless)** en hilos secundarios (`Dispatchers.IO`), optimizando drásticamente el espacio y la velocidad de carga.
- **Generador de Arte Procedural**: Si una canción importada no tiene carátula, Aura Music genera dinámicamente una obra de arte geométrica vectorial única basada en el algoritmo de dispersión matemática del título y artista, con surcos de vinilo y gradientes de neón.

### 3. Edición de Metadatos de Canciones
- Diálogo integrado para editar **título, artista y álbum** directamente desde el menú de opciones de cualquier canción o desde la vista de reproducción.
- Los cambios se sincronizan en tiempo real en la base de datos Room y en los archivos JSON de metadatos.

### 4. Estructura Organizada de Almacenamiento
Ubicado en el almacenamiento privado del paquete `Android/data/com.aistudio.musicplayer.aurasound/files/`:
- 📁 **`images/`**: Carátulas de álbumes convertidas a WebP sin pérdida.
- 📁 **`songs/`**: Canciones locales y pistas sintetizadas de demostración.
- 📁 **`lyrics/`**: Archivos de letras sincronizadas (`.lrc`) y texto.
- 📁 **`metadata/`**: Ficheros JSON estructurados con información técnica y artística de cada pista.

### 5. Motor de Audio DSP Nativo en C++20 (10 Bandas ISO)
- **Ecualizador Paramétrico de 10 Bandas**:
  - Frecuencias centrales: `31 Hz, 62 Hz, 125 Hz, 250 Hz, 500 Hz, 1 kHz, 2 kHz, 4 kHz, 8 kHz, 16 kHz`.
  - Filtros IIR Bi-cuadráticos (*Peaking Biquads*) en coma flotante de doble precisión (64-bit).
  - Rango de ganancia de `-15 dB` a `+15 dB`.
- **Refuerzo de Bajos C++ (Bass Boost)**:
  - Curva de ganancia calibrada a 60 Hz con factor Q armónico y control de 0% a 100%.
- **Limitador Analógico Suave (Anti-Clipping)**:
  - Algoritmo de saturación cúbica en C++ que evita distorsiones digitales cuando las bandas están elevadas.
- **Acceso Exclusivo**: El ecualizador se controla de manera lógica e intuitiva desde el **Mini Reproductor** y desde la pantalla de reproducción.

### 6. Privacidad Total (Storage Access Framework)
- **Cero Escaneo Ciego**: No rastrea el disco del teléfono sin autorización.
- Importación selectiva de canciones (`OpenMultipleDocuments`) y carpetas (`OpenDocumentTree`).
- Permisos URI persistentes con `takePersistableUriPermission`.
- Generador de demostraciones Synthwave para pruebas inmediatas sin requerir archivos en el teléfono.

---

## 📱 Requisitos del Sistema

- **Versión mínima de Android**: Android 8.0 (Oreo, API 26) o superior.
- **Versión objetivo**: Android 14 / 15+ (API 36).
- **Arquitecturas soportadas**: 64 bits (`arm64-v8a`, `x86_64`) y 32 bits (`armeabi-v7a`, `x86`).
- **Autónomo**: No requiere archivos `.env` ni claves externas.

---

## 🛠️ Compilación e Instalación

```bash
# Compilar el APK de depuración
gradle :app:assembleDebug

# Ejecutar las pruebas unitarias y de Robolectric
gradle :app:testDebugUnitTest
```

---

## 📦 Distribución

Aura Music está preparado para distribuirse libremente como APK independiente en tiendas como **Uptodown**, repositorios independientes o instalación manual directa.
