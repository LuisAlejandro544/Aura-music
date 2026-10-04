# Plan de Implementación: Color Dinámico del Visualizador, Botón Anterior en MiniPlayer, Colas Contextuales y Suite Reverb

Mejoras avanzadas para Aura Music basadas en la interacción del usuario: visualizador de ondas con gradiente dinámico sincronizado al Video Canvas, botón de canción anterior en el Mini Reproductor (sustituyendo el atajo de EQ), colas de reproducción automáticas contextuales por Playlist/Álbum/Artista, y un sistema integral de reverberación acústica ambiental con presets y personalización libre.

---

## Decisiones Críticas y Preferencias del Usuario

> [!IMPORTANT]
> Confirmadas por el usuario a través de la fase de consulta interactiva:
> - **Estilo visual del visualizador de ondas**: Degradado vertical fluido (`Brush.verticalGradient`) que combina suavemente el color primario (`animatedPrimary`) en la parte superior y secundario (`animatedSecondary`) en la base, extraídos en tiempo real del Video Canvas o carátula (eliminando el cyan estático fijo).
> - **Control de Reverb**: Modelo híbrido con presets acústicos inmediatos (*Estudio*, *Sala Mediana*, *Club Acústico*, *Gran Hall*, *Catedral*, *Eco Espacial*) combinados con controles deslizantes de ajuste fino para personalización libre (*Tamaño de Sala*, *Tiempo de Decaimiento / Resonancia* y *Nivel de Mezcla Reverb*).
> - **Reemplazo en el Mini Reproductor**: Se elimina el botón de acceso directo al ecualizador del MiniPlayer y se sustituye por el botón para regresar a la pista anterior (**Skip Previous** / ⏮️), conformando la botonera ergonómica estándar: Anterior, Play/Pausa y Siguiente.
> - **Colas de Reproducción Contextuales**: Al pulsar una canción desde una Playlist, Álbum, Artista o lista filtrada, la cola de reproducción activa (`audioPlayer.queue`) se asigna estrictamente a esa lista de canciones completa, permitiendo navegar y saltar únicamente entre las pistas de ese contexto.

---

## 1. Visión General y Concepto Central

- **Visualizador Armonizado**: Las barras de onda espectrales reaccionan cromáticamente con un degradado vertical que refleja las luces y tonalidades del video en segundo plano.
- **Control Ergonómico en MiniPlayer**: Navegación fluida de pistas (Anterior / Pausa / Siguiente) directamente desde cualquier pantalla sin necesidad de expandir Now Playing.
- **Consistencia en Colas**: La experiencia de reproducción respeta las selecciones del usuario en listas de reproducción o categorías temáticas.
- **Acústica Envolvente con Reverb**: Procesamiento acústico ambiental que añade profundidad espacial y resonancia a la música, accesible en la hoja de efectos de audio unificada.

---

## 2. Experiencia de Usuario y Diseño Visual

### Flujos Clave
1. **Visualizador Reactivo**: Durante la reproducción de Video Canvas o canciones con carátula viva, el visualizador modula sus 28 barras con un degradado vertical de `animatedPrimary` hacia `animatedSecondary`, sincronizado al muestreo de 2.5s.
2. **Navegación Rápida en Mini Reproductor**: El usuario toca ⏮️ en el mini reproductor -> La app reproduce la canción anterior de la lista sin abrir Now Playing.
3. **Reproducción desde Playlists/Álbumes**: El usuario entra a la playlist "Tus Me Gusta" o a un álbum de 10 canciones y pulsa la pista 3 -> La cola de Now Playing muestra exactamente esas 10 canciones y avanza secuencialmente dentro de ese grupo.
4. **Pestaña de Reverb & Espacialidad**: En el modal de efectos de audio (`AudioEffectsBottomSheet`), se añade la pestaña "Reverb", permitiendo activar presets con un toque (p. ej. *Catedral*) o ajustar manualmente el tamaño de sala y decay time.

---

## 3. Decisiones de Producto y Arquitectura Técnica

