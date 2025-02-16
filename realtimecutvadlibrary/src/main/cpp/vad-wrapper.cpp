#include <jni.h>
#include "c_wrapper.h"


extern "C" {

static JavaVM* g_vm = nullptr;
static jobject g_vadWrapper = nullptr;

// コールバック関数
void voice_start_callback(void* context) {
    JNIEnv* env;
    if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK || !env) return;

    jclass clazz = env->GetObjectClass(g_vadWrapper);
    jmethodID method = env->GetMethodID(clazz, "onVoiceStart", "()V");

    if (method) {
        env->CallVoidMethod(g_vadWrapper, method);
    }
}

void voice_end_callback(void* context, const uint8_t* wav_data, size_t wav_size) {
    JNIEnv* env;
    if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK || !env) return;

    jclass clazz = env->GetObjectClass(g_vadWrapper);
    jmethodID method = env->GetMethodID(clazz, "onVoiceEnd", "([B)V");

    if (method) {
        jbyteArray wavArray = env->NewByteArray(wav_size);
        env->SetByteArrayRegion(wavArray, 0, wav_size, (const jbyte*)wav_data);
        env->CallVoidMethod(g_vadWrapper, method, wavArray);
        env->DeleteLocalRef(wavArray);
    }
}

// JNI: VAD インスタンスを作成 & コールバック設定
JNIEXPORT jlong JNICALL
Java_io_codeconcept_realtimecutvadlibrary_VADWrapper_createVADInstance(JNIEnv *env, jobject obj) {
    // VAD インスタンスを作成
    VADInstanceHandle instance = create_vad_instance();

    if (instance) {
        env->GetJavaVM(&g_vm);
        g_vadWrapper = env->NewGlobalRef(obj); // Java オブジェクトをグローバル参照

        // コールバックを設定
        set_vad_callback(instance, nullptr, voice_start_callback, voice_end_callback);
    }

    return reinterpret_cast<jlong>(instance);
}

// JNI: VAD インスタンスを破棄
JNIEXPORT void JNICALL
Java_io_codeconcept_realtimecutvadlibrary_VADWrapper_destroyVADInstance(JNIEnv *env, jobject obj, jlong instance) {
    if (instance) {
        destroy_vad_instance(reinterpret_cast<VADInstanceHandle>(instance));
    }
    if (g_vadWrapper) {
        env->DeleteGlobalRef(g_vadWrapper);
        g_vadWrapper = nullptr;
    }
}

// JNI: サンプリングレート設定
JNIEXPORT void JNICALL
Java_io_codeconcept_realtimecutvadlibrary_VADWrapper_setVADSampleRate(JNIEnv *env, jobject obj, jlong instance, jint sampleRate) {
    set_vad_sample_rate(reinterpret_cast<VADInstanceHandle>(instance), sampleRate);
}

// JNI: モデルの設定
JNIEXPORT void JNICALL
Java_io_codeconcept_realtimecutvadlibrary_VADWrapper_setVADModel(JNIEnv *env, jobject obj, jlong instance, jint modelVersion, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    set_vad_model(reinterpret_cast<VADInstanceHandle>(instance), modelVersion, path);
    env->ReleaseStringUTFChars(modelPath, path);
}

// JNI: 閾値の設定
JNIEXPORT void JNICALL
Java_io_codeconcept_realtimecutvadlibrary_VADWrapper_setVADThreshold(JNIEnv *env, jobject obj, jlong instance,
    jfloat vadStartProb, jfloat vadEndProb, jfloat startTrueRatio, jfloat endFalseRatio, jint startFrameCount, jint endFrameCount) {
    set_vad_threshold(reinterpret_cast<VADInstanceHandle>(instance), vadStartProb, vadEndProb, startTrueRatio, endFalseRatio, startFrameCount, endFrameCount);
}

// JNI: 音声処理を実行
JNIEXPORT void JNICALL
Java_io_codeconcept_realtimecutvadlibrary_VADWrapper_processAudio(JNIEnv *env, jobject obj, jlong instance, jfloatArray audioData) {
    jsize length = env->GetArrayLength(audioData);
    jfloat* buffer = env->GetFloatArrayElements(audioData, nullptr);

    process_vad_audio(reinterpret_cast<VADInstanceHandle>(instance), buffer, length);

    env->ReleaseFloatArrayElements(audioData, buffer, 0);
}

}
