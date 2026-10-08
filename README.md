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
- **5 Paletas de Acentos Vibrantes & Material You (Con Persistencia Permanente)**:
  - 🎨 **Material You**: Colores dinámicos sincronizados con el fondo de pantalla del sistema operativo (Android 12+ / Material 3) manteniendo el fondo oscuro OLED.
  - 🌌 **Nebula Violet**: Violeta eléctrico y cyan neón futurista.
  - 🍃 **Cyber Mint**: Esmeralda brillante y menta líquida.
  - 🔥 **Sunset Ember**: Coral cálido, naranja fuego y destellos dorados.
  - 🌊 **Ocean Abyss**: Azul zafiro profundo y agua bioluminiscente.
  - 💾 **Persistencia Permanente en el Dispositivo**: El tema elegido (incluyendo *Material You*), el interruptor de *Aura Dinámica de Carátula* y el modo de visualización de *Video Canvas* se guardan permanentemente en las preferencias del sistema (`SharedPreferences`), conservándose intactos al cerrar y volver a abrir la aplicación.
- **Protección Tipográfica Fija y Marquesina Automática (Cero Desbordamientos)**:
  - Escala de densidad y fuente estabilizada (`fontScale = 1.0f`) para que las configuraciones globales de tamaño de letra en Android no rompan la maquetación ni corten textos.
  - **Títulos en Movimiento (Marquee Fluido)**: Los títulos y artistas largos se desplazan horizontalmente de forma continua (`basicMarquee`) en la pantalla completa *Now Playing*, en el *Mini Reproductor* y en la canción en reproducción dentro de las listas y la cola.
- **Mini Reproductor Flotante con Video Canvas Miniatura**:
  - Barra persistente con barra de progreso, controles táctiles y títulos animados en marquesina.
  - **Soporte de Video Canvas en Miniatura**: El usuario puede configurar en Ajustes si desea visualizar el video en movimiento también en la carátula pequeña del mini reproductor.
  - **Acceso Directo al Ecualizador C++20 integrado** mediante hoja modal inferior sin abandonar la vista actual.
- **Visualizador de Cola de Reproducción con Carátulas (Now Playing Queue Sheet)**:
  - Despliegue modal de la lista en espera ("Up Next") con **miniatura de carátula oficial en alta fidelidad** (`ArtworkImage` de 42dp con esquinas redondeadas) para cada canción, permitiendo identificar instantáneamente cada pista de un vistazo, con marquesina fluida y ecualizador animado en la canción activa.
- **Pantallas Independientes de Bienvenida, Inducción y Disclaimer de Almacenamiento (`OnboardingScreen`)**:
  - Experiencia introductoria modular en 3 etapas diseñada con estética Dark Luxury Neo-Glass:
    - *Etapa 1: Bienvenida a Aura Music*: Filosofía 100% fuera de línea, privacidad total SAF sin escaneos ciegos y motor audiófilo C++20.
    - *Etapa 2: Lo Que Te Ofrecemos*: Resumen de capacidades clave (DSP C++20 de 10 bandas, Video Canvas dinámico, Karaoke con letras sincronizadas LRCLIB y conversor Video a Música 3 en 1).
    - *Etapa 3: Aviso Importante & Disclaimer de Almacenamiento*: Información transparente de que los Video Canvas se almacenan en alta resolución en el teléfono y pueden acumular espacio con el tiempo, complementado con la optimización automática en FFmpeg y el acceso directo a *Ajustes > Medios* para inspeccionar y liberar espacio en MB.
  - Persistencia permanente del estado de bienvenida (`pref_onboarding_completed`), ocultamiento inmersivo de la barra inferior y mini reproductor, y botón accesible desde Ajustes de Apariencia para volver a consultarlo en cualquier momento.
- **Transparencia y Gestión de Medios Almacenados en Ajustes**:
  - Pestaña dedicada **"Medios"** en los Ajustes del sistema (`StoredMediaSettingsTab`) con panel de transparencia total sobre las carátulas WebP y Videos Canvas MP4 guardados en disco (`images/` y `videos/`).
  - Muestra qué archivo pertenece a qué canción, su tamaño exacto en KB/MB y botones con confirmación para borrar la carátula o el video canvas individualmente, liberando espacio físico de inmediato.
- **Apartado "Ajustes" con Navegación por Menús Independientes a Pantalla Completa (`SettingsScreen` & `SettingsSubMenuScreen`)**:
  - La pantalla de Configuración & Ajustes (accesible desde el botón **"Ajustes"** en la barra de navegación inferior) elimina la antigua barra superior de pestañas para ofrecer un menú vertical directo y limpio de tarjetas navegables (con el mismo diseño estilizado de *Diseño del Reproductor* e indicador `>`) para *Diseño del Reproductor*, *Aura Dinámica & Video en Mini Reproductor*, *Paleta Base Predeterminada*, *Auriculares & Acústica DSP*, *Medios & Almacenamiento*, *Motores Nativos & Diagnóstico* y *Bienvenida & Guía*.
  - Al tocar cualquiera de estos apartados, **en lugar de desplegar un modal emergente**, se abre una **pantalla independiente a pantalla completa** (`SettingsSubMenuScreen`) con animación fluida, barra superior de retorno y soporte nativo del botón atrás (`BackHandler`).

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
- **Clarificador de Voces HD en C++20 (`VocalClarityProcessor` Mid-Side)**:
  - Aislamiento del canal vocal central (*Mid = (L+R) × 0.5*) frente al acompañamiento lateral (*Side = (L-R) × 0.5*).
  - Limpieza de turbidez vocal mediante atenuación de graves medios a 180 Hz y realce quirúrgico de presencia (2.8 kHz) y articulación de consonantes (5.5 kHz) con filtros Biquad de 64 bits.
  - Ubicado estratégicamente tanto en la pestaña **Ecualizador** como en **Velocidad/Voz** con presets rápidos (*Sutil 40%*, *Estudio 65%*, *Máximo 100%*).

