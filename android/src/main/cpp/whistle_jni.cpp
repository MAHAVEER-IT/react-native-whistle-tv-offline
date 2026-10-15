#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <cstring>

#define TAG "WhistleJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#if HAVE_WHISTLE_ENGINE
#include "needle.h"
#endif

extern "C" {

// ============================================================================
// 1. Standalone Package JNI Bindings (com.pandivo.whistle.WhistleEngine)
// ============================================================================

JNIEXPORT jboolean JNICALL
Java_com_pandivo_whistle_WhistleEngine_nativeIsEngineSupported(JNIEnv *env, jclass clazz) {
#if HAVE_WHISTLE_ENGINE
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT jboolean JNICALL
Java_com_pandivo_whistle_WhistleEngine_nativeLoadModel(JNIEnv *env, jclass clazz, jbyteArray modelBytes) {
#if HAVE_WHISTLE_ENGINE
    if (!modelBytes) {
        LOGE("modelBytes is null");
        return JNI_FALSE;
    }
    jsize len = env->GetArrayLength(modelBytes);
    jbyte *bytes = env->GetByteArrayElements(modelBytes, nullptr);
    if (!bytes) {
        LOGE("Failed to get byte array elements");
        return JNI_FALSE;
    }
    
    int res = needle_load(reinterpret_cast<const unsigned char*>(bytes), static_cast<unsigned long long>(len));
    env->ReleaseByteArrayElements(modelBytes, bytes, JNI_ABORT);
    
    if (res < 0) {
        const char *err = needle_last_error();
        LOGE("needle_load failed: %s", err ? err : "unknown error");
        return JNI_FALSE;
    }
    LOGD("needle_load succeeded! Models loaded: %d", needle_models());
    return JNI_TRUE;
#else
    LOGD("Whistle engine stubbed on this ABI");
    return JNI_FALSE;
#endif
}

JNIEXPORT jstring JNICALL
Java_com_pandivo_whistle_WhistleEngine_nativeTranscribe(
    JNIEnv *env, jclass clazz,
    jfloatArray pcmSamples,
    jint sampleCount,
    jstring language,
    jstring keywords
) {
#if HAVE_WHISTLE_ENGINE
    if (!pcmSamples || sampleCount <= 0) {
        return env->NewStringUTF("{\"error\":\"Invalid PCM samples input\",\"isSuccess\":false}");
    }

    jfloat *samples = env->GetFloatArrayElements(pcmSamples, nullptr);
    if (!samples) {
        return env->NewStringUTF("{\"error\":\"Failed to get sample array elements\",\"isSuccess\":false}");
    }

    const char *langStr = language ? env->GetStringUTFChars(language, nullptr) : "en";
    const char *kwStr = keywords ? env->GetStringUTFChars(keywords, nullptr) : "";

    char resultBuf[2048] = {0};
    int status = needle_transcribe(
        samples,
        sampleCount,
        langStr,
        kwStr,
        resultBuf,
        sizeof(resultBuf) - 1
    );

    env->ReleaseFloatArrayElements(pcmSamples, samples, JNI_ABORT);
    if (language) env->ReleaseStringUTFChars(language, langStr);
    if (keywords) env->ReleaseStringUTFChars(keywords, kwStr);

    if (status < 0) {
        const char *err = needle_last_error();
        std::string errJson = std::string("{\"error\":\"") + (err ? err : "Inference failure") + "\",\"isSuccess\":false}";
        return env->NewStringUTF(errJson.c_str());
    }

    return env->NewStringUTF(resultBuf);
#else
    return env->NewStringUTF("{\"error\":\"Whistle engine not supported on this architecture\",\"isSuccess\":false}");
#endif
}

JNIEXPORT jstring JNICALL
Java_com_pandivo_whistle_WhistleEngine_nativeGetLastError(JNIEnv *env, jclass clazz) {
#if HAVE_WHISTLE_ENGINE
    const char *err = needle_last_error();
    return env->NewStringUTF(err ? err : "None");
#else
    return env->NewStringUTF("Architecture stub");
#endif
}

// ============================================================================
// 2. Backward Compatibility JNI Bindings (com.firetv.musicpoc.WhistleEngine)
// ============================================================================

JNIEXPORT jboolean JNICALL
Java_com_firetv_musicpoc_WhistleEngine_nativeIsEngineSupported(JNIEnv *env, jclass clazz) {
    return Java_com_pandivo_whistle_WhistleEngine_nativeIsEngineSupported(env, clazz);
}

JNIEXPORT jboolean JNICALL
Java_com_firetv_musicpoc_WhistleEngine_nativeLoadModel(JNIEnv *env, jclass clazz, jbyteArray modelBytes) {
    return Java_com_pandivo_whistle_WhistleEngine_nativeLoadModel(env, clazz, modelBytes);
}

JNIEXPORT jstring JNICALL
Java_com_firetv_musicpoc_WhistleEngine_nativeTranscribe(
    JNIEnv *env, jclass clazz,
    jfloatArray pcmSamples,
    jint sampleCount,
    jstring language,
    jstring keywords
) {
    return Java_com_pandivo_whistle_WhistleEngine_nativeTranscribe(env, clazz, pcmSamples, sampleCount, language, keywords);
}

JNIEXPORT jstring JNICALL
Java_com_firetv_musicpoc_WhistleEngine_nativeGetLastError(JNIEnv *env, jclass clazz) {
    return Java_com_pandivo_whistle_WhistleEngine_nativeGetLastError(env, clazz);
}

} // extern "C"
