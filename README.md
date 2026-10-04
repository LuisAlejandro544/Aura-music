# Aura Music 🎵

**Aura Music** es un reproductor de música local de alta fidelidad para Android, diseñado con una estética original Dark Luxury Neo-Glass, enriquecida con colores vibrantes, gradientes de neón, fondos oscuros OLED 100% opacos, Video Canvas audiovisual dinámico y procesamiento de audio profesional avanzado.

Está construido con las tecnologías más modernas del ecosistema Android: **Jetpack Compose (Material 3)**, **Jetpack Media3 (ExoPlayer)**, **Room Persistence**, **Coroutines / StateFlow**, un **motor DSP nativo compilado en ISO C++20 con CMake**, almacenamiento estructurado de datos y una suite de diagnóstico autónoma (**Aura Monitor**).

---

## 🌟 Características Principales

### 1. Interfaz Visual, Temas y Animaciones Fluidas
- **Estética Dark Luxury Neo-Glass OLED**: Superficies 100% opacas de alto contraste sin transparencias indeseadas ni filtraciones de fondo, con halos luminosos reactivos al compás de la música.
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
- **Protección Tipográfica Fija y Marquesina Automática (Cero Desbordamientos)**:
  - Escala de densidad y fuente estabilizada (`fontScale = 1.0f`) para que las configuraciones globales de tamaño de letra en Android no rompan la maquetación ni corten textos.
  - **Títulos en Movimiento (Marquee Fluido)**: Los títulos y artistas largos se desplazan horizontalmente de forma continua (`basicMarquee`) en la pantalla completa *Now Playing*, en el *Mini Reproductor* y en la canción en reproducción dentro de las listas y la cola.
- **Mini Reproductor Flotante con Video Canvas Miniatura**:
  - Barra persistente con barra de progreso, controles táctiles y títulos animados en marquesina.
  - **Soporte de Video Canvas en Miniatura**: El usuario puede configurar en Ajustes si desea visualizar el video en movimiento también en la carátula pequeña del mini reproductor.
  - **Acceso Directo al Ecualizador C++20 integrado** mediante hoja modal inferior sin abandonar la vista actual.
- **Pantalla Completa Now Playing**:
  - Cabecera superior minimalista y despejada (sin textos redundantes que ocupen espacio).
  - Visualizador de ondas animado en tiempo real.
  - Deslizador de búsqueda interactivo con formato de tiempo `mm:ss` y **barra compacta del Repetidor de Segmento A-B**.
  - Hoja de especificaciones de audio y modal para **editar metadatos, carátula y video**.

### 2. Ecualizador C++20 Integrado en Modal (Sin Apartados Aislados)
- **10 Bandas Paramétricas ISO con Nombres Intuitivos**:
  - Cada perilla incluye su descriptor acústico en lenguaje claro junto a los hercios:
    - `31 Hz`: **Subgraves** (vibración sísmica y bajos sub-graves).
    - `62 Hz`: **Bajos** (graves contundentes y bombo).
    - `125 Hz`: **Graves** (calidez y bajo melódico).
    - `250 Hz`: **Cuerpo** (resonancia acústica de instrumentos).
    - `500 Hz`: **Medios Bajos** (peso instrumental).
    - `1 kHz`: **Voces** (presencia vocal principal).
    - `2 kHz`: **Claridad** (definición y articulación de voz).
    - `4 kHz`: **Presencia** (ataque de percusión y guitarras).
    - `8 kHz`: **Brillo** (platillos y detalle agudo).
    - `16 kHz`: **Aire / Agudos** (apertura Hi-Fi y espacialidad).
  - Filtros IIR Bi-cuadráticos (*Peaking Biquads*) en coma flotante de 64 bits con limitador suave anti-clipping.
  - Rango de ganancia de `-15 dB` a `+15 dB`.
- **Integración Total en Hoja Modal**: Ya no existe una pantalla separada que interrumpa la navegación; se abre como una pestaña directa en la hoja de efectos desde el Mini Reproductor o Now Playing.
- **Perfiles Acústicos (Presets)**: Rock, Pop, Electrónica, Jazz, Acústico, Bass Boost y Plano.
- **Refuerzo de Bajos C++ (Bass Boost)** calibrado a 60 Hz con modulación precisa.