### Decisión 1: Visualizador con Degradado Vertical Primario a Secundario
- **Estrategia**: Actualizar `AudioVisualizer.kt` para recibir `customPrimaryColor` y `customSecondaryColor`. En `NowPlayingScreen.kt`, inyectar `animatedPrimary` y `animatedSecondary`.
- **Por qué**: Sustituye el valor fijo `MaterialTheme.colorScheme.secondary` (cyan) por la paleta real del video en reproducción, logrando una estética 100% coordinada.

### Decisión 2: Cola Contextual Fiel en `MusicViewModel.playTrack`
- **Estrategia**: Cambiar la condición en `MusicViewModel.playTrack(track, fromList)` de `if (fromList != null && fromList.size > 1)` a `if (!fromList.isNullOrEmpty()) fromList`.
- **Por qué**: Asegura que cualquier lista proporcionada (incluso de 1 sola canción o listas de álbumes/playlists) defina la cola activa en lugar de rellenarla con toda la biblioteca global.

### Decisión 3: Arquitectura Híbrida de Reverb (Presets + Ajuste Libre)
- **Estrategia**: Crear `ReverbConfig` en `model/AudioEffectsConfig.kt`, gestionarlo en `AudioEffectManager.kt` mediante `android.media.audiofx.PresetReverb` / `EnvironmentalReverb` y algoritmos de reverberación en `NativeAudioEngine` / C++20, e integrarlo en `ReverbTabContent.kt`.
- **Por qué**: Ofrece a usuarios casuales la inmediatez de presets y a audiófilos la libertad de esculpir su propia acústica espacial.

---

## 4. Diagrama de Arquitectura y Componentes

```
┌────────────────────────────────────────────────────────┐
│                   AuraMusicApp (UI)                    │
│   ┌────────────────────────────────────────────────┐   │
│   │ MiniPlayer: [⏮️ Anterior] [⏯️ Play] [⏭️ Sig]    │   │
│   └───────────────────────┬────────────────────────┘   │
│                           │ onSkipPrevious()           │
│                           ▼                            │
│   ┌────────────────────────────────────────────────┐   │
│   │ AudioVisualizer: Brush.verticalGradient        │   │
│   │ (animatedPrimary -> animatedSecondary)         │   │
│   └────────────────────────────────────────────────┘   │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                    MusicViewModel                      │
│   playTrack(track, fromList) -> setContextualQueue()   │
│   setReverbPreset() / setReverbCustomParameters()      │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│           AudioEffectManager / Native DSP              │
│   Equalizer 10 Bandas + Audio 8D + Reverb Híbrido     │
│   (Presets: Hall, Club, Room + Custom Decay / Size)    │
└────────────────────────────────────────────────────────┘
```

---

## 5. Plan de Tareas de Implementación

1. **`AudioVisualizer.kt` & `NowPlayingScreen.kt`**:
   - Modificar `AudioVisualizer` para admitir `customPrimaryColor` y `customSecondaryColor`.
   - Conectar `animatedPrimary` y `animatedSecondary` en `NowPlayingScreen`, logrando el degradado vertical dinámico coordinado con el Video Canvas.
2. **`MiniPlayer.kt` & `MainActivity.kt`**:
   - Reemplazar el botón de ecualizador por el botón de pista anterior (`Icons.Default.SkipPrevious`).
   - Vincular `onSkipPrevious = { viewModel.playPrevious() }` en `MainActivity.kt`.
3. **`MusicViewModel.kt`**:
   - Corregir `playTrack(track, fromList)` para priorizar fielmente la lista contextual provista (`fromList`).
4. **Suite de Reverb (`model`, `playback`, `ui`)**:
   - Crear modelo `ReverbConfig` (enum de presets y parámetros libres: `roomSize`, `decayMs`, `reverbLevel`).
   - Implementar integración de reverberación en `AudioEffectManager.kt`.
   - Exponer estados y métodos en `MusicViewModel.kt`.
   - Crear `ReverbTabContent.kt` y vincular la pestaña en `AudioEffectsBottomSheet.kt`.
5. **Verificación y Documentación**:
   - Compilar con `compile_applet` y ejecutar pruebas unitarias con Gradle (`:app:testDebugUnitTest`).
   - Sincronizar los 5 archivos Markdown en español (`README.md`, `ROADMAP.md`, `STRUCTURE.md`, `AI_CONTEXT.md`, `AGENTS.md`).