### 3. Audio Espacial 8D y 16D Multi-Órbita, Repetidor A-B, Eliminación Inteligente de Silencios y Controles Avanzados
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
- **Audio Espacial 8D y 16D Multi-Órbita Binaural en C++20 (Sin Animaciones Decorativas)**:
  - **Modo 8D Clásico (Órbita 360°)**: Paneo orbital tridimensional continuo en tiempo real (4s a 30s por rotación completa) con simulación de sombra de cabeza (*Head Shadow Filtering*) y reverberación binaural.
  - **Modo 16D Multi-Órbita (Doble Capa Contra-Rotatoria)**: Separación espectral en dos capas espaciales independientes en C++20: una órbita interna estable para cuerpo y bajos (< 260 Hz) y una segunda órbita contra-rotatoria a velocidad armónica (1.618x) con modulación figura-8 (Lissajous) y micro-retardo Haas para voces, guitarras y detalles agudos.
  - Interfaz de estudio limpia sin radares ni logotipos animados que consuman recursos innecesarios.
- **Control Estable de Velocidad de Música y Velocidad de Voz / Tono hasta 2.0x (Pitch & Speed)**:
  - Modulación fluida de **0.50x a 2.00x** tanto en **Velocidad de la Música** como en **Velocidad de Voz / Tono (Pitch Shift)**, con botones rápidos de un toque (`0.8x`, `1.0x`, `1.25x`, `1.5x`, `2.0x`) en ambos controles.
  - Protegida con *throttling* y recuperación automática ante anomalías de audio para evitar que la canción se pause accidentalmente.
- **Temporizador de Apagado (Sleep Timer)**:
  - Minutos personalizados o chips rápidos (15m, 30m, 45m, 60m).
  - Contador regresivo en tiempo real con opción de añadir +5 minutos.
  - **Atenuación suave de volumen de 10 segundos** (*fade-out*) antes de pausar.
- **Normalización de Volumen Inteligente (Loudness Normalizer Spotify / EBU R128 en C++20)**:
  - Nivelación automática de sonoridad acústica entre pistas grabadas en épocas dispares o procedentes de fuentes heterogéneas (audios antiguos, grabaciones Hi-Fi, videos de YouTube y clips de TikTok).
  - Algoritmo de control de ganancia dinámico en coma flotante de 64 bits en C++20 con limitador transparente anti-clipping:
    - *Modo Sutil (-18 LUFS)*: Preserva el rango dinámico completo para música clásica y acústica.
    - *Modo Estándar Spotify (-14 LUFS)*: Nivel oficial de streaming Spotify, garantizando volumen consistente sin pérdida de pegada.
    - *Modo Alto (-11 LUFS)*: Sonoridad potente y constante para entornos ruidosos o altavoces portátiles.
- **Automix Inteligente & Crossfade DJ con Curva de Ecualización en X (`DjAutomixFilter` en C++20)**:
  - Mezcla continua y dinámica entre canciones estilo discoteca/DJ.
  - **Detección Acústica de Outro**: Si la energía de la canción actual cae por debajo del umbral de silencio (`< 0.07f`) durante los últimos 4 segundos, salta suavemente a la siguiente pista omitiendo silencios muertos.
  - **Curva de Ecualización DJ en X (Anti Bass Clashing)**: Atenúa progresivamente los subgraves (< 120 Hz) y agudos de la canción que finaliza para abrir espacio acústico a la canción entrante, eliminando saturaciones y choques de frecuencias bajas.
- **Reproducción Continua Automática y Transición Suave (Crossfade / Fade-In)**:
  - Al terminar cualquier canción, avanza y reproduce automáticamente la siguiente pista de la cola o biblioteca de forma ininterrumpida.
  - Fundido de salida progresivo al acercarse al final de la pista y rampa de entrada suave (*fade-in*) calibrada al iniciar la siguiente canción, subiendo poco a poco hasta restaurar el 100% del volumen original sin quedarse atrapado en volumen bajo.
  - Modo Gapless para reproducción continua sin silencios intermedios.
- **Limpieza Atómica de Buffer (Buffer Flushing) en C++20 y Media3**:
  - Purgado atómico a cero de acumuladores IIR en las 10 bandas del ecualizador, filtros de graves, líneas de retardo de Crossfeed y colas de Reverb en `playTrack`, `seekTo`, `pause`, `release` y `onFlush()`.
  - Erradica de forma absoluta cualquier "pop" o chasquido digital residual y colas de reverberación al saltar en la pista o cambiar de canción.

### 4. Video Canvas Multifuncional, Sincronización de Velocidad y Reacción Cromática Instantánea 🎬⚡
- **Respiración y Pulsación Acústica Ligada a C++20 DSP**:
  - Medición RMS y envolvente espectral continua en tiempo real calculada dentro del motor nativo en C++20 (`getAudioIntensity()` y `getVisualizerBands()`).
  - **Carátula Central y Video Canvas 100% Despejados (Cero Insignias Invasivas)**: Se eliminó cualquier cápsula o indicador superpuesto ("VIDEO SYNC" / "LOOP CANVAS") para una apreciación visual prístina, gestionándose todo mediante el botón selector de la barra superior.
  - Halo lumínico ambiental superior y visualizador de 28 bandas con degradado vertical fluido modulando brillo y color en tiempo real según el Video Canvas.
- **Armonización Cromática Instantánea y sin Retrasos (Extracción Exacta con `OPTION_CLOSEST`)**:
  - Eliminación definitiva del retraso de ~2.5 segundos causado por fotogramas clave (*Keyframes*): ahora utiliza `MediaMetadataRetriever.OPTION_CLOSEST` con escalado nativo por hardware a 32x32 y reutilización persistente del descriptor de video (`getOrCreateRetriever`), respondiendo al cambio de color de las escenas de forma inmediata con transición fluida (`tween(180)`).
