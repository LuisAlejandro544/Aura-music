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
- **5 Paletas de Acentos Vibrantes & Material You**:
  - 🎨 **Material You**: Colores dinámicos sincronizados con el fondo de pantalla del sistema operativo (Android 12+ / Material 3) manteniendo el fondo oscuro OLED.
  - 🌌 **Nebula Violet**: Violeta eléctrico y cyan neón futurista.
  - 🍃 **Cyber Mint**: Esmeralda brillante y menta líquida.
  - 🔥 **Sunset Ember**: Coral cálido, naranja fuego y destellos dorados.
  - 🌊 **Ocean Abyss**: Azul zafiro profundo y agua bioluminiscente.
- **Protección Tipográfica Fija (Cero Desbordamientos)**:
  - Escala de densidad y fuente estabilizada (`fontScale = 1.0f`) para que las configuraciones globales de tamaño de letra en Android no rompan la maquetación ni corten textos.
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

### 4. Video de Fondo Multifuncional (Fondo Completo, Lienzo en Carátula o Desactivado)
- **3 Modos de Visualización Seleccionables por el Usuario**:
  - 🌌 **Fondo Completo (Full Background)**: El video se reproduce ocupando todo el fondo de pantalla de Now Playing detrás de la interfaz gráfica con un velo oscuro/gradiente para máxima legibilidad, mientras la carátula flota al frente con su aura lumínica, elevación y sombra.
  - 🔲 **Lienzo en Carátula (Card Canvas)**: El video se reproduce dentro del marco central de la carátula (relación de aspecto 1:1 estilo Spotify Canvas).
  - 🖼️ **Solo Carátula**: Muestra únicamente la carátula estática o procedural sin video.
- **Selector Modal Interactivo**: El usuario puede abrir un cuadro de diálogo modal desde el icono de video en la barra superior o alternar con un solo toque desde la etiqueta en la carátula.
- **Detección Automática y Forzado Manual**:
  - **Loop Canvas (≤ 10s - 20s)**: Bucle infinito continuo silenciado.
  - **Video Largo Sincronizado (> 20s)**: Sincronizado con la reproducción y los saltos temporales (`seekTo`).
  - **Selector en Edición**: Opción de forzar bucle o sincronización desde `EditTrackDialog`.
- **Armonización Cromática Inteligente**: Extracción en tiempo real del halo de luz y visualizador a partir de fotogramas del video para armonizar la interfaz.
- **Sincronización Instantánea de Favoritos**: El botón de corazón en Now Playing y en la Biblioteca refleja reactivamente el estado en tiempo real (icono relleno en rojo `Color(0xFFEF4444)` al marcar favorito).
- **Lista de Inicio Estable**: La sección "Populares en tu biblioteca" permanece fija y nunca elimina otras canciones al marcar un favorito.

### 5. Video a Música (Extracción 3 en 1 Directa en el Teléfono) 🎬➡️🎵
- **Solución Nativa para Usuarios Móviles sin PC**: Permite seleccionar cualquier video de la galería (conciertos, clips de redes, TikToks, descargas) y transformarlo instantáneamente en una pista de música completa.
- **Flujo 3 en 1 Automático**:
  - 🎵 **Extracción de Audio Directa**: Demuxing sin recodificación (*Direct Stream Copy*) mediante `MediaExtractor` y `MediaMuxer` en Android a formato `.m4a` guardado en `songs/`, preservando la fidelidad acústica al 100% y ejecutándose en ~1 segundo.
  - 🖼️ **Captura Inteligente de Carátula**: Extrae un fotograma clave del video en alta definición (evitando pantallas negras de inicio) y lo comprime en WebP sin pérdida a `images/`.
  - 🎬 **Video Canvas Vinculado**: Vincula el video como Canvas de fondo sincronizado en `NowPlayingScreen`.
- **Reproducción Inmediata**: Al finalizar la conversión, la pista recién creada se reproduce de inmediato con su Video Canvas y atmósfera lumínica armonizada.

### 6. Descarga Directa desde TikTok y Enlaces Web (Música, Carátula y Video Canvas) 🎬🔗🎵
- **Descargas sin Límite de Duración**: Permite pegar cualquier enlace de video de TikTok (o URL de video web) para descargar música completa, parodias, versiones especiales o directos de cualquier duración.
- **Extracción Automática sin Marcas de Agua**:
  - 🎵 **Audio de Alta Fidelidad**: Extrae la pista de audio pura en formato `.m4a` o `.mp3` directamente a `songs/`.
  - 🖼️ **Carátula Oficial en WebP**: Descarga la portada en alta resolución (o extrae fotograma clave) y la procesa a WebP sin pérdida en `images/`.
  - 🎬 **Video Canvas Vinculado**: Almacena el video en `videos/` para reproducirlo de fondo continuo o sincronizado en *Now Playing*.
- **Previsualización y Edición Rápida**: Muestra título, autor/creador, duración y portada antes de confirmar, permitiendo ajustar los nombres antes de guardar.
- **Reproducción al Instante**: Una vez descargada, inicia la reproducción automáticamente abriendo Now Playing.

