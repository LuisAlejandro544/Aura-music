# Aura Music 🎵

**Aura Music** es un reproductor de música local de alta fidelidad para Android, diseñado con una estética moderna e inmersiva inspirada en Spotify, pero enriquecida con colores vibrantes, gradientes de neón, fondos oscuros OLED 100% opacos y procesamiento de audio avanzado.

Está construido con las tecnologías más modernas del ecosistema Android: **Jetpack Compose (Material 3)**, **Jetpack Media3 (ExoPlayer)**, **Room Persistence**, **Coroutines / StateFlow**, un **motor DSP nativo compilado en ISO C++20 con CMake** y almacenamiento estructurado de datos.

---

## 🌟 Características Principales

### 1. Interfaz Visual, Temas y Animaciones Fluidas
- **Estética Dark Luxury OLED**: Superficies 100% opacas de alto contraste sin transparencias indeseadas ni filtraciones de fondo.
- **Transiciones y Animaciones del Sistema**:
  - Cambio entre pantallas con animación combinada de desvanecimiento y deslizamiento suave (`AnimatedContent`).
  - Despliegue elástico amortiguado de la pantalla completa Now Playing desde el mini reproductor.
  - Indicadores y micro-interacciones táctiles con retroalimentación inmediata.
- **4 Paletas de Acentos Vibrantes**:
  - 🌌 **Nebula Violet**: Violeta eléctrico y cyan neón futurista.
  - 🍃 **Cyber Mint**: Esmeralda brillante y menta líquida.
  - 🔥 **Sunset Ember**: Coral cálido, naranja fuego y destellos dorados.
  - 🌊 **Ocean Abyss**: Azul zafiro profundo y agua bioluminiscente.
- **Mini Reproductor Flotante**:
  - Barra persistente con barra de progreso y controles táctiles.
  - **Acceso Directo al Ecualizador C++20** con un solo toque.
- **Pantalla Completa Now Playing**:
  - Visualizador de ondas animado en tiempo real.
  - Deslizador de búsqueda interactivo con formato de tiempo `mm:ss`.
  - Hoja de especificaciones de audio y modal para **editar metadatos y carátula**.

### 2. Carátulas Personalizadas de Galería & Arte Procedural
- **Selección de Carátula desde Galería**: Mediante el Android Photo Picker nativo del sistema, el usuario puede asignar cualquier imagen personal a una pista.
- **Compresión WebP y Borrado Inteligente**: La nueva carátula se comprime automáticamente a formato **WebP sin pérdida (Lossless)** en hilos secundarios (`Dispatchers.IO`), y la carátula previa se elimina físicamente del disco para evitar archivos residuales.
- **Generador de Arte Procedural**: Si una canción no tiene carátula o el usuario desea restaurarla, se genera una ilustración vectorial matemática única basada en los metadatos de la pista.

### 3. Sistema Completo de Playlists en la Biblioteca
- **Pestaña "Playlists" en Tu Biblioteca**:
  - **Tarjeta Especial "Tus Me Gusta"**: Sincronización automática de todas las canciones marcadas con corazón en tiempo real.
  - **Listas Personalizadas**: Creación de nuevas playlists con nombre y descripción libre.
  - **Renombrado y Edición**: Opción para cambiar el nombre de cualquier playlist en cualquier momento.
  - **Añadir Canciones Rápidamente**: Diálogo modal con buscador dentro de la playlist para agregar múltiples canciones de la biblioteca con un toque.
  - **Menú Contextual**: Posibilidad de añadir o quitar pistas de playlists desde el menú de 3 puntos de cada canción.

### 4. Estructura Organizada de Almacenamiento
Ubicado en el almacenamiento privado del paquete `Android/data/com.aistudio.musicplayer.aurasound/files/`:
- 📁 **`images/`**: Carátulas de álbumes y personalizadas en WebP Lossless.
- 📁 **`songs/`**: Canciones locales y pistas sintetizadas de demostración.
- 📁 **`lyrics/`**: Archivos de letras sincronizadas (`.lrc`) y texto.
- 📁 **`metadata/`**: Ficheros JSON estructurados con información técnica de cada pista.

### 5. Motor de Audio DSP Nativo en C++20 (10 Bandas ISO)
- **Compilado nativamente con CMake**: Integrado en el APK final para arquitecturas de 64 bits (`arm64-v8a`, `x86_64`) y 32 bits (`armeabi-v7a`, `x86`).
- **Ecualizador Paramétrico de 10 Bandas**:
  - Frecuencias centrales ISO: `31 Hz, 62 Hz, 125 Hz, 250 Hz, 500 Hz, 1 kHz, 2 kHz, 4 kHz, 8 kHz, 16 kHz`.
  - Filtros IIR Bi-cuadráticos (*Peaking Biquads*) en coma flotante de 64 bits.
  - Rango de ganancia de `-15 dB` a `+15 dB`.
- **Refuerzo de Bajos C++ (Bass Boost)**:
  - Curva de ganancia calibrada a 60 Hz con control de 0% a 100%.
- **Limitador Suave Anti-Clipping**:
  - Algoritmo de saturación cúbica que evita distorsiones digitales cuando las bandas están elevadas.

### 6. Privacidad Total (Storage Access Framework)
- **Cero Escaneo Ciego**: No rastrea el disco del teléfono sin autorización.
- Importación selectiva de canciones (`OpenMultipleDocuments`) y carpetas (`OpenDocumentTree`).
- Generador de canciones demostrativas Synthwave listo para usar.

---

## 📱 Requisitos del Sistema

- **Versión mínima de Android**: Android 8.0 (Oreo, API 26) o superior.
- **Versión objetivo**: Android 14 / 15+ (API 36).
- **Arquitecturas soportadas**: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`.
- **Autónomo**: No requiere archivos `.env` ni claves externas.

---

## 🛠️ Compilación y GitHub Actions

### Compilación Local con Generación Limpia de Firma:
```bash
# Ejecutar script que genera la firma debug desde cero y compila el APK
./scripts/generate_keystore_and_build.sh
```

### GitHub Actions (Activación Manual):
- Se incluye el flujo `.github/workflows/build-debug-apk.yml`.
- Se activa manualmente desde la pestaña **Actions -> Run workflow** (`workflow_dispatch`).
- Genera la firma `debug.keystore` de forma autónoma en el runner, compila con C++20 y sube el APK Debug listo para descargar.

---

## 📦 Distribución

Aura Music está preparado para distribuirse libremente como APK independiente en tiendas como **Uptodown**, repositorios independientes o instalación manual directa en teléfonos Android.
