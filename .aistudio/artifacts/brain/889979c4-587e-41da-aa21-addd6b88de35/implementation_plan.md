# Actualización Transparente de Paquetes en Segundo Plano & Motores C++20 (Claridad Vocal y Audio 16D)

Esta actualización incorpora un sistema transparente de verificación y descarga de paquetes nativos (`yt-dlp`) al iniciar la aplicación —con notificación nativa en la barra de estado, barra de progreso en vivo, aviso compacto no intrusivo dentro de la app, bloqueo preventivo de descargas por `yt-dlp` durante la actualización y reinicio rápido para aplicar cambios— junto con dos nuevos procesadores acústicos en **ISO C++20**: **Clarificador de Voces HD** y **Audio 16D Multi-Órbita**, ubicados estratégicamente en la hoja modal de efectos sin iconos animados ni distracciones visuales.

## User Review & Critical Decisions

> [!IMPORTANT]
> Se han consolidado todas las decisiones clave confirmadas para esta implementación:

- **Verificación y Actualización en Segundo Plano con Notificación Dual**: Al entrar a Aura Music, la app inicia de inmediato sin pantallas de espera bloqueantes y lanza en segundo plano la verificación de paquetes mostrando una notificación nativa (*"Verificando paquetes necesarios..."*) y una píldora superior discreta en la interfaz. Si detecta una nueva versión de `yt-dlp`, tanto la notificación del sistema como el indicador en la app pasan a mostrar la barra de progreso en vivo (*"Descargando actualización de paquetes..."* con porcentaje).
- **Bloqueo Preventivo de `yt-dlp` y Aplicación Forzada / Reinicio**: Mientras `yt-dlp` se está actualizando o tiene una actualización descargada pendiente de aplicar, las descargas mediante el motor `yt-dlp` quedan bloqueadas temporalmente con un mensaje informativo claro. Al concluir la descarga y validación criptográfica `SHA-256`, se ofrece un botón directo **"Aplicar y Reiniciar Ahora"** (además de recargar el binario y recomendar reiniciar/salir) para garantizar que la nueva versión quede activa al 100%.
- **Clarificador de Voces HD y Audio 16D en C++20 (Sin Animaciones Decorativas)**: Se integran en el motor nativo de 64 bits el procesador de **Claridad Vocal** (realce de canal central *Mid* y bandas de articulación vocal) y el motor **Audio 16D Multi-Órbita** (doble órbita binaural con separación de frecuencias graves y melódicas en contrarrotación). Siguiendo tu indicación, la interfaz utiliza controles limpios y directos sin logotipos animados ni gráficos decorativos innecesarios.
- **Ubicación Estratégica de Controles**: El **Audio 16D** comparte la pestaña espacial (**"Audio 8D / 16D"**) junto al Audio 8D para conmutar entre ambos modos orbitales con un toque, mientras que el **Clarificador de Voces HD** se sitúa estratégicamente tanto en la pestaña **"Ecualizador"** (junto al refuerzo de graves) como en la pestaña **"Velocidad/Voz"**.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Gestor de Paquetes con Notificación Nativa y Estado Reactivo**: Supervisa al arrancar la app si el paquete `yt-dlp` está en su última versión oficial, informando de forma transparente en la barra de notificaciones de Android y en una franja compacta no intrusiva dentro de la app, bloqueando temporalmente el uso de `yt-dlp` durante la descarga y facilitando el reinicio inmediato de la app al terminar.
  2. **Motor C++20 de Clarificación de Voces (Vocal Clarity)**: Desempaqueta la voz principal del centro estéreo (*Mid-Side Vocal Isolation*) y aplica filtros bi-cuadráticos de presencia (`1.8 kHz` y `3.5 kHz`) mientras atenúa la resonancia fangosa (`220 Hz`), logrando voces nítidas en cualquier canción o podcast.
  3. **Motor C++20 de Audio 16D Multi-Órbita**: Divide la señal en dos planos acústicos independientes (base rítmica/bajos y plano vocal/armónico) que orbitan en trayectorias binaurales complementarias con filtro de sombra acústica de cabeza, brindando una espacialidad más rica y equilibrada que el 8D clásico.
