#ifndef AURAMUSIC_DSP_H
#define AURAMUSIC_DSP_H

/**
 * Aura Music - Motor Nativo de Procesamiento Digital de Señales (DSP)
 * Estándar: ISO C++20
 *
 * Fachada modular (<100 líneas):
 * - `dsp_engine.h`: clase `aura::dsp::NativeDspEngine` (EQ 10 bandas,
 *   Bass Boost, 8D/16D, Crossfeed, Reverb, Balance, Normalización,
 *   DJ Automix, Bit-Perfect y Buffer Flushing).
 * - `dsp_visualizer.h`: espectro de 28 bandas e intensidad RMS.
 * - `dsp_jni_bridge.h`: declaraciones JNI para NativeAudioEngine.
 * Los submódulos de filtros/espacial/crossfeed/reverb/bitperfect
 * se incluyen transitivamente desde `dsp_engine.h`.
 */

#include "dsp_engine.h"
#include "dsp_jni_bridge.h"

#endif // AURAMUSIC_DSP_H