### 3. Audio 8D Espacial, Repetidor A-B, Eliminación Inteligente de Silencios y Controles Avanzados
- **Eliminación Inteligente de Silencios al Inicio y Final (`AudioSilenceTrimmer`)**:
  - Antes de cada importación (archivos locales, carpetas, conversión *Video a Música*, descargas desde *TikTok/YouTube* o recepción vía *Abrir con... / Compartir con...*), la app presenta un interruptor interactivo para activar o desactivar el recorte inteligente de silencios.
  - Analiza las muestras PCM de los primeros y últimos 25 segundos calculando la energía RMS por ventanas (umbral `-42 dB`) para detectar dónde empieza y termina realmente la música.
  - Ejecuta recorte físico sin pérdida (*Direct Stream Copy*) en contenedores `.m4a`/`.mp4` o recorte exacto en `.wav`, además de persistir límites de recorte no destructivos (`MediaItem.ClippingConfiguration`) para cualquier formato.
- **Repetidor de Segmento A-B (A-B Loop)**:
  - **Barra Compacta en Now Playing**: Botones rápidos `[A]`, `[B]` y limpiar (`×`) situados junto al indicador de progreso, con franja luminosa sobre el deslizador que demarca el fragmento activo.
  - **Panel Detallado en Efectos de Audio**: Ajuste fino de ±1 segundo para los puntos A y B (`A -1s`, `A +1s`, `B -1s`, `B +1s`), interruptor de activación y reinicio automático al cambiar de pista.
- **Suite Reverb Híbrida & Filtros Acústicos Ambientales (100% C++20 en Tiempo Real, Cero Silencios)**:
  - Simulación de espacios físicos reales mediante presets de alta fidelidad: *Estudio*, *Sala Mediana*, *Club En Vivo*, *Gran Hall / Teatro*, *Catedral* y *Eco Espacial*.
  - **Motor Acústico Blindado en C++20 Libre de Bloqueo LVREV**: Desacoplado del efecto auxiliar `EnvironmentalReverb`/`PresetReverb` de Android que silenciaba la señal directa (*dry*) y retenía el canal durante segundos al apagarse. Todo el procesamiento se ejecuta en tiempo real dentro de `ReverbProcessor` (C++20) con filtros peine amortiguados (*LBCF*), filtros pasa-todo Schroeder, soporte mono/estéreo y reinicio atómico sin condiciones de carrera.
  - Personalización acústica libre para ajuste milimétrico:
    - *Tamaño de Sala / Espacio* (0.1x a 2.0x).
    - *Tiempo de Decaimiento / Resonancia* (100 ms a 6000 ms).
    - *Nivel de Reverberación / Mezcla Húmeda* (-30 dB a +6 dB).
- **Audio Espacial 8D Binaural para Auriculares**:
  - Paneo orbital tridimensional continuo en tiempo real (4s a 30s por rotación completa).
  - Simulación acústica de sombra de cabeza (*Head Shadow Filtering*) y reverberación espacial ambiental.
- **Control Estable de Velocidad de Música y Velocidad de Voz / Tono hasta 2.0x (Pitch & Speed)**:
  - Modulación fluida de **0.50x a 2.00x** tanto en **Velocidad de la Música** como en **Velocidad de Voz / Tono (Pitch Shift)**, con botones rápidos de un toque (`0.8x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`) en ambos controles.
  - Protegida con *throttling* y recuperación automática ante anomalías de audio para evitar que la canción se pause accidentalmente.
- **Temporizador de Apagado (Sleep Timer)**:
  - Minutos personalizados o chips rápidos (15m, 30m, 45m, 60m).
  - Contador regresivo en tiempo real con opción de añadir +5 minutos.
  - **Atenuación suave de volumen de 10 segundos** (*fade-out*) antes de pausar.
- **Reproducción Continua Automática y Transición Suave (Crossfade / Fade-In)**:
  - Al terminar cualquier canción, avanza y reproduce automáticamente la siguiente pista de la cola o biblioteca de forma ininterrumpida.
  - Fundido de salida progresivo al acercarse al final de la pista y rampa de entrada suave (*fade-in*) calibrada al iniciar la siguiente canción, subiendo poco a poco hasta restaurar el 100% del volumen original sin quedarse atrapado en volumen bajo.
  - Modo Gapless para reproducción continua sin silencios intermedios.

