#ifndef AURAMUSIC_DSP_FILTERS_H
#define AURAMUSIC_DSP_FILTERS_H

#include <array>
#include <cmath>
#include <algorithm>
#include <numbers>

namespace aura::dsp {

// Frecuencias ISO centrales para el ecualizador de 10 bandas
inline constexpr std::array<double, 10> EQ_FREQUENCIES_HZ = {
    31.25, 62.5, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0
};

// Filtro Bi-cuadrático (Biquad IIR) con cálculo paramétrico Peaking EQ
class BiquadPeakingFilter {
public:
    double b0{1.0}, b1{0.0}, b2{0.0};
    double a1{0.0}, a2{0.0};
    double x1{0.0}, x2{0.0};
    double y1{0.0}, y2{0.0};

    // Pone a cero los acumuladores de muestras previas (Buffer Flush)
    void reset() {
        x1 = x2 = y1 = y2 = 0.0;
    }

    // Calcula los coeficientes de filtro peaking según Robert Bristow-Johnson Audio EQ Cookbook
    void configurePeaking(double sampleRate, double centerFreqHz, double gainDb, double q = 1.414) {
        if (sampleRate <= 0.0 || centerFreqHz <= 0.0) return;
        
        // Limitar la frecuencia central por debajo de Nyquist (Fs / 2)
        double maxFreq = (sampleRate * 0.49);
        double f0 = std::clamp(centerFreqHz, 20.0, maxFreq);

        double A = std::pow(10.0, gainDb / 40.0);
        double omega = 2.0 * std::numbers::pi * f0 / sampleRate;
        double sinOmega = std::sin(omega);
        double cosOmega = std::cos(omega);
        double alpha = sinOmega / (2.0 * q);

        double a0 = 1.0 + (alpha / A);
        b0 = (1.0 + alpha * A) / a0;
        b1 = (-2.0 * cosOmega) / a0;
        b2 = (1.0 - alpha * A) / a0;
        a1 = (-2.0 * cosOmega) / a0;
        a2 = (1.0 - alpha / A) / a0;
    }

    // Filtro Paso-Bajos Bi-cuadrático (Biquad Low-Pass)
    void configureLowPass(double sampleRate, double cutoffHz, double q = 0.707) {
        if (sampleRate <= 0.0 || cutoffHz <= 0.0) return;
        double maxFreq = sampleRate * 0.49;
        double f0 = std::clamp(cutoffHz, 20.0, maxFreq);
        double omega = 2.0 * std::numbers::pi * f0 / sampleRate;
        double sinOmega = std::sin(omega);
        double cosOmega = std::cos(omega);
        double alpha = sinOmega / (2.0 * q);

        double a0 = 1.0 + alpha;
        b0 = ((1.0 - cosOmega) / 2.0) / a0;
        b1 = (1.0 - cosOmega) / a0;
        b2 = ((1.0 - cosOmega) / 2.0) / a0;
        a1 = (-2.0 * cosOmega) / a0;
        a2 = (1.0 - alpha) / a0;
    }

    // Procesa una muestra de audio flotante
    inline double process(double in) {
        double out = (b0 * in) + (b1 * x1) + (b2 * x2) - (a1 * y1) - (a2 * y2);
        x2 = x1;
        x1 = in;
        y2 = y1;
        y1 = out;
        return out;
    }
};

// Limitador suave tipo saturación de cinta para prevenir recortes digitales duros (Anti-clipping)
inline double softClip(double x) {
    if (std::isnan(x) || std::isinf(x)) return 0.0;
    if (x > 1.2) return 1.0;
    if (x < -1.2) return -1.0;
    return x - (x * x * x) / 6.0;
}

/**
 * Procesador C++20 de Clarificación de Voces (Vocal Clarity HD).
 * Extrae la componente central (Mid) donde reside la voz principal, atenúa el enmascaramiento
 * de graves fangosos (~220 Hz) y realza las bandas formantes de presencia (1.85 kHz) y
 * articulación/dicción (3.8 kHz) en 64-bit, con 3 modos estratégicos:
 * - Modo 0 (Natural): Realce vocal equilibrado conservando todo el cuerpo instrumental.
 * - Modo 1 (Nítido HD): Mayor articulación de consonantes y enfoque central definido.
 * - Modo 2 (Enfoque Vocal): Prioridad máxima a la voz atenuando el acompañamiento lateral (Side).
 */
class VocalClarityProcessor {
public:
    void init(int sampleRate) {
        mSampleRate = (sampleRate > 0) ? sampleRate : 44100;
        reconfigureFilters();
        reset();
    }