- **Target Audience / Persona**: Usuarios móviles sin PC que desean que las descargas de YouTube funcionen siempre al día sin errores inesperados y que buscan herramientas de mejora acústica avanzadas y directas.
- **Key Value**: Cero fallos silenciosos por paquetes desactualizados y una suite DSP C++20 más potente y ergonómica.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Flujo de Inicio y Actualización de Paquetes**:
     - **Paso 1 (Verificación)**: El usuario abre Aura Music y navega normalmente por su biblioteca. En la barra de estado de Android aparece una notificación silenciosa *"Verificando paquetes necesarios..."* y en la parte superior de la app un chip compacto discreto.
     - **Paso 2 (Al día vs. Actualización disponible)**:
       - Si todo está actualizado, la notificación y el chip desaparecen suavemente tras confirmar la versión.
       - Si hay una nueva versión de `yt-dlp`, la notificación nativa y el chip en la app muestran una barra de progreso porcentual: *"Descargando actualización de paquetes (0% - 100%)"*.
     - **Paso 3 (Bloqueo temporal de `yt-dlp`)**: Si el usuario abre el diálogo *"Descargar desde YouTube / Web"* mientras los paquetes se están descargando o antes de aplicar la actualización, el selector de `yt-dlp` muestra un aviso de bloqueo temporal preventivo (*"Actualización de yt-dlp en curso: espera a que finalice y aplica los cambios para descargar con este motor"*).
     - **Paso 4 (Finalización y Reinicio)**: Al completarse la descarga verificada con `SHA-256`, la notificación y el banner no intrusivo cambian a *"Paquetes actualizados • Reinicia la app para aplicar"*, incluyendo el botón **"Aplicar y Reiniciar Ahora"** que recarga la aplicación al instante con los nuevos paquetes activos.
  2. **Flujo Estratégico de Claridad Vocal y Audio 16D**:
     - Desde el Mini Reproductor o Now Playing, el usuario abre la hoja modal de efectos (`EQ FX`).
     - En la pestaña **"Ecualizador"** (y también en **"Velocidad/Voz"**), encuentra el control **"Clarificar Voces (C++20)"** con interruptor, deslizador de nitidez vocal (`0% - 100%`) y 3 perfiles rápidos (*Suave*, *Nítido HD*, *Enfoque Vocal*).
     - En la pestaña **"Audio 8D / 16D"**, puede elegir con un selector limpio entre **Modo 8D (Órbita Simple 360°)** y **Modo 16D (Doble Órbita Multi-Capa)**, ajustando velocidad de rotación, amplitud espacial y profundidad acústica sin animaciones superfluas.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Dark Luxury Neo-Glass OLED** consistente con el diseño actual de Aura Music, priorizando legibilidad, superficies 100% opacas y controles directos sin animaciones ornamentales.
  - *Color Palette & Mood*: Superficies `--background: #07080D`, tarjetas `--card: #121521`, bordes sutiles `--border: #23283D`, acentos dinámicos del tema activo y verde esmeralda `#10B981` para confirmación de paquetes listos.
  - *Typography & Hierarchy*: Escala tipográfica fija protegida (`fontScale = 1.0f`), etiquetas técnicas claras con valores en porcentaje/segundos en vivo.
  - *Component Styling & Layout*: Banner compacto superior de una sola línea con barra de progreso lineal de `4.dp` y área táctil mínima de `48.dp` en botones interactivos.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Notificación Nativa + Banner No Intrusivo con Bloqueo Preventivo de `yt-dlp`**
  - *Chosen Approach*: Ejecutar la verificación y descarga por bloques con progreso real en segundo plano publicando tanto una notificación nativa del sistema (`NotificationCompat` con canal silencioso `IMPORTANCE_LOW` durante el progreso) como un estado reactivo `PackageUpdateState` en la UI, bloqueando únicamente la extracción por `yt-dlp` mientras se actualiza y ofreciendo un botón de reinicio inmediato al concluir.
  - *Why*: Permite que el usuario escuche su música local desde el primer segundo sin bloqueos, pero evita que intente usar un binario `yt-dlp` a medio actualizar o desactualizado.
  - *Alternatives Considered*: Mostrar un diálogo modal bloqueante al abrir la app (descartado porque interrumpe al usuario que solo entra a escuchar su música local).

- **Decision 2: Procesamiento de Audio 16D y Claridad Vocal 100% en ISO C++20**
  - *Chosen Approach*: Implementar ambos algoritmos en coma flotante de 64 bits dentro del motor nativo C++20 existente, integrados en el ciclo de *Buffer Flushing* (`flushDspBuffers()`) para erradicar cualquier chasquido al activar/desactivar o cambiar de canción.
  - *Why*: Garantiza latencia cero, consumo mínimo de CPU y compatibilidad idéntica en arquitecturas de 32 y 64 bits desde Android 8.0 (API 26).
  - *Alternatives Considered*: Usar filtros `AudioEffect` de Java/Android (descartado porque carecen de separación *Mid-Side* y paneo multi-órbita, además de causar pausas o ruidos en distintos fabricantes).

- **Decision 3: Ubicación Estratégica en la Hoja Modal Unificada**
  - *Chosen Approach*: Integrar el selector **8D / 16D** en la pestaña espacial y el **Clarificador de Voces** tanto en **Ecualizador** como en **Velocidad/Voz**, sin añadir logotipos animados ni pantallas aisladas.
  - *Why*: Respeta la regla arquitectónica de mantener todos los efectos en la hoja modal unificada y coloca cada herramienta exactamente donde el usuario espera encontrarla.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       AURA MUSIC APPLICATION START                      │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│            ORQUESTADOR DE PAQUETES OTA (Background & Reactivo)          │