### 4. Video Canvas Multifuncional, Sincronización de Velocidad y Reacción Cromática Instantánea 🎬⚡
- **Respiración y Pulsación Acústica Ligada a C++20 DSP**:
  - Medición RMS y envolvente espectral continua en tiempo real calculada dentro del motor nativo en C++20 (`getAudioIntensity()` y `getVisualizerBands()`).
  - **Carátula Central y Video Canvas 100% Despejados (Cero Insignias Invasivas)**: Se eliminó cualquier cápsula o indicador superpuesto ("VIDEO SYNC" / "LOOP CANVAS") para una apreciación visual prístina, gestionándose todo mediante el botón selector de la barra superior.
  - Halo lumínico ambiental superior y visualizador de 28 bandas con degradado vertical fluido modulando brillo y color en tiempo real según el Video Canvas.
- **Armonización Cromática Instantánea en Video Canvas**:
  - Muestreo dinámico continuo de alta frecuencia (cada ~300ms) de fotogramas del video con decodificación ultra-ligera en memoria y transición ágil (`tween(220)`), eliminando el retraso previo y sincronizando la atmósfera lumínica de forma inmediata con los cambios de escena del video.
- **Sincronización Total de Velocidad de Video y Música (0.50x a 2.00x)**:
  - Al modular la velocidad de la música o cambiar de presets rápidos, el reproductor de Video Canvas adapta en tiempo real su velocidad de reproducción para marchar al unísono exacto con el tempo musical.
- **Mini Reproductor Tintado Dinámicamente con Soporte de Video Canvas**:
  - Superficie con fondo tintado y degradado armónico extraído de la pista o fotograma activo.
  - **Soporte de Video Canvas en Miniatura**: Configurable por el usuario para alternar entre carátula estática y reproducción de Video Canvas en la miniatura de 46dp.
  - Botonera ergonómica estándar: **Canción Anterior** (⏮️), **Play/Pausa** (⏯️) y **Siguiente** (⏭️).
- **Colas de Reproducción Contextuales Fieles**:
  - Al reproducir desde una Playlist, Álbum o Artista, la cola activa (`queue`) se adapta estrictamente a las pistas de esa lista, permitiendo navegar ordenadamente dentro de ese contenido.
- **Atajos Inferiores Espaciosos y Equilibrados**:
  - Botonera inferior de Now Playing distribuida uniformemente en 3 módulos compactos (`EQ FX`, `Letras`/`Carátula` y `Cola`), con área táctil superior a 48dp y texto protegido sin desbordamientos ni saltos verticales.
- **3 Modos de Visualización Seleccionables por el Usuario**:
  - 🌌 **Fondo Completo (Full Background)**: El video se reproduce ocupando todo el fondo de pantalla de Now Playing detrás de la interfaz gráfica con un velo oscuro/gradiente para máxima legibilidad, mientras la carátula flota al frente con su aura lumínica y sombra.
  - 🔲 **Lienzo en Carátula (Card Canvas)**: El video se reproduce dentro del marco central de la carátula (relación de aspecto 1:1 estilo marco cinemático).
  - 🖼️ **Solo Carátula**: Muestra únicamente la carátula estática o procedural sin video.
- **Detección Automática y Forzado Manual**:
  - **Loop Canvas (≤ 10s - 20s)**: Bucle infinito continuo silenciado.
  - **Video Largo Sincronizado (> 20s)**: Sincronizado con la reproducción y los saltos temporales (`seekTo`).
  - **Selector en Edición y Barra Superior**: Accesible cómodamente desde `NowPlayingTopBar` y `EditTrackDialog`.
- **Sincronización Instantánea de Favoritos**: El botón de corazón en Now Playing y en la Biblioteca refleja reactivamente el estado en tiempo real (icono relleno en rojo `Color(0xFFEF4444)` al marcar favorito).
- **Lista de Inicio Estable**: La sección "Populares en tu biblioteca" permanece fija y nunca elimina otras canciones al marcar un favorito.

