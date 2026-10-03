# Aura Music 🎵

**Aura Music** es un reproductor de música local de alta fidelidad para Android, diseñado con una estética moderna e inmersiva inspirada en Spotify, pero enriquecida con colores vibrantes, gradientes de neón, fondos oscuros OLED 100% opacos y procesamiento de audio avanzado.

Está construido con las tecnologías más modernas del ecosistema Android: **Jetpack Compose (Material 3)**, **Jetpack Media3 (ExoPlayer)**, **Room Persistence**, **Coroutines / StateFlow**, un **motor DSP nativo compilado en ISO C++20 con CMake**, almacenamiento estructurado de datos y una suite de diagnóstico autónoma (**Aura Monitor**).

---

## 🌟 Características Principales

### 1. Interfaz Visual, Temas y Animaciones Fluidas
- **Estética Dark Luxury OLED**: Superficies 100% opacas de alto contraste sin transparencias indeseadas ni filtraciones de fondo.
- **Transiciones y Animaciones del Sistema**:
  - Cambio entre pantallas con animación combinada de desvanecimiento y deslizamiento suave (`AnimatedContent`).
  - Despliegue elástico de la pantalla completa Now Playing desde el mini reproductor y **cierre suave con deslizamiento vertical instantáneo sin capas negras residuales**.
  - Indicadores y micro-interacciones táctiles con retroalimentación inmediata.
- **4 Paletas de Acentos Vibrantes**:
  - 🌌 **Nebula Violet**: Violeta eléctrico y cyan neón futurista.
  - 🍃 **Cyber Mint**: Esmeralda brillante y menta líquida.
  - 🔥 **Sunset Ember**: Coral cálido, naranja fuego y destellos dorados.
  - 🌊 **Ocean Abyss**: Azul zafiro profundo y agua bioluminiscente.
- **Mini Reproductor Flotante**:
  - Barra persistente con barra de progreso y controles táctiles.
  - **Acceso Directo al Ecualizador C++20 integrado** mediante hoja modal inferior sin abandonar la vista actual.
- **Pantalla Completa Now Playing**:
  - Visualizador de ondas animado en tiempo real.
  - Deslizador de búsqueda interactivo con formato de tiempo `mm:ss`.
  - Hoja de especificaciones de audio y modal para **editar metadatos y carátula**.

### 2. Ecualizador C++20 Integrado en Modal (Sin Apartados Aislados)
- **10 Bandas Paramétricas ISO**:
  - Frecuencias centrales: `31 Hz, 62 Hz, 125 Hz, 250 Hz, 500 Hz, 1 kHz, 2 kHz, 4 kHz, 8 kHz, 16 kHz`.
  - Filtros IIR Bi-cuadráticos (*Peaking Biquads*) en coma flotante de 64 bits con limitador suave anti-clipping.
  - Rango de ganancia de `-15 dB` a `+15 dB`.
- **Integración Total en Hoja Modal**: Ya no existe una pantalla separada que interrumpa la navegación; se abre como una pestaña directa en la hoja de efectos desde el Mini Reproductor o Now Playing.
- **Perfiles Acústicos (Presets)**: Rock, Pop, Electrónica, Jazz, Acústico, Bass Boost y Plano.
- **Refuerzo de Bajos C++ (Bass Boost)** calibrado a 60 Hz con modulación precisa.

### 3. Audio 8D Espacial y Controles Avanzados de Escucha
- **Audio Espacial 8D Binaural para Auriculares**:
  - Paneo orbital tridimensional continuo en tiempo real (4s a 30s por rotación completa).
  - Simulación acústica de sombra de cabeza (*Head Shadow Filtering*) y reverberación espacial ambiental.
- **Control Estable de Velocidad y Tono (Pitch & Speed)**:
  - Modulación fluida de 0.50x a 2.00x protegida con *throttling* y amortiguación de llamadas a ExoPlayer.
  - Recuperación automática ante anomalías de audio para evitar que la canción se pause accidentalmente.
- **Temporizador de Apagado (Sleep Timer)**:
  - Minutos personalizados o chips rápidos (15m, 30m, 45m, 60m).
  - Contador regresivo en tiempo real con opción de añadir +5 minutos.
  - **Atenuación suave de volumen de 10 segundos** (*fade-out*) antes de pausar.
- **Transición Suave (Crossfade) y Reproducción Gapless**:
  - Fundido cruzado de 0 a 12 segundos con desvanecimiento de volumen progresivo.
  - Modo Gapless para reproducción continua sin silencios entre pistas.