- **Cero Pantallas Negras ni Parpadeos y Transición de Fundido Suave sin Residuos entre Canciones**:
  - El componente `BackgroundVideoPlayer` sincroniza la posición exacta (`seekTo`) antes de inicializar el buffer (`prepare()`) evitando pausas de re-buffering.
  - El obturador negro de ExoPlayer se desactiva (`setShutterBackgroundColor(TRANSPARENT)`) y se muestra la carátula oficial de la pista como capa base de respaldo (`placeholderTrack`) mientras el decodificador prepara el primer cuadro.
  - **Erradicación Total de Videos Residuales al Cambiar de Canción**: Se eliminó de raíz el problema donde el video de la canción anterior se quedaba congelado en Now Playing y en el Mini Reproductor. Mediante claves reactivas de composición (`key(currentTrack.id, currentTrack.videoUri)`), actualización de player en `AndroidView` y reseteo de `isFirstFrameRendered`, la pista previa se detiene y libera de inmediato, efectuando una transición con fundido suave (*crossfade*) sobre la carátula oficial de la pista entrante.
  - Si la siguiente pista no tiene video, el reproductor de video se desmonta limpiamente mostrando de inmediato la carátula oficial sin retrasos ni imágenes residuales.
- **Encuadre Personalizable de Videos Horizontales (16:9) y Verticales (9:16): Rellenar Pantalla vs Adaptado Horizontal**:
  - Permite al usuario elegir libremente cómo desea ver los videos horizontales tanto **antes de descargar/convertir/editar** como **en tiempo real dentro de Now Playing**:
    - **Fondo Completo • Rellenar (Zoom / Recortar)**: El video llena el 100% de la pantalla de arriba a abajo (`RESIZE_MODE_ZOOM`) tomando la parte central sin bandas ni líneas divisorias a mitad de pantalla.
    - **Fondo Completo • Adaptado Horizontal**: Muestra el fotograma horizontal 16:9 completo centrado (`RESIZE_MODE_FIT`) sin recortar rostros ni bordes laterales, desvaneciendo la carátula estática trasera para evitar costuras horizontales.
  - **Inspección de Proporción y Maquetación Compacta en Ajustes (`StoredMediaSettingsTab`)**: Cada video guardado muestra sus etiquetas (*Sincronizado 480p* / *Loop 480p* y *9:16 Vertical* / *16:9 Panorámico*) en una fila horizontal limpia bajo el título, con marquesina acotada (`clipToBounds`) y recorte automático de franjas negras (`letterbox` 4:3) en miniaturas de YouTube.
- **Bucle Infinito sin Cortes (Seamless Loop con Crossfade) & Efecto Boomerang / Ping-Pong (`reverse` + `concat`) en FFmpeg**:
  - **Modo Crossfade (`xfade`)**: Para loops cortos de Canvas (≤ 20s), el motor nativo `FFmpegNativeEngine` aplica una transición de fundido cruzado (*crossfade* continuo con `xfade`) entre el final y el inicio del video.
  - **Modo Boomerang / Ping-Pong (`reverse` + `concat`)**: El usuario puede elegir el **Efecto Boomerang** en los diálogos de *Editar Canción*, *Video a Música* y *Descargar desde Enlace*. El motor **FFmpeg** invierte una copia del clip y la concatena (`[0:v]split[f][r];[r]reverse[rev];[f][rev]concat=n=2:v=1:a=0`), creando un ciclo continuo de ida y vuelta matemáticamente perfecto donde el último fotograma coincide exactamente con el primero sin ningún corte brusco en ExoPlayer.
- **Optimización de Fotogramas Clave (Keyframes / GOP Corto a 30fps) para Saltos Instantáneos**:
  - Para videos largos sincronizados con la música (> 20s), se reestructura el flujo de video insertando un *Keyframe* (`I-frame`) regular cada 30 fotogramas (exactamente cada 1 segundo a 30 FPS constantes CFR) con `-movflags +faststart`.
  - Al arrastrar el deslizador de tiempo o saltar pistas en *Now Playing* y en el *Mini Reproductor*, el video se sincroniza al milisegundo exacto con 0ms de congelamiento de fotogramas, eliminando los molestos retrasos provocados por los fotogramas clave espaciados de YouTube o TikTok.
- **Compresión Ligera en FFmpeg: Pista de Audio Eliminada (`-an`) y Purga de Metadatos (`-map_metadata -1`)**:
  - El motor nativo de procesamiento aplica sistemáticamente `-an` para eliminar cualquier flujo de audio duplicado del archivo de video, ahorrando de 5 a 20 MB por archivo ya que el audio de alta fidelidad se reproduce desde `songs/`.
  - Purga completa de metadatos, tags y miniaturas innecesarias con `-map_metadata -1` en contenedores MP4.
  - Tubería de remux rápido ultra ligero `stripAudioAndMetadata` (`-c:v copy -an -map_metadata -1 -movflags +faststart`) ejecutada como respaldo universal ante cualquier video de galería, importación o descarga web.
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
- **Selección de Diseños del Reproductor: Modo Clásico vs Modo Cinemático Canvas (Estilo Spotify) (`NowPlayingDesignMode`)**:
  - Permite al usuario elegir libremente el estilo visual de la pantalla completa *Now Playing*, con selector interactivo accesible desde la barra superior (`NowPlayingTopBar`), la botonera inferior de utilidades y los *Ajustes de Apariencia*:
    - 🎛️ **Modo Clásico (Carátula Central)**: Diseño tradicional de Aura Music con carátula flotante grande (1:1), visualizador de ondas sonoras de 28 bandas, barra de balance estéreo L/R en vivo y botonera de atajos inferiores (`EQ FX`, `Letras`, `Cola`).
    - 🎬 **Modo Cinemático Canvas (Estilo Spotify con Reactividad Cromática Total)**: Diseñado especialmente para Video Canvas en movimiento. Despeja más del 70% de la pantalla para el video vertical continuo de fondo, presenta la procedencia en la barra superior ("REPRODUCIENDO DESDE..."), muestra la frase lírica en vivo flotando con borde y halo reactivo al compás del video, viñeta atmosférica ambiental superior e inferior tintada dinámicamente con los colores extraídos del video (`animatedPrimary` y `animatedSecondary`), miniatura cuadrada oficial (54dp), títulos con marquesina fluida, botón de favorito, seekbar interactiva iluminada con el acento del video y selector clásico circular ("bolita"), botón central de Play/Pausa de 64dp con halo pulsante al ritmo del audio, fila de utilidades y acceso directo a "Vista previa de la letra" armonizado.
    - ⚪ **Selector Clásico Circular ("Bolita") con Barra Continua sin Cortes**: Sustitución del indicador vertical tipo mango/palo de M3 y eliminación de los cortes laterales (`thumbTrackGapSize`) y el punto final (`stopIndicator`) mediante renderizado de `track` continuo de 4dp con una bolita circular clásica de 13dp blanca pura, halo suave reactivo y sombra, presente en Modo Clásico, Modo Cinemático Canvas, Karaoke a Pantalla Completa y Balance Estéreo.
    - ✨ **Modo Automático Inteligente (Por Defecto)**: Conmuta dinámicamente al Modo Cinemático Canvas cuando la canción reproduce Video Canvas en fondo completo, y al Modo Clásico cuando la pista es de solo carátula estática o de audio tradicional.
  - Persistencia permanente de la elección en las preferencias del sistema (`pref_now_playing_design_mode`).
