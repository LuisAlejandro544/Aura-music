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
Java_com_example_playback_NativeAudioEngine_initDsp(JNIEnv* /* env */, jobject /* thiz */, jint sampleRate, jint channels) {
    LOGI("Aura Music DSP Inicializado: Fs=%d, Canales=%d", sampleRate, channels);
    sDspEngine.init(sampleRate, channels);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_setBandGain(JNIEnv* /* env */, jobject /* thiz */, jint bandIndex, jfloat gainDb) {
    sDspEngine.setBandGain(bandIndex, static_cast<double>(gainDb));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_setBassBoost(JNIEnv* /* env */, jobject /* thiz */, jfloat strength) {
    sDspEngine.setBassBoost(static_cast<double>(strength));
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_setDspEnabled(JNIEnv* /* env */, jobject /* thiz */, jboolean enabled) {
    sDspEngine.setEnabled(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_processPcmBuffer(JNIEnv* env, jobject /* thiz */, jobject byteBuffer, jint offset, jint length) {
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

}