### 4. Video Canvas de Fondo (Loops Cortos de 10-20s o Videos Largos Sincronizados)
- **Lienzo Dinámico de Video**: Capacidad de vincular videos desde la galería del teléfono móvil a cualquier canción.
- **Detección Automática y Forzado Manual**:
  - **Loop Canvas (≤ 10s - 20s)**: Se repite en bucle infinito suave y continuo de fondo como en Spotify Canvas.
  - **Video Largo Sincronizado (> 20s)**: El video avanza sincronizado con la reproducción de la canción y los saltos de búsqueda (`seekTo`).
  - **Selector de Modo en Edición**: El usuario puede elegir entre detección automática (`≤ 20s`), forzar como Bucle (Loop) o forzar como Video Sincronizado.
- **Armonización Cromática Inteligente (Cero Interferencia con Carátulas)**:
  - Cuando el Video Canvas está en pantalla, el halo ambiental superior, el visualizador y la barra de progreso extraen su paleta cromática en tiempo real **directamente de un fotograma clave del video**.
  - De esta forma, el color de la carátula estática nunca interfiere ni desentona con el video.
  - Al alternar de vuelta a la carátula estática, la iluminación se adapta suavemente a los tonos de la portada.
- **Reproducción Silenciada de Alto Rendimiento**: El video se renderiza mediante una instancia secundaria de ExoPlayer optimizada con `volume = 0.0f` y liberación estricta de códecs, preservando íntegramente la señal de audio que alimenta el motor DSP C++20 de 10 bandas.
- **Conmutador Rápido en Now Playing**: Botón interactivo en la barra superior para alternar al instante entre la carátula clásica y el Video Canvas.
- **Gestión Limpia en Almacenamiento**: Los videos se copian de forma segura a la subcarpeta privada `videos/` y los videos anteriores se eliminan automáticamente para evitar acumulación de archivos huérfanos.

### 5. Video a Música (Extracción 3 en 1 Directa en el Teléfono) 🎬➡️🎵
- **Solución Nativa para Usuarios Móviles sin PC**: Permite seleccionar cualquier video de la galería (conciertos, clips de redes, TikToks, descargas) y transformarlo instantáneamente en una pista de música completa.
- **Flujo 3 en 1 Automático**:
  - 🎵 **Extracción de Audio Directa**: Demuxing sin recodificación (*Direct Stream Copy*) mediante `MediaExtractor` y `MediaMuxer` en Android a formato `.m4a` guardado en `songs/`, preservando la fidelidad acústica al 100% y ejecutándose en ~1 segundo.
  - 🖼️ **Captura Inteligente de Carátula**: Extrae un fotograma clave del video en alta definición (evitando pantallas negras de inicio) y lo comprime en WebP sin pérdida a `images/`.
  - 🎬 **Video Canvas Vinculado**: Vincula el video como Canvas de fondo sincronizado en `NowPlayingScreen`.
- **Reproducción Inmediata**: Al finalizar la conversión, la pista recién creada se reproduce de inmediato con su Video Canvas y atmósfera lumínica armonizada.

### 6. Carátulas Personalizadas de Galería & Arte Procedural
- **Selección de Carátula desde Galería**: Mediante el Android Photo Picker nativo del sistema.
- **Compresión WebP y Borrado Inteligente**: Conversión en segundo plano (`Dispatchers.IO`) a WebP sin pérdida y eliminación de carátulas residuales del disco.
- **Generador de Arte Procedural**: Ilustración matemática vectorial única para canciones sin portada.

### 7. Playlists y Almacenamiento Estructurado
- **Pestaña "Playlists" en Tu Biblioteca**: Tarjeta "Tus Me Gusta" sincronizada, creación, renombrado y adición rápida de canciones.
- **Almacenamiento Organizado** en `Android/data/com.aistudio.musicplayer.aurasound/files/`:
  - 📁 `images/`: Carátulas en WebP Lossless.
  - 📁 `songs/`: Canciones locales y demos.
  - 📁 `lyrics/`: Archivos de letras sincronizadas (`.lrc`).
  - 📁 `metadata/`: Ficheros JSON estructurados con información técnica.
  - 📁 `videos/`: Videos de fondo y loops de Canvas (.mp4/.webm).

### 8. Suite de Diagnóstico Autónoma: Aura Monitor 🛠️ & LeakCanary
- **Aura Monitor (App Debug Propia en el Cajón de Aplicaciones)**:
  - Cuenta con su propio icono de acceso directo en el cajón de apps del teléfono móvil.
  - Atrapa y registra automáticamente **Crashes** no controlados mediante `UncaughtExceptionHandler`, **Errores Críticos**, **Warnings de Memoria** y eventos de Media3 / JNI.
  - Registra datos técnicos del teléfono móvil: Modelo, Fabricante, Versión de Android / SDK API, CPU ABI (64-bit / 32-bit), memoria RAM libre/total y espacio de disco disponible.
  - Visualizador de **Stack Trace en crudo** completo con copia rápida al portapapeles y generación de informe diagnóstico integral para compartir sin necesidad de PC.
- **LeakCanary**: Integrado en el entorno de desarrollo para auditoría y detección en tiempo real de fugas de memoria en la JVM.

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