    void reset() {
        mMudCutFilter.reset();
        mPresenceFilter.reset();
        mArticulationFilter.reset();
    }

    void setEnabled(bool enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            if (!enabled) reset();
        }
    }

    [[nodiscard]] bool isEnabled() const { return mEnabled; }

    void setStrength(double strength) {
        double clamped = std::clamp(strength, 0.0, 1.0);
        if (std::abs(mStrength - clamped) > 0.001) {
            mStrength = clamped;
            reconfigureFilters();
        }
    }

    [[nodiscard]] double getStrength() const { return mStrength; }

    void setMode(int mode) {
        int clamped = std::clamp(mode, 0, 2);
        if (mMode != clamped) {
            mMode = clamped;
            reconfigureFilters();
        }
    }

    [[nodiscard]] int getMode() const { return mMode; }

    inline void processSample(double& sampleL, double& sampleR) {
        if (!mEnabled || mStrength <= 0.001) return;

        // Descomposición Mid-Side (Mid = centro vocal, Side = apertura instrumental)
        double mid = (sampleL + sampleR) * 0.5;
        double side = (sampleL - sampleR) * 0.5;

        // Procesar el canal central con limpieza de frecuencias fangosas y realce de formantes vocales
        double cleanMid = mMudCutFilter.process(mid);
        cleanMid = mPresenceFilter.process(cleanMid);
        cleanMid = mArticulationFilter.process(cleanMid);

        // Atenuación selectiva del canal lateral según el modo elegido para despejar la voz
        double sideScale = 1.0;
        if (mMode == 0) {
            sideScale = 1.0 - (0.10 * mStrength);
        } else if (mMode == 1) {
            sideScale = 1.0 - (0.24 * mStrength);
        } else {
            sideScale = 1.0 - (0.52 * mStrength);
        }

        double outSide = side * sideScale;
        sampleL = cleanMid + outSide;
        sampleR = cleanMid - outSide;
    }

private:
    void reconfigureFilters() {
        double modeMultiplier = (mMode == 0) ? 0.85 : ((mMode == 1) ? 1.15 : 1.35);
        double mudCutDb = -3.8 * mStrength * modeMultiplier;
        double presenceBoostDb = 5.6 * mStrength * modeMultiplier;
        double articulationBoostDb = 4.2 * mStrength * modeMultiplier;

        mMudCutFilter.configurePeaking(mSampleRate, 220.0, mudCutDb, 1.15);
        mPresenceFilter.configurePeaking(mSampleRate, 1850.0, presenceBoostDb, 1.25);
        mArticulationFilter.configurePeaking(mSampleRate, 3800.0, articulationBoostDb, 1.40);
    }

    int mSampleRate{44100};
    bool mEnabled{false};
    double mStrength{0.65};
    int mMode{1}; // 0 = Natural, 1 = Nítido HD, 2 = Enfoque Vocal

    BiquadPeakingFilter mMudCutFilter{};
    BiquadPeakingFilter mPresenceFilter{};
    BiquadPeakingFilter mArticulationFilter{};
};

/**
 * Procesador C++20 de Normalización de Volumen Inteligente (Loudness Normalizer estilo Spotify / EBU R128).
 * Mide continuamente la energía envolvente de la señal de audio (RMS aproximado) con seguimiento suave
 * (ataque de 120ms, decaimiento de 600ms) y calcula una ganancia de compensación dinámica hacia el nivel
 * objetivo (por defecto -14.0 LUFS / Spotify).
 *
 * Incluye filtro IIR de suavizado para evitar cambios abruptos ("pumping") y limitador suave softClip.
 */
class VolumeNormalizerProcessor {
public:
    void init(int sampleRate) {
        mSampleRate = (sampleRate > 0) ? sampleRate : 44100;
        reset();
    }

    void reset() {
        mCurrentRms = 0.15;
        mCurrentGain = 1.0;
    }

