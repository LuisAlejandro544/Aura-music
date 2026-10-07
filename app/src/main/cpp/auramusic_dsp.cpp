#include "auramusic_dsp.h"
#include <android/log.h>
#include <mutex>

#define LOG_TAG "AuraMusicDSP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static aura::dsp::NativeDspEngine sDspEngine;
static std::mutex sDspMutex;

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_example_playback_NativeAudioEngine_getNativeEngineInfo(JNIEnv* env, jobject /* thiz */) {
    LOGI("Aura Music C++20 10-Band DSP consultado");
    std::lock_guard<std::mutex> lock(sDspMutex);
    return env->NewStringUTF(sDspEngine.getEngineInfo().c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_example_playback_NativeAudioEngine_isDspActive(JNIEnv* /* env */, jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    return sDspEngine.isEnabled() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeInitDsp(JNIEnv* /* env */, jobject /* thiz */, jint sampleRate, jint channels) {
    LOGI("Aura Music DSP Inicializado: Fs=%d, Canales=%d", sampleRate, channels);
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.init(sampleRate, channels);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetBandGain(JNIEnv* /* env */, jobject /* thiz */, jint bandIndex, jfloat gainDb) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setBandGain(bandIndex, static_cast<double>(gainDb));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetBassBoost(JNIEnv* /* env */, jobject /* thiz */, jfloat strength) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setBassBoost(static_cast<double>(strength));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetDspEnabled(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeProcessPcmBuffer(JNIEnv* env, jobject /* thiz */, jobject byteBuffer, jint offset, jint length) {
    if (!byteBuffer || offset < 0 || length <= 0) return;

    void* directBuffer = env->GetDirectBufferAddress(byteBuffer);
    if (!directBuffer) return;

    jlong capacity = env->GetDirectBufferCapacity(byteBuffer);
    if (capacity <= 0) return;

    jlong endOffset = static_cast<jlong>(offset) + static_cast<jlong>(length);
    if (static_cast<jlong>(offset) >= capacity || endOffset > capacity || endOffset < static_cast<jlong>(offset)) {
        LOGW("Intento de acceso fuera de limites en DirectByteBuffer bloqueado: offset=%d, length=%d, capacity=%lld",
             offset, length, static_cast<long long>(capacity));
        return;
    }

    auto* pcm16Ptr = reinterpret_cast<int16_t*>(static_cast<uint8_t*>(directBuffer) + offset);
    size_t sampleCount = static_cast<size_t>(length) / sizeof(int16_t);

    if (sampleCount > 0) {
        std::lock_guard<std::mutex> lock(sDspMutex);
        std::span<int16_t> sampleSpan(pcm16Ptr, sampleCount);
        sDspEngine.processPcm16(sampleSpan);
    }
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDEnabled(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled) {
    LOGI("Aura Music Audio 8D/16D: %s", enabled ? "ACTIVADO" : "DESACTIVADO");
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setEightDEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightD16DMode(JNIEnv* /* env */, jobject /* thiz */, jboolean is16DMode) {
    LOGI("Aura Music Modo Espacial: %s", is16DMode ? "16D Multi-Orbita" : "8D Clasico");
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setEightD16DMode(is16DMode == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDOrbitSpeed(JNIEnv* /* env */, jobject /* thiz */, jfloat speedSeconds) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setEightDOrbitSpeed(static_cast<double>(speedSeconds));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDSpatialIntensity(JNIEnv* /* env */, jobject /* thiz */, jfloat intensity) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setEightDSpatialIntensity(static_cast<double>(intensity));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDRoomDepth(JNIEnv* /* env */, jobject /* thiz */, jfloat depth) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setEightDRoomDepth(static_cast<double>(depth));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetVocalClarityParameters(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled, jfloat strength) {
    LOGI("Aura Music Clarificador Vocal HD: %s (%.0f%%)", enabled ? "ACTIVADO" : "DESACTIVADO", strength * 100.0f);
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setVocalClarityParameters(enabled == JNI_TRUE, static_cast<double>(strength));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetCrossfeedEnabled(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled) {
    LOGI("Aura Music Crossfeed: %s", enabled ? "ACTIVADO" : "DESACTIVADO");
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setCrossfeedEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetCrossfeedHeadphonesConnected(JNIEnv* /* env */, jobject /* thiz */, jboolean connected) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setCrossfeedHeadphonesConnected(connected == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetCrossfeedStrength(JNIEnv* /* env */, jobject /* thiz */, jint strengthMode) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setCrossfeedStrength(strengthMode);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetBalanceEnabled(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setBalanceEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetStereoBalance(JNIEnv* /* env */, jobject /* thiz */, jfloat balance) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setStereoBalance(static_cast<double>(balance));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetReverbParameters(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled, jfloat roomSize, jint decayMs, jfloat levelDb) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setReverbParameters(enabled == JNI_TRUE, static_cast<float>(roomSize), static_cast<int>(decayMs), static_cast<float>(levelDb));
}

JNIEXPORT jfloat JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetAudioIntensity(JNIEnv* /* env */, jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    return sDspEngine.getAudioIntensity();
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetVisualizerBands(JNIEnv* env, jobject /* thiz */, jfloatArray outBands) {
    if (!outBands) return;
    jsize len = env->GetArrayLength(outBands);
    if (len <= 0) return;

    jfloat* elements = env->GetFloatArrayElements(outBands, nullptr);
    if (elements) {
        {
            std::lock_guard<std::mutex> lock(sDspMutex);
            std::span<float> span(elements, static_cast<size_t>(len));
            sDspEngine.getVisualizerBands(span);
        }
        env->ReleaseFloatArrayElements(outBands, elements, 0);
    }
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeFlushDspBuffers(JNIEnv* /* env */, jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.flushDspBuffers();
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetVolumeNormalization(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled, jfloat targetLufs, jint mode) {
    LOGI("Aura Music Normalizacion de Volumen: %s (Target=%.1f LUFS, Mode=%d)", enabled ? "ACTIVADA" : "DESACTIVADA", targetLufs, mode);
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setVolumeNormalization(enabled == JNI_TRUE, static_cast<float>(targetLufs), static_cast<int>(mode));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetDjAutomixTransition(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled, jfloat progress) {
    std::lock_guard<std::mutex> lock(sDspMutex);
    sDspEngine.setDjAutomixTransition(enabled == JNI_TRUE, static_cast<float>(progress));
}

}