│  ┌─────────────────────────┐    ┌────────────────────────────────────┐  │
│  │ Estados Reactivos:      │    │ Notificación Nativa del Sistema:   │  │
│  │ • CheckingPackages      │───▶│ "Verificando paquetes..."          │  │
│  │ • Downloading(0..100%)  │───▶│ "Descargando actualización (X%)"   │  │
│  │ • RestartRequired       │───▶│ "Paquetes listos • Toca reiniciar" │  │
│  │ • UpToDate / Idle       │    └────────────────────────────────────┘  │
│  └────────────┬────────────┘                                            │
└───────────────┼─────────────────────────────────────────────────────────┘
                │
      ┌─────────┴────────────────────────────────────┐
      ▼                                              ▼
┌─────────────────────────────────────┐  ┌────────────────────────────────┐
│   BANNER NO INTRUSIVO EN INTERFAZ   │  │  BLOQUEO PREVENTIVO EN YT-DLP  │
│ • Chip superior con progreso en vivo│  │ • Deshabilita extracción yt-dlp│
│ • Botón "Aplicar y Reiniciar Ahora" │  │   mientras descarga o espera   │
│   al terminar la descarga SHA-256   │  │   reinicio de aplicación       │
└─────────────────────────────────────┘  └────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                MOTOR DSP NATIVO EN ISO C++20 (64-BIT PCM)               │
│                                                                         │
│  Entrada PCM 16-bit ──▶ [Ecualizador 10 Bandas + Bass Boost]            │
│                     ──▶ [Clarificador de Voces HD (Mid-Side + Biquads)] │
│                     ──▶ [Motor Espacial: Modo 8D ó Modo 16D Multi-Capa] │
│                     ──▶ [Crossfeed Auriculares + Balance Estéreo L/R]   │
│                     ──▶ [Reverb Schroeder + Limitador Suave] ──▶ Salida │
└─────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

1. **Estado de Verificación y Actualización de Paquetes (`PackageUpdateState`)**:
   - `Idle`: Sin actividad visible.
   - `Checking`: Verificando versión instalada de `yt-dlp` y paquetes nativos contra la última versión oficial.
   - `Downloading(progressPercent: Int, downloadedKb: Long, totalKb: Long)`: Descargando paquete con verificación `SHA-256` en curso; activa el bloqueo preventivo de descargas por `yt-dlp`.
   - `ReadyToApply(newVersion: String)`: Descarga completada y verificada; mantiene el aviso no intrusivo recomendando salir o pulsar **"Aplicar y Reiniciar Ahora"** para refrescar toda la app.
   - `UpToDate(version: String)`: Confirmación breve que se oculta automáticamente.

2. **Configuración de Claridad Vocal y Audio 16D**:
   - **Clarificador de Voces (`VocalClarityConfig`)**:
     - `enabled: Boolean`: Activa o desactiva el realce vocal en tiempo real.
     - `clarityStrength: Float`: Nivel de clarificación de `0.0f` a `1.0f` (`0%` a `100%`).
     - `mode: Int`: `0 = Natural`, `1 = Nítido HD`, `2 = Enfoque Vocal (Atenuación Instrumental)`.
   - **Extensión Espacial 8D / 16D (`Spatial8DConfig`)**:
     - `is16DMode: Boolean`: Conmuta entre órbita simple **8D** y doble órbita multi-capa **16D**.
     - Reutiliza `orbitSpeedSeconds`, `spatialIntensity` y `roomDepth` con limpieza atómica en `flushDspBuffers()`.

### Interactive Component & State Mapping

- **Notificación y Banner de Paquetes**:
  - Al iniciar `AuraApplication`, el actualizador emite `Checking` y publica la notificación nativa en la barra de estado.
  - Durante la lectura del stream HTTP de GitHub Releases, calcula el porcentaje real a partir de `contentLength` y actualiza tanto la barra de la notificación nativa como el indicador compacto en la pantalla principal.
  - Al pulsar **"Aplicar y Reiniciar Ahora"** (en el banner de la app o en la notificación final), se guarda el estado y se reinicia limpiamente `MainActivity` y el proceso para que todos los módulos utilicen el nuevo paquete de inmediato.
- **Bloqueo Temporal en `DownloadFromLinkDialog`**:
  - Observa `packageUpdateState`: si está en `Checking`, `Downloading` o `ReadyToApply` y el motor seleccionado es `yt-dlp`, muestra una tarjeta de aviso y bloquea el botón de inspección/descarga por `yt-dlp` (permitiendo aplicar el reinicio con un toque desde el propio diálogo si ya terminó de descargarse).
- **Controles Estratégicos de Audio en `AudioEffectsBottomSheet`**:
  -En la pestaña **Ecualizador** y en **Velocidad/Voz**, el control de **Clarificar Voces** actualiza vía JNI los filtros C++20 en tiempo real sin cortes ni logos animados.
  - En la pestaña **Audio 8D / 16D**, el selector segmentado alterna entre **Audio 8D** y **Audio 16D** en tiempo real sobre el flujo PCM de ExoPlayer.
