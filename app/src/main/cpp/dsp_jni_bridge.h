// Puente JNI del motor DSP de Aura Music.
// Rol arquitectónico: submódulo de `auramusic_dsp.h`.
// Concentra todas las declaraciones JNI consumidas por
// `NativeAudioEngine` / `NativeAudioProcessor`, sin lógica de audio.
#ifndef AURAMUSIC_DSP_JNI_BRIDGE_H
#define AURAMUSIC_DSP_JNI_BRIDGE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jstring JNICALL
Java_com_example_playback_NativeAudioEngine_getNativeEngineInfo(JNIEnv* env, jobject thiz);

JNIEXPORT jboolean JNICALL
Java_com_example_playback_NativeAudioEngine_isDspActive(JNIEnv* env, jobject thiz);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeInitDsp(JNIEnv* env, jobject thiz, jint sampleRate, jint channels);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeFlushDspBuffers(JNIEnv* env, jobject thiz);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetBandGain(JNIEnv* env, jobject thiz, jint bandIndex, jfloat gainDb);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetBassBoost(JNIEnv* env, jobject thiz, jfloat strength);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetDspEnabled(JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeProcessPcmBuffer(JNIEnv* env, jobject thiz, jobject byteBuffer, jint offset, jint length);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeAnalyzePcmBufferReadOnly(JNIEnv* env, jobject thiz, jobject byteBuffer, jint offset, jint length);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDEnabled(JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightD16DMode(JNIEnv* env, jobject thiz, jboolean is16DMode);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDOrbitSpeed(JNIEnv* env, jobject thiz, jfloat speedSeconds);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDSpatialIntensity(JNIEnv* env, jobject thiz, jfloat intensity);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetEightDRoomDepth(JNIEnv* env, jobject thiz, jfloat depth);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetVocalClarityParameters(JNIEnv* env, jobject thiz, jboolean enabled, jfloat strength);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetCrossfeedEnabled(JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetCrossfeedHeadphonesConnected(JNIEnv* env, jobject thiz, jboolean connected);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetCrossfeedStrength(JNIEnv* env, jobject thiz, jint strengthMode);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetBalanceEnabled(JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetStereoBalance(JNIEnv* env, jobject thiz, jfloat balance);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetReverbParameters(JNIEnv* env, jobject thiz, jboolean enabled, jfloat roomSize, jint decayMs, jfloat levelDb);

JNIEXPORT jfloat JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetAudioIntensity(JNIEnv* env, jobject thiz);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetVisualizerBands(JNIEnv* env, jobject thiz, jfloatArray outBands);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetVolumeNormalization(JNIEnv* env, jobject thiz, jboolean enabled, jfloat targetLufs, jint mode);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetDjAutomixTransition(JNIEnv* env, jobject thiz, jboolean enabled, jfloat progress);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetBitPerfectMode(JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT jboolean JNICALL
Java_com_example_playback_NativeAudioEngine_nativeIsBitPerfectMode(JNIEnv* env, jobject thiz);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetLowLatencyMode(JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetUsbDacExclusiveMode(JNIEnv* env, jobject thiz, jboolean enabled);

JNIEXPORT void JNICALL
Java_com_example_playback_NativeAudioEngine_nativeSetHiResTargetMode(JNIEnv* env, jobject thiz, jint mode);

JNIEXPORT jfloat JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetEstimatedLatencyMs(JNIEnv* env, jobject thiz);

JNIEXPORT jint JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetActiveSampleRate(JNIEnv* env, jobject thiz);

JNIEXPORT jint JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetActiveBitDepth(JNIEnv* env, jobject thiz);

JNIEXPORT jint JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetFramesPerBurst(JNIEnv* env, jobject thiz);

JNIEXPORT jint JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetHardwareSampleRate(JNIEnv* env, jobject thiz);

JNIEXPORT jstring JNICALL
Java_com_example_playback_NativeAudioEngine_nativeGetBitPerfectStatusSummary(JNIEnv* env, jobject thiz);

#ifdef __cplusplus
}
#endif

#endif // AURAMUSIC_DSP_JNI_BRIDGE_H