- **4 Modos de Visualización Seleccionables por el Usuario (`VideoDisplayMode`)**:
  - 📱 **Fondo Completo (Rellenar / Recortar)**: El video rellena verticalmente toda la pantalla de Now Playing (`RESIZE_MODE_ZOOM`) sin divisiones horizontales, tomando la parte central aunque recorte laterales.
  - 🎬 **Fondo Completo (Adaptado Horizontal)**: El video horizontal se muestra completo de lado a lado (`RESIZE_MODE_FIT`) sin recortar caras ni detalles.
  - 🔲 **Lienzo en Carátula (Card Canvas)**: El video se reproduce dentro del marco central de la carátula (relación de aspecto 1:1 estilo marco cinemático).
  - 🖼️ **Solo Carátula**: Muestra únicamente la carátula estática o procedural sin video.
- **Detección Automática y Forzado Manual**:
  - **Loop Canvas (≤ 10s - 20s)**: Bucle infinito continuo silenciado.
  - **Video Largo Sincronizado (> 20s)**: Sincronizado con la reproducción y los saltos temporales (`seekTo`).
  - **Selector en Descarga, Edición y Barra Superior**: Accesible desde `DownloadFromLinkDialog`, `VideoToMusicDialog`, `EditTrackDialog` y `NowPlayingTopBar`.
- **Descarga Continua en Segundo Plano con Notificación Nativa (`AuraDownloadService`)**:
  - Servicio en primer plano (`ForegroundService`) que mantiene viva la descarga por bloques, extracción de carátula WebP y optimización FFmpeg aunque el usuario salga de Aura Music o bloquee el teléfono.
  - Muestra el progreso en vivo en la barra de notificaciones de Android y, al terminar fuera de la app, avisa con una notificación interactiva que reproduce la canción inmediatamente al tocarla.
- **Acceso Directo a la Playlist Más Escuchada en Inicio**:
  - La cuadrícula 2x2 de la pantalla de Inicio destaca automáticamente la Playlist con mayor número de reproducciones acumuladas del usuario (reemplazando el antiguo botón redundante del ecualizador).
- **Sincronización Instantánea de Favoritos**: El botón de corazón en Now Playing y en la Biblioteca refleja reactivamente el estado en tiempo real (icono relleno en rojo `Color(0xFFEF4444)` al marcar favorito).
- **Lista de Inicio Estable**: La sección "Populares en tu biblioteca" permanece fija y nunca elimina otras canciones al marcar un favorito.