    void setEnabled(bool enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            if (!enabled) reset();
        }
    }

    [[nodiscard]] bool isEnabled() const { return mEnabled; }

    void setTargetLufs(float targetLufs) {
        mTargetLufs = std::clamp(targetLufs, -24.0f, -6.0f);
    }

    [[nodiscard]] float getTargetLufs() const { return mTargetLufs; }

    void setMode(int mode) {
        mMode = std::clamp(mode, 0, 2); // 0 = Sutil (-18 LUFS), 1 = Estándar Spotify (-14 LUFS), 2 = Alto (-11 LUFS)
        if (mMode == 0) mTargetLufs = -18.0f;
        else if (mMode == 1) mTargetLufs = -14.0f;
        else mTargetLufs = -11.0f;
    }

    [[nodiscard]] int getMode() const { return mMode; }

    inline void processSample(double& sampleL, double& sampleR) {
        if (!mEnabled) return;

        double sampleEnergy = 0.5 * (std::abs(sampleL) + std::abs(sampleR));
        double alpha = (sampleEnergy > mCurrentRms) ? 0.00015 : 0.00004;
        mCurrentRms = (1.0 - alpha) * mCurrentRms + alpha * sampleEnergy;

        double targetLinear = std::pow(10.0, (mTargetLufs + 3.0) / 20.0);
        double targetGain = targetLinear / std::max(0.04, mCurrentRms);
        targetGain = std::clamp(targetGain, 0.40, 2.50);

        mCurrentGain = 0.99985 * mCurrentGain + 0.00015 * targetGain;

        sampleL *= mCurrentGain;
        sampleR *= mCurrentGain;
    }

private:
    int mSampleRate{44100};
    bool mEnabled{false};
    float mTargetLufs{-14.0f}; // Spotify estándar
    int mMode{1};              // 0 = Sutil, 1 = Estándar, 2 = Alto
    double mCurrentRms{0.15};
    double mCurrentGain{1.0};
};

/**
 * Filtro C++20 de Curva de Ecualización DJ Automix (Crossfade con Curva en X).
 * Durante la transición de pistas, atenúa de forma paramétrica y progresiva los subgraves (<160 Hz)
 * y el brillo extremo (>9 kHz) de la canción que está finalizando, abriendo espacio acústico en la mezcla
 * para que la canción entrante entre con pegada limpia sin distorsión por choque de bajos.
 */
class DjAutomixFilter {
public:
    void init(int sampleRate) {
        mSampleRate = (sampleRate > 0) ? sampleRate : 44100;
        reconfigure();
        reset();
    }

    void reset() {
        mBassCutFilterL.reset();
        mBassCutFilterR.reset();
        mHighTameFilterL.reset();
        mHighTameFilterR.reset();
    }

    void setEnabled(bool enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            if (!enabled) reset();
        }
    }

    [[nodiscard]] bool isEnabled() const { return mEnabled; }

    void setTransitionProgress(float progress) {
        float clamped = std::clamp(progress, 0.0f, 1.0f);
        if (std::abs(mProgress - clamped) > 0.005f) {
            mProgress = clamped;
            reconfigure();
        }
    }

    [[nodiscard]] float getTransitionProgress() const { return mProgress; }

    inline void processSample(double& sampleL, double& sampleR) {
        if (!mEnabled || mProgress <= 0.005f) return;

        sampleL = mBassCutFilterL.process(sampleL);
        sampleR = mBassCutFilterR.process(sampleR);
        sampleL = mHighTameFilterL.process(sampleL);
        sampleR = mHighTameFilterR.process(sampleR);
    }

private:
    void reconfigure() {
        double bassGainDb = -12.0 * mProgress;
        double highGainDb = -6.0 * mProgress;

        mBassCutFilterL.configurePeaking(mSampleRate, 120.0, bassGainDb, 0.85);
        mBassCutFilterR.configurePeaking(mSampleRate, 120.0, bassGainDb, 0.85);
        mHighTameFilterL.configurePeaking(mSampleRate, 9500.0, highGainDb, 0.95);
        mHighTameFilterR.configurePeaking(mSampleRate, 9500.0, highGainDb, 0.95);
    }

    int mSampleRate{44100};
    bool mEnabled{false};
    float mProgress{0.0f};

    BiquadPeakingFilter mBassCutFilterL{};
    BiquadPeakingFilter mBassCutFilterR{};
    BiquadPeakingFilter mHighTameFilterL{};
    BiquadPeakingFilter mHighTameFilterR{};
};

} // namespace aura::dsp

#endif // AURAMUSIC_DSP_FILTERS_H