### 5. Letras Sincronizadas (.LRC y .TXT) Estilo Karaoke con Importador Local y Descarga Automática 🎤📜
- **Descarga Automática 100% Gratuita**: Al iniciar la reproducción de cualquier canción sin letra, consulta automáticamente el servicio público libre LRCLIB mediante metadatos y búsqueda inteligente (sanitizando sufijos como *official*, *video*, *remastered* o *feat.*) sin requerir registro ni API keys.
- **Importador de Archivos de Letras (.LRC y .TXT) desde el Celular**: Botón directo en la barra superior y en el estado vacío de la tarjeta de Karaoke para importar archivos de letras descargados en el teléfono mediante el selector de documentos nativo. Soporta tanto letras sincronizadas `.lrc` como letras en texto plano `.txt`.
- **Detección y Vinculación Automática al Importar Música**: Al importar canciones desde el almacenamiento (SAF, carpetas o 'Abrir con...'), detecta automáticamente archivos hermanos `.lrc` o `.txt` con el mismo nombre en la carpeta o metadatos incrustados, asignando la letra al instante sin requerir internet.
- **Persistencia en Almacenamiento Estructurado**: Almacena las letras en archivos `.lrc` locales organizados en `Android/data/.../files/lyrics/track_{id}.lrc` para consulta sin conexión a internet de por vida.
- **Experiencia Karaoke Fluida**:
  - Resaltado lumínico neón con tipografía ampliada (`19.sp`, negrita extra) para la frase que se está cantando.
  - Desplazamiento automático (*auto-scroll*) continuo y suave para mantener la frase activa centrada en pantalla.
  - Atenuación de frases anteriores y estilo neutro para los versos venideros.
  - Salto táctil instantáneo (*Seek-to-time*): Tocar cualquier verso rebobina o avanza la canción exactamente a esa marca de tiempo `[mm:ss.xx]`.
  - Visualizador de letras planas `.txt` con desplazamiento vertical continuo y tipografía cómoda.
- **Edición y Carga Manual**: Diálogo modal para ingresar o pegar letras `.lrc` personalizadas o texto plano en cualquier momento.
- **Acceso Rápido**: Botón de micrófono en la barra superior de Now Playing y botón alternador en los atajos inferiores para alternar entre carátula/canvas y vista karaoke con un solo toque.

### 6. Video a Música (Extracción 3 en 1 Directa en el Teléfono) 🎬➡️🎵
- **Solución Nativa para Usuarios Móviles sin PC**: Permite seleccionar cualquier video de la galería (conciertos, clips de redes, TikToks, descargas) y transformarlo instantáneamente en una pista de música completa.
- **Flujo 3 en 1 Automático**:
  - 🎵 **Extracción de Audio Directa**: Demuxing sin recodificación (*Direct Stream Copy*) mediante `MediaExtractor` y `MediaMuxer` en Android a formato `.m4a` guardado en `songs/`, preservando la fidelidad acústica al 100% y ejecutándose en ~1 segundo.
  - 🖼️ **Captura Inteligente de Carátula**: Extrae un fotograma clave del video en alta definición (evitando pantallas negras de inicio) y lo comprime en WebP sin pérdida a `images/`.
  - 🎬 **Video Canvas Vinculado**: Vincula el video como Canvas de fondo sincronizado en `NowPlayingScreen`.
- **Reproducción Inmediata**: Al finalizar la conversión, la pista recién creada se reproduce de inmediato con su Video Canvas y atmósfera lumínica armonizada.

### 7. Descarga Directa desde TikTok, YouTube y Enlaces Web (Música, Carátula Oficial y Video Canvas) 🎬🔗🎵
- **Descargas sin Límite de Duración**: Permite pegar enlaces de **TikTok**, **YouTube** y videos web para descargar música completa, directos, sesiones o parodias de cualquier duración.
- **Arquitectura de Extracción Resiliente de 3 Niveles con Carátula Garantizada**:
  - ⚡ **Motor InnerTube Nativo (`InnerTubeClient`)**: Consulta directa ultrarrápida al endpoint oficial de YouTube mediante clientes de baja fricción (`ANDROID_VR` y `VISIONOS`). Entrega flujos de audio y video directos sin cifrado de firma (`n-sig`) ni bloqueos de `LOGIN_REQUIRED` en menos de ~300ms.
  - 🛡️ **Bypass de Respaldo Invidious (`InvidiousStreamResolver`)**: Para pistas con restricciones estrictas de derechos de autor (VEVO, discográficas) que exigen inicio de sesión en clientes anónimos, consulta en milisegundos instancias públicas de alta disponibilidad que descifran los enlaces directos a `googlevideo.com`.
  - 🌐 **Motor Headless WebView Reparado con Garantía de Carátula (`HeadlessWebViewExtractor`)**: Navegador efímero en segundo plano cargado sobre `m.youtube.com` (evitando el error 150) con timeout de 22s, extracción de miniaturas oficiales en el DOM y **descarga en cascada resiliente** (`maxresdefault.jpg` -> `hqdefault.jpg` -> `mqdefault.jpg` -> `i.ytimg.com` -> fotograma clave de video). ¡Garantiza que ningún video descargado se quede jamás sin carátula!
  - 🎛️ **Selector Interactivo en el Diálogo**: El usuario puede alternar entre ambos motores en el diálogo de descarga con auto-fallback cruzado de 3 capas.
