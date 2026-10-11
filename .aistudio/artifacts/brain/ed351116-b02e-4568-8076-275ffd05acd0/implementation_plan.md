# Plan de Implementación: Smart AutoMix DJ, Crossfade Simultáneo 100% y Corrección de Indicador de Red en Pistas Locales

## 1. Contexto y Objetivos

1. **Protección de Marca e Identidad Propia (`Smart AutoMix DJ`)**:
   - Eliminar todas las menciones a marcas registradas de terceros (*Apple Music*, *Spotify*) en la interfaz de usuario (`TransitionsTabContent`, `NowPlayingDesignMode`, `NowPlayingDesignSelectorDialog`, `PlayerDesignSettingsContent`) y en los comentarios del código fuente, renombrando la función a **Smart AutoMix DJ** y el nivel de normalización `-14 LUFS` a **Estándar (-14 LUFS)**.
2. **Cruce de Volumen Progresivo y 100% Simultáneo (Canción A 100% → 0% y Canción B 0% → 100%)**:
   - Actualmente, `AuraAudioPlayer` aplica un *fade-out* prematuro sobre la Canción A (`applyFadeOut`) antes de que comience el cruce, o la transfiere con el volumen ya mermado, provocando que la caída de la Canción A y la subida de la Canción B no se sientan como dos rampas continuas y simultáneas de `100% → 0%` y `0% → 100%`.
   - Se ajustará el motor **Dual-Deck Crossfade** (`AudioFadeController` y `AuraAudioPlayer`) para que al llegar al punto de transición, la Canción A comience exactamente al **100%** de su volumen base y baje progresivamente hasta **0%** al mismo tiempo exacto en que la Canción B arranca en **0%** y sube progresivamente hasta **100%** durante toda la ventana de superposición.
3. **Eliminación del Indicador de "Buscando Internet / Cargando Stream" en Canciones Locales**:
   - Actualmente, `Player.STATE_BUFFERING` en `AuraAudioPlayer`, `BackgroundVideoLoadingState` en `BackgroundVideoPlayer` y el estado global `isResolvingStreamGlobal` activan los indicadores visuales (`isLoadingOrBuffering` / `isWaitingForNetwork`) sin verificar si la pista activa (`currentTrack`) o su `videoUri` son archivos locales en el almacenamiento del teléfono.
   - Se blindarán `AuraAudioPlayer`, `BackgroundVideoPlayer`, `AuraMusicAppContent` y `NowPlayingScreen` para que las canciones y videos locales jamás muestren spinners ni avisos de `"Cargando stream..."` o `"Esperando red..."`, restringiendo dichos indicadores exclusivamente a flujos remotos (`isStreamingTrack` o URLs `http/https`).

---

## 2. Cambios Propuestos por Archivo

### A. Limpieza de Marcas Registradas en UI y Modelos
- **`app/src/main/java/com/example/ui/components/audioeffects/TransitionsTabContent.kt`**:
  - Cambiar `"Smart AutoMix (Estilo Apple Music)"` por `"Smart AutoMix DJ"`.
  - Cambiar el botón de sonoridad `-14 LUFS` de `"Spotify"` a `"Estándar"` y su descripción inferior a `"Nivel estándar de plataforma (-14 LUFS): balance ideal entre dinámica y volumen constante."`.
- **`app/src/main/java/com/example/model/NowPlayingDesignMode.kt`**:
  - Cambiar la etiqueta de `CINEMATIC_CANVAS` de `"Cinemático Canvas (Spotify)"` a `"Cinemático Canvas"`.
- **`app/src/main/java/com/example/ui/screens/nowplaying/components/NowPlayingDesignSelectorDialog.kt`** y **`app/src/main/java/com/example/ui/screens/settings/components/appearance/PlayerDesignSettingsContent.kt`**:
  - Actualizar comentarios y textos descriptivos para referirse a `"Modo Cinemático Canvas"` y `"Smart AutoMix DJ"` sin nombrar marcas comerciales externas.
- **Archivos de código y C++ (`AuraAudioPlayer.kt`, `AudioEffectsConfig.kt`, `dsp_filters.h`, `auramusic_dsp.h`, etc.)**:
  - Limpiar los comentarios internos que mencionen a dichas marcas registradas (preservando intactos los `User-Agent` técnicos de red como `AppleWebKit/537.36` necesarios para las peticiones HTTP).

### B. Rampa Simultánea 100% → 0% (Canción A) y 0% → 100% (Canción B)
- **`app/src/main/java/com/example/playback/controllers/AudioFadeController.kt`**:
  - Actualizar `startDualDeckCrossfade` para que cuando comienza la transición superpuesta entre `outgoingPlayer` (Canción A) e `incomingPlayer` (Canción B):
    - `outgoingPlayer` inicie en el **100%** del volumen base (`baseVolume`) y descienda progresivamente paso a paso (`1.0f - t`) hasta `0.0f` al concluir la transición.
    - `incomingPlayer` inicie en **0%** (`0.0f`) y ascienda progresivamente paso a paso (`t`) hasta el **100%** (`baseVolume`) exactamente en el mismo intervalo de tiempo.
- **`app/src/main/java/com/example/playback/AuraAudioPlayer.kt`**:
  - Eliminar la atenuación anticipada (`fadeController.applyFadeOut`) previa al disparo del Dual-Deck en el bucle de seguimiento (`startProgressTracking`), de modo que la Canción A no baje su volumen antes de que la Canción B empiece a sonar.
  - Al transferir la Canción A al `outgoingExoPlayer` (`outDeck`), conservar el `100%` del volumen (`fadeController.getVolume()`) y sincronizar el arranque del fundido cruzado cuando ambos reproductores están listos, logrando un cruce limpio y 100% simultáneo.

### C. Supresión Definitiva del Indicador de Red / Buffering en Canciones Locales
- **`app/src/main/java/com/example/playback/AuraAudioPlayer.kt`**:
  - En `onPlaybackStateChanged(Player.STATE_BUFFERING)`, activar `_isBuffering.value = true` **únicamente** si la pista actual es de streaming (`_currentTrack.value?.isStreamingTrack == true`) o su URI es remota (`http://` o `https://`). Para pistas locales en disco, `_isBuffering.value` e `_isNetworkStalled.value` se mantendrán siempre en `false`.
- **`app/src/main/java/com/example/ui/components/artwork/BackgroundVideoPlayer.kt`**:
  - Verificar si `videoUriString` es un archivo local (no inicia con `http://` ni `https://`). Si es un video local almacenado en el dispositivo, nunca publicar `isVideoBuffering = true` ni `isVideoNetworkStalled = true` en `BackgroundVideoLoadingState`.
- **`app/src/main/java/com/example/ui/AuraMusicAppContent.kt`** y **`app/src/main/java/com/example/ui/screens/nowplaying/NowPlayingScreen.kt`**:
  - Restringir las banderas `isLoadingOrBuffering`, `isVideoLoading` e `isWaitingForNetwork` pasadas a `MiniPlayer`, `NowPlayingScreen`, `NowPlayingArtworkCard`, `NowPlayingPlaybackControls` y `NowPlayingCinematicLayout` para que evalúen `effectiveTrack?.isStreamingTrack == true`, garantizando que en canciones locales jamás aparezca el indicador de buscando internet o cargando stream.

---

## 3. Verificación
- Compilar la aplicación mediante `compile_applet` para verificar que no existan errores de sintaxis, tipos ni referencias en Kotlin ni C++20.
- Confirmar mediante búsqueda (`grep`) que no queden menciones visibles a marcas registradas en la interfaz.