### 5. Letras Sincronizadas (.LRC y .TXT) Estilo Karaoke con Búsqueda Interactiva, Elección de Versiones y Recomendación Oficial 🎤📜
- **Búsqueda Interactiva Personalizada y Selección de Versiones (`SearchLyricsDialog`)**:
  - Diálogo modal con diseño Dark Luxury Neo-Glass donde el usuario puede escribir o editar el nombre de la canción y el artista libremente para buscar letras exactas o variantes.
  - **Recomendación de Lírica Oficial en Primer Lugar (#1)**: El sistema identifica y prioriza la versión canónica oficial de LRCLIB (o la coincidencia más fiel con timestamps sincronizados) presentándola en primera posición con una insignia destacada `⭐ OFICIAL / RECOMENDADA`, borde de neón y botón prioritario.
  - **Búsqueda Multidimensional con Búsqueda Específica por `track_name` y Desacople de Duración Local**: Resuelve canciones populares buscando directamente por nombre de pista (`?track_name=...`) y texto amplio (`?q=...`), omitiendo restricciones de duración local cuando el audio está recortado para encontrar siempre letras completas.
  - **Tarjeta de Karaoke Compacta y sin Desbordamientos**: Estado vacío rediseñado con desplazamiento vertical fluido (`verticalScroll`), botón principal destacado "Buscar Letras en Línea" y fila compacta de acciones "Importar" / "Escribir", eliminando al 100% cualquier corte o deformación visual.
  - **Comparación de Versiones Alternativas**: Muestra las opciones disponibles en la base de datos comunitaria, con etiquetas de formato (*Sincronizada (Karaoke)* vs *Texto Plano*), duración y previsualización de versos (*snippet*). El usuario puede elegir cualquier versión con un solo toque y aplicarla al instante.
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
- **Modo Karaoke a Pantalla Completa Inmersivo (Full Screen Lyrics estilo Spotify / Apple Music Sing)**:
  - Experiencia inmersiva completa accesible desde el botón de pantalla completa en la tarjeta de Karaoke o desde los controles de reproducción.
  - Fondo degradado dinámico cinematográfico con halo ambiental que respira al ritmo del audio en C++20.
  - Tipografía grande de alta definición (hasta 26sp) con resaltado neón, escala suave ampliada y atenuación progresiva de versos pasados y futuros.
  - Auto-scroll continuo y botón flotante de sincronización (*"Sincronizar con audio"*) que aparece si el usuario explora la letra manualmente.
  - Barra de transporte flotante inferior con minicarátula, marquesina fluida de título y artista, seekbar interactiva y controles de salto y reproducción.
- **Edición y Carga Manual**: Diálogo modal para ingresar o pegar letras `.lrc` personalizadas o texto plano en cualquier momento.
- **Acceso Rápido**: Botón de micrófono en la barra superior de Now Playing y botón alternador en los atajos inferiores para alternar entre carátula/canvas y vista karaoke con un solo toque.

### 6. Video a Música (Extracción 3 en 1 Directa en el Teléfono) 🎬➡️🎵
- **Solución Nativa para Usuarios Móviles sin PC**: Permite seleccionar cualquier video de la galería (conciertos, clips de redes, TikToks, descargas) y transformarlo instantáneamente en una pista de música completa.
- **Flujo 3 en 1 Automático**:
  - 🎵 **Extracción de Audio Directa**: Demuxing sin recodificación (*Direct Stream Copy*) mediante `MediaExtractor` y `MediaMuxer` en Android a formato `.m4a` guardado en `songs/`, preservando la fidelidad acústica al 100% y ejecutándose en ~1 segundo.
  - 🖼️ **Captura Inteligente de Carátula**: Extrae un fotograma clave del video en alta definición (evitando pantallas negras de inicio) y lo comprime en WebP sin pérdida a `images/`.
  - 🎬 **Video Canvas Vinculado**: Vincula el video como Canvas de fondo sincronizado en `NowPlayingScreen`.
- **Reproducción Inmediata**: Al finalizar la conversión, la pista recién creada se reproduce de inmediato con su Video Canvas y atmósfera lumínica armonizada.

### 7. Descarga Directa desde TikTok, YouTube y Enlaces Web con FFmpeg Puro y yt-dlp Integrados en el APK Final 🎬🔗🎵
- **Descargas sin Límite de Duración**: Permite pegar enlaces de **TikTok**, **YouTube** y videos web para descargar música completa, directos, sesiones o parodias de cualquier duración.
- **Motor FFmpeg Puro sin Wrappers en el APK Final (`FFmpegNativeEngine` + `app/build.gradle.kts`)**:
  - Binario nativo ejecutable `libffmpeg.so` empaquetado directamente en `jniLibs/` para todas las arquitecturas (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`), aprovisionado y compilado de forma 100% pura desde la tarea `provisionNativeDeps` en `app/build.gradle.kts` y `native_ffmpeg_launcher.c` con el NDK Clang Toolchain (`dlopen`/`dlsym` sobre `libffmpegkit.so` / `libavcodec.so` / `libavfilter.so`), instalado con permisos nativos de ejecución en `nativeLibraryDir` sin depender jamás de scripts `.sh`.
  - Paquete dinámico optimizado con solo lo necesario para audio y video (`libffmpeg.zip.so`), descomprimido de forma atómica en segundo plano para procesar y transcodificar en alta fidelidad (AAC, Opus, Vorbis, FLAC, WebM -> M4A / MP3), aplicar filtros de video (`xfade`, `reverse` + `concat` Boomerang, Keyframes GOP corto) y fusionar flujos DASH de video y audio (`-c copy`) sin inflar el APK con encoders pesados innecesarios.
- **Entorno Python Nativo y Actualización en Caliente OTA Blindada para yt-dlp (`YtDlpNativeEngine` & `YtDlpAutoUpdater`)**:
  - Runtime de CPython nativo (`libpython.so`) con entorno optimizado (`libpython.zip.so`) re-comprimido con **Deflate Nivel 9**, libre de tests/paquetes pesados (`_pydecimal`, `pydoc`, `mailbox`, `pickletools`, `difflib`, `distutils`, `lib2to3`, `tarfile`, `bdb`, `tracemalloc`, `webbrowser`, `compileall`, `py_compile` y codificaciones arcaicas de mainframes IBM/MacOS Classic/PalmOS en `encodings/`) y módulos C innecesarios (`_sqlite3`, `_tkinter`, `_test`, `audioop`, `_decimal`, `_lsprof`, `_statistics`, `_zoneinfo`, `termios`, etc.), leído directamente vía `zipimport` desde `nativeLibraryDir/libpython.zip.so` sin extraer `stdlib.zip` al disco interno. Los módulos C nativos se instalan directamente en `nativeLibraryDir/libpymod_*.so` y se enlazan con `Os.symlink` (0 bytes) sin triplicarse en el teléfono, junto con el motor QuickJS (`libqjs.so`), poda de `libavdevice.so`/`libffmpegkit_abidetect.so`, optimización agresiva `android.enableR8.fullMode=true` con limpieza de `Intrinsics` y filtrado de recursos `localeFilters` para todas las variantes de Español e Inglés (incluyendo `en-rES` / `b+en+ES`).
  - Copia base oficial de `yt-dlp` re-comprimida con **Deflate Nivel 9** y empaquetada como `libytdlp.zip.so` en `nativeLibraryDir` con verificación criptográfica SHA-256 (`SHA2-256SUMS`) para ejecución inmediata sin duplicar megabytes en `assets/` ni en `files/bin/`.
  - **Verificación y Actualización Transparente de Paquetes al Iniciar (`YtDlpAutoUpdater` & `PackageUpdateBanner`)**:
    - Al entrar a la app, verifica en segundo plano si los paquetes nativos (`yt-dlp`) están en su última versión, mostrando una notificación nativa silenciosa en la barra de estado (*"Verificando paquetes necesarios..."*) y una píldora no intrusiva dentro de la app.
    - Cuando detecta una actualización disponible, la notificación nativa y el indicador interno muestran una barra de progreso en tiempo real (*"Descargando actualización de paquetes"*), con blindaje criptográfico SHA-256 (`SHA2-256SUMS`).
    - Mientras se descarga o está pendiente de aplicar una actualización de `yt-dlp`, las descargas por el motor `yt-dlp` quedan bloqueadas preventivamente (permitiendo usar InnerTube o WebView).
    - Al finalizar la descarga en staging seguro (`yt-dlp.staged`), recomienda al usuario salir de la app o pulsar **"Actualizar y Reiniciar"** para aplicar el nuevo paquete instantáneamente.
  - **Protección Zip Slip y Consolidación de Certificados TLS en Android 14+**: Tanto `FFmpegNativeEngine` como `YtDlpNativeEngine` validan exhaustivamente las rutas canónicas (`canonicalFile.toPath().startsWith(...)`) al desempaquetar librerías dinámicas, impidiendo cualquier escape de directorio. Además, en Android 14+ (API 34+), consolida automáticamente los certificados CA de Conscrypt APEX (`/apex/com.android.conscrypt/cacerts/`) y KeyStore en `usr/etc/tls/cert.pem` con auto-recuperación ante fallos de emisor local (`CERTIFICATE_VERIFY_FAILED`).
  - **Disparador Dual**: Comprobación automática ante errores de extracción de YouTube y botón interactivo manual en *Ajustes > Apariencia & Temas > Motores de Extracción & yt-dlp OTA*.
- **Arquitectura de Extracción Resiliente de 3 Niveles con Carátula Garantizada**:
  - ⚡ **Motor InnerTube Nativo (`InnerTubeClient`)**: Consulta directa ultrarrápida al endpoint oficial de YouTube mediante clientes de baja fricción (`ANDROID_VR` y `VISIONOS`). Entrega flujos de audio y video directos sin cifrado de firma (`n-sig`) ni bloqueos de `LOGIN_REQUIRED` en menos de ~300ms.
  - 🛡️ **Bypass de Respaldo Invidious (`InvidiousStreamResolver`)**: Para pistas con restricciones estrictas de derechos de autor (VEVO, discográficas) que exigen inicio de sesión en clientes anónimos, consulta en milisegundos instancias públicas de alta disponibilidad que descifran los enlaces directos a `googlevideo.com`.
  - 🌐 **Motor Headless WebView Reparado con Garantía de Carátula (`HeadlessWebViewExtractor`)**: Navegador efímero en segundo plano cargado sobre `m.youtube.com` (evitando el error 150) con timeout de 22s, extracción de miniaturas oficiales en el DOM y **descarga en cascada resiliente** (`maxresdefault.jpg` -> `hqdefault.jpg` -> `mqdefault.jpg` -> `i.ytimg.com` -> fotograma clave de video). ¡Garantiza que ningún video descargado se quede jamás sin carátula!
  - 🎛️ **Selector Interactivo en el Diálogo**: El usuario puede alternar entre ambos motores en el diálogo de descarga con auto-fallback cruzado de 3 capas.
- **Acelerador de Descarga sin Estrangulamiento (Chunked Range Download a Máxima Velocidad)**:
  - Destruye la limitación artificial de ~63 KB/s de los servidores de Google Video mediante descargas fragmentadas por bloques HTTP Range (`Range: bytes=X-Y` de 2.5 MB).
  - Descarga a la velocidad real de la red (10 MB/s - 40 MB/s), completando pistas de audio en segundos y videos en ~2-4 segundos.
- **Video Canvas por Defecto en 480p Óptimo y Vinculación Automática Garantizada**:
  - Tanto `yt-dlp` como `InnerTube` e `Invidious` priorizan por defecto y de forma estricta la resolución **480p en contenedor MP4 con códec H.264 (`avc1`)**, excluyendo manifiestos HLS (`.m3u8`), DASH (`.mpd`) y códecs conflictivos (`AV1`/`VP9`), y preservando los `http_headers` (`User-Agent`) firmados por el extractor para evitar rechazos HTTP 403 en `googlevideo.com`.
  - Si el flujo inicial tenía `videoUrl == audioUrl` (pista de solo audio), `OnlineVideoAudioImporter` resuelve activamente en segundo plano un stream de video MP4 de 480p dedicado y verifica mediante `MediaMetadataRetriever` (`METADATA_KEY_HAS_VIDEO`) que contenga pista de video real antes de vincularlo en `videos/`.
- **Extracción Automática 3 en 1**:
  - 🎵 **Audio de Alta Fidelidad**: Extrae la pista de audio pura en formato `.m4a` o `.mp3` directamente a `songs/`.
  - 🖼️ **Carátula Oficial en WebP**: Descarga la portada oficial en alta resolución y la procesa a WebP sin pérdida en `images/`.
  - 🎬 **Video Canvas Vinculado (480p)**: Almacena el video en `videos/` para reproducirlo de fondo continuo o sincronizado en *Now Playing*.
- **Previsualización y Edición Rápida**: Muestra título, autor/creador, duración y portada antes de confirmar con temas visuales adaptativos (Cyan/Magenta para TikTok, Rojo Carmesí/Naranja para YouTube/Web).
- **Cierre Automático del Modal y Reproducción al Instante**: Al confirmar la descarga, el modal de inserción de enlaces se cierra automáticamente para despejar la interfaz (mostrando el progreso compacto en segundo plano), y una vez descargada la canción inicia su reproducción abriendo *Now Playing*.

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

### 12. Playlists con Imagen Personalizada o Collage Dinámico (1-4 Fotos), Vista de Álbumes/Artistas y Almacenamiento Estructurado
- **Portadas Personalizadas en Playlists y Collage Dinámico Automático de 1 a 4 Canciones (`PlaylistCoverCollage`)**:
  - El usuario puede asignar cualquier **imagen personalizada de su galería** como portada de una Playlist (tanto al crearla como al editarla), la cual se comprime a **WebP sin pérdida** en `images/` y se elimina automáticamente al cambiarla o borrar la lista.
  - Si el usuario **no asigna una imagen manual**, la Playlist genera automáticamente un **collage dinámico** basado en las canciones que contiene:
    - **1 canción**: Muestra la carátula de esa única canción a cuadro completo.
    - **2 canciones**: Muestra un collage dividido de 2 fotos lado a lado.
    - **3 canciones**: Muestra un collage equilibrado de 3 fotos (2 arriba y 1 panorámica abajo).
    - **4 o más canciones**: Muestra una cuadrícula 2x2 con las carátulas de las primeras 4 canciones añadidas.
- **Vista Detallada de Canciones para Artistas y Álbumes**:
  - Al tocar cualquier **Artista** o **Álbum** en la Biblioteca, la aplicación **no reproduce directamente la primera canción**, sino que despliega una vista detallada idéntica a la de las Playlists (`PlaylistDetailScreen`), mostrando su collage de portadas, el listado completo de sus canciones y los botones dedicados de *Reproducir* y *Aleatorio*.
- **Pestaña "Playlists" en Tu Biblioteca**: Tarjeta "Tus Me Gusta" sincronizada, creación, edición de nombre/descripción/portada y adición rápida de canciones.
- **Almacenamiento Organizado** en `Android/data/com.aistudio.musicplayer.aurasound/files/`:
  - 📁 `images/`: Carátulas en WebP Lossless.
  - 📁 `songs/`: Canciones locales y demos.
  - 📁 `lyrics/`: Archivos de letras sincronizadas (`.lrc`).
  - 📁 `metadata/`: Ficheros JSON estructurados con información técnica.
  - 📁 `videos/`: Videos de fondo y loops de Canvas (.mp4/.webm).

### 13. Mixtape Maker / Fusión de Canciones con Crossfade Continuo en FFmpeg 🎛️✨
- **Fusión Nativa en un Solo Archivo Continuo (.m4a) con FFmpeg (`MixtapeEngine`)**:
  - Permite tomar cualquier lista de canciones (mínimo 2 pistas) y combinarlas en una sola pieza musical continua mediante fundido cruzado (*acrossfade* en FFmpeg).
  - Selector de duración de transición personalizado: **3s**, **5s (recomendado)**, **8s** o **10s** de fundido suave entre canciones.
  - Normalización acústica previa a 44.1 kHz estéreo para garantizar cero chasquidos, desajustes de reloj o saltos de volumen durante la transición.
- **Carátulas y Video Canvas Reactivos en Tiempo Real por Capítulos**:
  - Resuelve de forma elegante el dilema visual de los mixes largos: en lugar de dejar estática la primera carátula durante toda la hora, el sistema registra un fichero estructurado de capítulos (`metadata/mixtape_{id}.json`) con los offsets exactos calculados matemáticamente.
  - **Conmutación Visual Dinámica**: Al reproducirse el Mixtape, tanto la pantalla completa *Now Playing* como el *Mini Reproductor* detectan reactivamente el segundo actual (`currentPositionMs`) y transicionan suavemente el título, artista, carátula oficial WebP y Video Canvas sincronizado de la canción que suena en ese tramo.
  - **Sincronización Cromática**: La paleta ambiental superior y el brillo de la pantalla extraen los tonos de la carátula o video de cada capítulo activo instantáneamente.
  - **Insignia de Mezcla**: Muestra una etiqueta refinada `MIX X/Y` en los controles de reproducción indicando qué segmento está activo del mix total.
- **Modo Karaoke Continuo con Letras (.LRC) Concatenadas**:
  - El motor compila automáticamente los archivos de letras de cada canción agregando el offset acumulado `[mm:ss.xx]` a cada verso.
  - Al abrir la tarjeta de Karaoke durante el Mixtape, los versos corren sincronizados con auto-scroll a lo largo de todo el mix continuo.
- **Portada Collage Oficial en WebP**:
  - Genera automáticamente una portada oficial en collage de 1 a 4 canciones en formato WebP Lossless en `images/`, integrándose como una pista completa en la biblioteca de Aura Music bajo el álbum *"Aura Mixtapes"*.

### 14. Modo Bit-Perfect 1:1, Salida AAudio de Ultra-Baja Latencia, Formatos Especiales (DSD/APE/Chiptune) y Widget Interactivo 🔊🎛️
- **Modo Bit-Perfect Direct Bypass & Motor Nativo AAudio en ISO C++20 (`dsp_bitperfect.h`)**:
  - **Bypass Puro 1:1**: Permite desactivar con un interruptor toda modificación matemática de fase, ecualización o espacialización para enviar los samples PCM puros de la pista (copia bit a bit 1:1) con medición en tiempo real de rango dinámico (dB) y pico de señal.
  - **Puente Hardware AAudio (`AAudioStreamBuilder`)**: Sonda y abre flujos nativos de baja latencia (`AAUDIO_PERFORMANCE_MODE_LOW_LATENCY`) con soporte de direccionamiento exclusivo hacia DACs USB externos y auriculares Hi-Res, reportando la tasa de muestreo nativa del hardware y la latencia exacta en milisegundos.
  - **Presets Hi-Res Configurables**: Selector en *Ajustes > Auriculares & Acústica DSP* entre *Nativo 1:1 (Sin Resampling)*, *Hi-Res 96 kHz / 24-bit* y *Ultra Hi-Res 192 kHz / 32-bit Float*, junto con telemetría detallada en la ficha técnica de cada canción (`AudioSpecsDialog`).
- **Decodificación Nativa de Formatos Especiales (`SpecialAudioFormatDecoder`)**:
  - Soporte directo al importar o abrir archivos audiófilos y retro: **DSD Super Audio CD** (`.dsf`, `.dff`), **Monkey's Audio** (`.ape`), **WavPack** (`.wv`), **Apple Lossless / AIFF** (`.alac`, `.aiff`), **Musepack** (`.mpc`) y módulos **Chiptune / Tracker** (`.mod`, `.xm`, `.it`, `.s3m`).
  - Conversión sin pérdida automatizada mediante `FFmpegNativeEngine` hacia contenedor maestro local en `songs/`, con insignias dedicadas (`DSD`, `APE`, `MOD/XM`, `ALAC`, `WAV`) en la biblioteca y el reproductor.
- **Widget Interactivo de Pantalla de Inicio y Soporte Android Auto (`AuraMusicWidgetProvider`)**:
  - Widget redimensionable para el escritorio de Android con estética Dark Luxury Neo-Glass, carátula redondeada o arte procedural en vivo, insignia dinámica del motor (`BIT-PERFECT AAUDIO` / `C++20 DSP`) y controles directos (*Anterior*, *Play/Pausa*, *Siguiente*) que funcionan incluso con la aplicación cerrada.
  - Integración declarada con **Android Auto** (`automotive_app_desc.xml`) para control desde la pantalla del vehículo.

### 15. Suite de Diagnóstico Autónoma: Aura Monitor 🛠️ & LeakCanary
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

### Compilación Local con Aprovisionamiento Nativo Puro y Firma en Gradle (Cero `.sh`):
```bash
# Ejecutar compilación Debug (con Aura Monitor y LeakCanary en 4 ABIs)
gradle :app:assembleDebug

# Ejecutar compilación Beta ("Aura Beta", com.auramusic.beta, Codename Nebula, 3 APKs móviles sin Debug)
gradle :app:assembleBeta
```

### GitHub Actions (Compilación Debug y Canal Beta `Nebula`):
- **Compilación de APK Beta — `Aura Beta` (`.github/workflows/build-beta-apk.yml`)**:
  - **Identidad Beta**: Nombre en el launcher **`Aura Beta`**, identificador de paquete **`com.auramusic.beta`** (almacenamiento en `Android/data/com.auramusic.beta/files/`), versión **`v0.1.0-beta.1a`**, codename **`Nebula`** y código estratégico **`NEBULA-00101A`** (`versionCode = 100101`).
  - **Limpio de Herramientas Debug y Pistas de Prueba (Excepto Actualizador OTA `yt-dlp`)**: Elimina por completo `DebugMonitorActivity` ("Aura Monitor") del Manifiesto y Ajustes (`BuildConfig.ENABLE_DEBUG_MONITOR = false`), oculta la tarjeta de generación de canciones de prueba Synthwave (`BuildConfig.ENABLE_DEMO_TRACKS = false`) y excluye `LeakCanary`, manteniendo **100% activo el actualizador OTA en caliente de `yt-dlp`**.
  - **100% Sin Caché y Basado en el Flujo Probado de Debug**: Compila siempre desde cero (`--no-build-cache --rerun-tasks`) sin depender de `actions/cache`.
  - **Exclusivo para Móviles (3 APKs) y Sin Arquitecturas de PC**: Elimina `x86` y `x86_64` y genera 3 APKs:
    1. `AuraBeta-v0.1.0-beta.1a-Nebula-arm64-v8a.apk` (64 bits).
    2. `AuraBeta-v0.1.0-beta.1a-Nebula-armeabi-v7a.apk` (32 bits).
    3. `AuraBeta-v0.1.0-beta.1a-Nebula-universal.apk` (Universal 32 + 64 bits).
  - **Firma desde GitHub Secrets**: Busca automáticamente en `secrets` las variables `BETA_KEYSTORE_BASE64` (o `KEYSTORE_BASE64`), `BETA_KEYSTORE_PASSWORD` (o `STORE_PASSWORD`), `BETA_KEY_ALIAS` (o `KEY_ALIAS`) y `BETA_KEY_PASSWORD` (o `KEY_PASSWORD`).
  - **Regla de Activación Dual (Manual vs Pre-Release con Tag `-beta`)**:
    - Si se activa **manualmente (`workflow_dispatch`)**, genera y sube los 3 APKs únicamente a los **Artifacts** de la ejecución (no los sube a ningún Release).
    - Si se activa por un **Pre-Release con su respectivo Tag `-beta`** (ej. `v0.1.0-beta.1a`), adjunta los 3 APKs y `SHA256SUMS.txt` a los **Assets del Pre-Release** e inyecta automáticamente las notas y la tabla explicativa de APKs desde **`chanelog-beta.md`**.
  - **Actualizador Automático de Versiones APK en la App (`AppReleaseUpdater` & `AppUpdateDialog`)**:
    - Conectado por defecto al repositorio oficial **`LuisAlejandro544/Aura-music`** (`https://github.com/LuisAlejandro544/Aura-music/releases` y API `https://api.github.com/repos/LuisAlejandro544/Aura-music/releases`).
    - Filtra los Pre-Releases `-beta` y compara semánticamente cualquier formato de tag dinámico (`v0.1.0-beta.1a` vs `v0.1.0-beta.2d` vs `v0.2.0-beta.1m`) desglosando versión, número de revisión beta y letra estratégica (`a`, `d`, `m`, `s`, `u`).
    - Detecta automáticamente la arquitectura del teléfono (`Build.SUPPORTED_ABIS`) para descargar el APK exacto (`arm64-v8a`, `armeabi-v7a` o `universal`), valida su integridad SHA-256 contra `SHA256SUMS.txt`, presenta las notas de `chanelog-beta.md` y lanza el instalador de Android mediante `FileProvider` conservando todos los datos en `Android/data/com.auramusic.beta/`.
- **Compilación de APK Debug (`.github/workflows/build-debug-apk.yml`)**:
  - Se activa manualmente desde la pestaña **Actions -> Run workflow** (`workflow_dispatch`).
  - **Caché Inteligente de Dependencias Nativas**: Mediante `actions/cache@v4`, almacena y restaura instantáneamente los binarios de FFmpeg, CPython 3.11, QuickJS y yt-dlp (`app/src/main/jniLibs` y `app/src/main/assets/bin`) basados en el hash de `app/build.gradle.kts`, `gradle/libs.versions.toml` y `app/src/main/cpp/*`.
  - **Compilación Autónoma en Gradle ante Cache Miss**: Si no existe la caché o si se activa el parámetro `force_rebuild_native`, el runner ejecuta `./gradlew :app:provisionNativeDeps` para resolver los artefactos nativos reales y compilar los lanzadores PIE puros para las 4 arquitecturas (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) con el Android NDK sin utilizar ningún script `.sh`.
  - Restaura o genera la firma `debug.keystore` desde la tarea Gradle `ensureDebugKeystore`, compila con C++20/C17 y sube el APK Debug listo para descargar.
- **Purgar Binarios del Historial Git (`.github/workflows/purge-native-binaries-history.yml`)**:
  - Permite limpiar definitivamente los archivos `.so`, `.zip.so` y `yt-dlp` del historial remoto usando `git-filter-repo` previa confirmación manual (`PURGAR`), dejando el repositorio ultra liviano.
  - El archivo `.gitignore` está configurado para evitar que los archivos binarios compilados vuelvan a ser añadidos al control de versiones.

---

## 📦 Distribución

Aura Music (`Aura Beta` • `com.auramusic.beta`) está preparado para distribuirse libremente como APK independiente en tiendas como **Uptodown**, GitHub Pre-Releases (`chanelog-beta.md`) o instalación manual directa en teléfonos Android.