- **Extracción Automática 3 en 1**:
  - 🎵 **Audio de Alta Fidelidad**: Extrae la pista de audio pura en formato `.m4a` o `.mp3` directamente a `songs/`.
  - 🖼️ **Carátula Oficial en WebP**: Descarga la portada oficial en alta resolución y la procesa a WebP sin pérdida en `images/`.
  - 🎬 **Video Canvas Vinculado**: Almacena el video en `videos/` para reproducirlo de fondo continuo o sincronizado en *Now Playing*.
- **Previsualización y Edición Rápida**: Muestra título, autor/creador, duración y portada antes de confirmar con temas visuales adaptativos (Cyan/Magenta para TikTok, Rojo Carmesí/Naranja para YouTube/Web).
- **Reproducción al Instante**: Una vez descargada, inicia la reproducción automáticamente abriendo Now Playing.

### 8. Integración Inteligente "Abrir con..." y "Compartir con..." (Puente con Gestores de Descarga y Apps Externas) 🔗📲
- **Detección Automática y Triaje Inteligente (`IncomingMediaHandler`)**:
  - Al abrir o compartir cualquier contenido hacia Aura Music desde aplicaciones como SnapTube, NewPipe, Seal, Telegram, WhatsApp, gestores de archivos o navegadores web, el sistema clasifica de forma inmediata y certera la naturaleza del medio:
    - 🎵 **Archivo de Audio (`audio/*`, `.mp3`, `.m4a`, `.flac`, `.wav`, `.ogg`, `.opus`, etc.)**:
      - Extrae los metadatos ID3 y la portada con `AudioMetadataParser`.
      - Realiza una copia segura hacia el almacenamiento estructurado `songs/` para garantizar que la canción persista localmente de por vida, incluso si la app externa revoca permisos o borra su caché temporal.
      - La registra reactivamente en Room Database y en `metadata/`.
      - Inicia la reproducción de inmediato (`playTrack`), despliega *Now Playing* y consulta letras automáticamente en LRCLIB.
    - 🎬 **Archivo de Video (`video/*`, `.mp4`, `.mkv`, `.webm`, `.3gp`)**:
      - Identifica pistas de video reales mediante inspección profunda con `MediaMetadataRetriever` (diferenciando contenedores de solo audio de videos reales).
      - Despliega automáticamente el diálogo **Video a Música**, permitiendo previsualizar el fotograma clave, personalizar título/artista y convertirlo en 1 segundo a música `.m4a` con carátula WebP y Video Canvas sincronizado.
    - 🌐 **Enlace Web o Texto Compartido (`text/plain`, URLs `http`/`https`)**:
      - Reconoce enlaces provenientes de TikTok, YouTube, redes o navegadores.
      - Abre directamente el diálogo de descarga de enlaces con la URL pre-cargada e inicia la resolución automática del título, creador y carátula.
    - 🎶 **Múltiples Audios Compartidos**:
      - Importa el lote completo a la biblioteca y comienza la reproducción continua del primer elemento en cola.
- **Soporte `singleTop` e Intent Filters Completos**:
  - Tanto si Aura Music está cerrada (arranque en frío) como si ya se encuentra en segundo plano reproduciendo música, `onNewIntent` gestiona la nueva solicitud sin interrumpir la interfaz ni recrear la pila de Compose.

### 9. Carátulas Personalizadas de Galería & Arte Procedural
- **Selección de Carátula desde Galería**: Mediante el Android Photo Picker nativo del sistema.
- **Compresión WebP y Borrado Inteligente**: Conversión en segundo plano (`Dispatchers.IO`) a WebP sin pérdida y eliminación de carátulas residuales del disco.
- **Generador de Arte Procedural**: Ilustración matemática vectorial única para canciones sin portada.

### 10. Suite Acústica y Ajustes para Auriculares / Audífonos 🎧
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