### 7. Carátulas Personalizadas de Galería & Arte Procedural
- **Selección de Carátula desde Galería**: Mediante el Android Photo Picker nativo del sistema.
- **Compresión WebP y Borrado Inteligente**: Conversión en segundo plano (`Dispatchers.IO`) a WebP sin pérdida y eliminación de carátulas residuales del disco.
- **Generador de Arte Procedural**: Ilustración matemática vectorial única para canciones sin portada.

### 8. Suite Acústica y Ajustes para Auriculares / Audífonos 🎧
- **Filtro Crossfeed Acústico (Algoritmo Bauer / Chu Moy en ISO C++20)**:
  - Elimina la fatiga auditiva mezclando sutilmente una porción de audio con filtro paso-bajos (~700 Hz) y retardo temporal interaural (ITD de 250 µs a 340 µs) hacia el oído opuesto, emulando la escucha natural de monitores de estudio en sala.
  - **Activación Exclusiva por Hardware**: Solo se aplica en la música cuando el sistema detecta que hay auriculares conectados (jack 3.5mm, USB-C DAC o Bluetooth), pausándose automáticamente en los altavoces del teléfono.
  - 3 niveles acústicos calibrados: *Sutil (Bauer 4.5 dB)*, *Moderado (Chu Moy Classic)* e *Intenso (Monitores de Estudio)*.
- **Balance Estéreo Fino (Control Izquierda / Derecha L/R en Tiempo Real)**:
  - Ajuste de ganancia L/R de precisión con cálculo de paneo suave en C++20 y limitador anti-clipping.
  - **Ajuste en Tiempo Real en Pantalla Completa**: Barra de balance accesible directamente en *Now Playing* bajo los controles de reproducción y en la hoja modal de efectos, con porcentaje en vivo y botón para centrar (`0%`).
- **Protección contra Desconexiones ("Becoming Noisy" Guard)**:
  - Pausa instantáneamente la música al desconectar los audífonos por cable o si se apaga la batería de auriculares Bluetooth, evitando que la música suene a todo volumen en altavoz en público.
- **Reanudación con Fade-In Suave (Volumen Progresivo)**:
  - Rampa suave de volumen de ~1 segundo al reanudar la reproducción con audífonos puestos para proteger los tímpanos de picos repentinos.
- **Memoria de Volumen Dedicada para Audífonos**:
  - Recuerda de forma independiente el volumen de audífonos y el volumen del altavoz del teléfono al conectar y desconectar.
- **Mapeo Avanzado de Botones y Gestos Físicos de Auriculares (Headset Controls)**:
  - Totalmente configurable por el usuario mediante diálogo modal para 1 pulsación, 2 pulsaciones, 3 pulsaciones y pulsación prolongada (Play/Pausa, Siguiente, Anterior, Me Gusta ❤️, Avanzar 15s, Retroceder 15s).

### 9. Notificación Nativa del Reproductor de Android & Segundo Plano 🔔
- **Controlador Multimedia Nativo de Android (System Media Controls)**:
  - Integración completa con **Jetpack Media3 `MediaSessionService`** y `MediaSession`.
  - **Android 13, 14, 15+**: Tarjeta multimedia nativa en la cortina de notificaciones con arte de tapa en alta resolución, colores adaptativos dinámicos y **línea ondulada interactiva (*squiggled seekbar*)** para avanzar o retroceder sin abrir la app.
  - **Android 11 y 12**: Controles multimedia integrados en el panel de Ajustes Rápidos (*Quick Settings*).
  - **Android 8.0 Oreo, 9 Pie y 10**: Notificación de estilo multimedia retrocompatible (`MediaStyle`) con botones de reproducción y carátula.
- **Canal de Notificación Silencioso**: Configurado con `IMPORTANCE_LOW` para cambiar de pista sin emitir timbres o alertas intrusivas.
- **Reproducción Continua en Segundo Plano (*Foreground Service*)**: Mantiene la música sonando ininterrumpidamente cuando la pantalla está apagada o la aplicación se minimiza.
- **Soporte Extendido**: Detección automática en **relojes inteligentes (Wear OS)**, **Android Auto** y mandos remotos Bluetooth.

### 10. Playlists y Almacenamiento Estructurado
- **Pestaña "Playlists" en Tu Biblioteca**: Tarjeta "Tus Me Gusta" sincronizada, creación, renombrado y adición rápida de canciones.
- **Almacenamiento Organizado** en `Android/data/com.aistudio.musicplayer.aurasound/files/`:
  - 📁 `images/`: Carátulas en WebP Lossless.
  - 📁 `songs/`: Canciones locales y demos.
  - 📁 `lyrics/`: Archivos de letras sincronizadas (`.lrc`).
  - 📁 `metadata/`: Ficheros JSON estructurados con información técnica.
  - 📁 `videos/`: Videos de fondo y loops de Canvas (.mp4/.webm).

### 11. Suite de Diagnóstico Autónoma: Aura Monitor 🛠️ & LeakCanary
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
