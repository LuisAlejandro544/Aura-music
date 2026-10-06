#include "auramusic_dsp.h"
#include <android/log.h>

#define LOG_TAG "AuraMusicDSP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static aura::dsp::NativeDspEngine sDspEngine;

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_example_playback_NativeAudioEngine_getNativeEngineInfo(JNIEnv* env, jobject /* thiz */) {
    LOGI("Aura Music C++20 10-Band DSP consultado");
    return env->NewStringUTF(sDspEngine.getEngineInfo().c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_example_playback_NativeAudioEngine_isDspActive(JNIEnv* /* env */, jobject /* thiz */) {
    return sDspEngine.isEnabled() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeInitDsp(JNIEnv* /* env */, jobject /* thiz */, jint sampleRate, jint channels) {
    LOGI("Aura Music DSP Inicializado: Fs=%d, Canales=%d", sampleRate, channels);
    sDspEngine.init(sampleRate, channels);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetBandGain(JNIEnv* /* env */, jobject /* thiz */, jint bandIndex, jfloat gainDb) {
    sDspEngine.setBandGain(bandIndex, static_cast<double>(gainDb));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetBassBoost(JNIEnv* /* env */, jobject /* thiz */, jfloat strength) {
    sDspEngine.setBassBoost(static_cast<double>(strength));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetDspEnabled(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled) {
    sDspEngine.setEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeProcessPcmBuffer(JNIEnv* env, jobject /* thiz */, jobject byteBuffer, jint offset, jint length) {
    if (!byteBuffer || length <= 0) return;

    void* directBuffer = env->GetDirectBufferAddress(byteBuffer);
    if (!directBuffer) return;

    auto* pcm16Ptr = reinterpret_cast<int16_t*>(static_cast<uint8_t*>(directBuffer) + offset);
    size_t sampleCount = static_cast<size_t>(length) / sizeof(int16_t);

    if (sampleCount > 0) {
        std::span<int16_t> sampleSpan(pcm16Ptr, sampleCount);
        sDspEngine.processPcm16(sampleSpan);
    }
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDEnabled(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled) {
    LOGI("Aura Music Audio 8D/16D: %s", enabled ? "ACTIVADO" : "DESACTIVADO");
    sDspEngine.setEightDEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightD16DMode(JNIEnv* /* env */, jobject /* thiz */, jboolean is16DMode) {
    LOGI("Aura Music Modo Espacial: %s", is16DMode ? "16D Multi-Orbita" : "8D Clasico");
    sDspEngine.setEightD16DMode(is16DMode == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDOrbitSpeed(JNIEnv* /* env */, jobject /* thiz */, jfloat speedSeconds) {
    sDspEngine.setEightDOrbitSpeed(static_cast<double>(speedSeconds));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDSpatialIntensity(JNIEnv* /* env */, jobject /* thiz */, jfloat intensity) {
    sDspEngine.setEightDSpatialIntensity(static_cast<double>(intensity));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDRoomDepth(JNIEnv* /* env */, jobject /* thiz */, jfloat depth) {
    sDspEngine.setEightDRoomDepth(static_cast<double>(depth));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetVocalClarityParameters(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled, jfloat strength) {
    LOGI("Aura Music Clarificador Vocal HD: %s (%.0f%%)", enabled ? "ACTIVADO" : "DESACTIVADO", strength * 100.0f);
    sDspEngine.setVocalClarityParameters(enabled == JNI_TRUE, static_cast<double>(strength));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetCrossfeedEnabled(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled) {
    LOGI("Aura Music Crossfeed: %s", enabled ? "ACTIVADO" : "DESACTIVADO");
    sDspEngine.setCrossfeedEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetCrossfeedHeadphonesConnected(JNIEnv* /* env */, jobject /* thiz */, jboolean connected) {
    sDspEngine.setCrossfeedHeadphonesConnected(connected == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetCrossfeedStrength(JNIEnv* /* env */, jobject /* thiz */, jint strengthMode) {
    sDspEngine.setCrossfeedStrength(strengthMode);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetBalanceEnabled(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled) {
    sDspEngine.setBalanceEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetStereoBalance(JNIEnv* /* env */, jobject /* thiz */, jfloat balance) {
    sDspEngine.setStereoBalance(static_cast<double>(balance));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetReverbParameters(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled, jfloat roomSize, jint decayMs, jfloat levelDb) {
    sDspEngine.setReverbParameters(enabled == JNI_TRUE, static_cast<float>(roomSize), static_cast<int>(decayMs), static_cast<float>(levelDb));
}

JNIEXPORT jfloat JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetAudioIntensity(JNIEnv* /* env */, jobject /* thiz */) {
    return sDspEngine.getAudioIntensity();
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetVisualizerBands(JNIEnv* env, jobject /* thiz */, jfloatArray outBands) {
    if (!outBands) return;
    jsize len = env->GetArrayLength(outBands);
    if (len <= 0) return;

    jfloat* elements = env->GetFloatArrayElements(outBands, nullptr);
    if (elements) {
        std::span<float> span(elements, static_cast<size_t>(len));
        sDspEngine.getVisualizerBands(span);
        env->ReleaseFloatArrayElements(outBands, elements, 0);
    }
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeFlushDspBuffers(JNIEnv* /* env */, jobject /* thiz */) {
    sDspEngine.flushDspBuffers();
}

}