### 11. Notificación Nativa del Reproductor de Android & Segundo Plano 🔔
- **Controlador Multimedia Nativo de Android (System Media Controls)**:
  - Integración completa con **Jetpack Media3 `MediaSessionService`**, `MediaSession` y `ForwardingPlayer`.
  - **Android 13, 14, 15+**: Tarjeta multimedia simétrica y completa en la cortina de notificaciones con arte de tapa en alta resolución, colores adaptativos dinámicos, **línea ondulada interactiva (*squiggled seekbar*)** y botonera completa con **Anterior (|<<), Play/Pausa (||) y Siguiente (>>|)** siempre disponibles.
  - **Android 11 y 12**: Controles multimedia integrados en el panel de Ajustes Rápidos (*Quick Settings*).
  - **Android 8.0 Oreo, 9 Pie y 10**: Notificación de estilo multimedia retrocompatible (`MediaStyle`) con botones de reproducción y carátula.
- **Canal de Notificación Silencioso**: Configurado con `IMPORTANCE_LOW` para cambiar de pista sin emitir timbres o alertas intrusivas.
- **Reproducción Continua en Segundo Plano (*Foreground Service*)**: Mantiene la música sonando ininterrumpidamente cuando la pantalla está apagada o la aplicación se minimiza.
- **Soporte Extendido**: Detección automática en **relojes inteligentes (Wear OS)**, **Android Auto** y mandos remotos Bluetooth.

### 12. Playlists y Almacenamiento Estructurado
- **Pestaña "Playlists" en Tu Biblioteca**: Tarjeta "Tus Me Gusta" sincronizada, creación, renombrado y adición rápida de canciones.
- **Almacenamiento Organizado** en `Android/data/com.aistudio.musicplayer.aurasound/files/`:
  - 📁 `images/`: Carátulas en WebP Lossless.
  - 📁 `songs/`: Canciones locales y demos.
  - 📁 `lyrics/`: Archivos de letras sincronizadas (`.lrc`).
  - 📁 `metadata/`: Ficheros JSON estructurados con información técnica.
  - 📁 `videos/`: Videos de fondo y loops de Canvas (.mp4/.webm).

### 13. Suite de Diagnóstico Autónoma: Aura Monitor 🛠️ & LeakCanary
- **Aura Monitor (App Debug Propia con Navegación Modular por Pestañas)**:
  - Cuenta con su propio icono independiente en el cajón de aplicaciones del teléfono móvil (tarea aislada con `taskAffinity` y `singleTask`) y también es accesible desde **Ajustes > Arquitectura y Privacidad > Abrir Aura Monitor**.
  - 📋 **Pestaña 1: Incidentes & Logs**:
    - Atrapa y registra automáticamente **Crashes** no controlados mediante `UncaughtExceptionHandler`, **Errores Críticos**, **Warnings de Memoria** y eventos de Media3 / JNI.
    - Registra datos técnicos del teléfono móvil: Modelo, Fabricante, Versión de Android / SDK API, CPU ABI (64-bit / 32-bit), memoria RAM libre/total y espacio de disco disponible.
    - Visualizador de **Stack Trace en crudo** completo con copia rápida al portapapeles y generación de informe diagnóstico integral para compartir sin necesidad de PC.
  - ⚡ **Pestaña 2: Rendimiento, RAM Detallada e Inspector de Hilos (En Vivo)**:
    - **Desglose de Memoria RAM Segmentado**: Visualización gráfica y numérica en vivo de PSS Total, Java Heap (VM), Native Heap (C++20 DSP y CMake), Gráficos/Shaders (Compose y Coil WebP) y Código compilado (.dex y binarios nativos .so).
    - **Telemetría de Procesador**: Carga de CPU del proceso en tiempo real (%), número de núcleos activos, estado térmico del hardware (Throttling) y tasa estimada de FPS.
    - **Inspector Quirúrgico de Hilos (Thread Profiler)**: Enumeración en vivo de todos los hilos del proceso (`main`, `ExoPlayer:Playback`, `DefaultDispatcher-worker`, `AudioTrack`, `RenderThread`, etc.) con clasificación por categoría, estado coloreado (`RUNNABLE`, `TIMED_WAITING`, `BLOCKED`), búsqueda instantánea, traza de pila completa expandible y copia rápida al portapapeles.
- **LeakCanary & Blindaje contra Fugas de Memoria**:
  - Integrado en el entorno de depuración para auditoría y detección en tiempo real de fugas de memoria en la JVM.
  - **Fuga de `ResourcesImpl.mAppContext` en `AuraMediaPlaybackService` Erradicada**: Corrección de la retención del servicio de reproducción multimedia mediante el uso exclusivo de `applicationContext` en constructores de proveedores de notificación y neutralización por reflexión del campo estático del framework de Android al destruirse el servicio.

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
