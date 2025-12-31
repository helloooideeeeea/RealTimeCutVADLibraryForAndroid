#include <jni.h>
#include "c_wrapper.h"
#include <unordered_map>
#include <mutex>

// インスタンスごとの callback 用 context 構造体
typedef struct
{
    JavaVM *vm;
    jobject vadWrapperGlobalRef;
} VadCallbackContext;

// グローバルなマップとその排他制御用ミューテックス
static std::unordered_map<VADInstanceHandle, VadCallbackContext *> s_ctxMap;
static std::mutex s_ctxMutex;

extern "C"
{

    // コールバック関数（インスタンスごとの context を使用）
    void voice_start_callback(void *context)
    {
        VadCallbackContext *ctx = (VadCallbackContext *)context;
        JNIEnv *env;
        if (ctx->vm->AttachCurrentThread(&env, nullptr) != JNI_OK || !env)
            return;

        jclass clazz = env->GetObjectClass(ctx->vadWrapperGlobalRef);
        jmethodID method = env->GetMethodID(clazz, "onVoiceStart", "()V");
        if (method)
        {
            env->CallVoidMethod(ctx->vadWrapperGlobalRef, method);
        }
    }

    void voice_end_callback(void *context, const uint8_t *wav_data, size_t wav_size)
    {
        VadCallbackContext *ctx = (VadCallbackContext *)context;
        JNIEnv *env;
        if (ctx->vm->AttachCurrentThread(&env, nullptr) != JNI_OK || !env)
            return;

        jclass clazz = env->GetObjectClass(ctx->vadWrapperGlobalRef);
        jmethodID method = env->GetMethodID(clazz, "onVoiceEnd", "([B)V");
        if (method)
        {
            jbyteArray wavArray = env->NewByteArray(wav_size);
            env->SetByteArrayRegion(wavArray, 0, wav_size, (const jbyte *)wav_data);
            env->CallVoidMethod(ctx->vadWrapperGlobalRef, method, wavArray);
            env->DeleteLocalRef(wavArray);
        }
    }

    void voice_did_continue_callback(void *context, const uint8_t *pcm_float_data, size_t data_size)
    {
        VadCallbackContext *ctx = (VadCallbackContext *)context;
        JNIEnv *env;
        if (ctx->vm->AttachCurrentThread(&env, nullptr) != JNI_OK || !env)
            return;

        jclass clazz = env->GetObjectClass(ctx->vadWrapperGlobalRef);
        jmethodID method = env->GetMethodID(clazz, "onVoiceDidContinue", "([B)V");
        if (method)
        {
            jbyteArray pcmArray = env->NewByteArray(data_size);
            env->SetByteArrayRegion(pcmArray, 0, data_size, (const jbyte *)pcm_float_data);
            env->CallVoidMethod(ctx->vadWrapperGlobalRef, method, pcmArray);
            env->DeleteLocalRef(pcmArray);
        }
    }

    // JNI: VAD インスタンスを作成 & コールバック設定
    JNIEXPORT jlong JNICALL
    Java_io_codeconcept_realtimecutvadlibrary_VADWrapper_createVADInstance(JNIEnv *env, jobject obj)
    {
        // VAD インスタンスを作成
        VADInstanceHandle instance = create_vad_instance();

        if (instance)
        {
            // JavaVM を取得
            JavaVM *vm;
            env->GetJavaVM(&vm);

            // インスタンス固有の context を生成
            VadCallbackContext *ctx = new VadCallbackContext();
            ctx->vm = vm;
            ctx->vadWrapperGlobalRef = env->NewGlobalRef(obj);
            // ガードスコープ（解放用にmapに登録）
            {
                std::lock_guard<std::mutex> lock(s_ctxMutex);
                s_ctxMap[instance] = ctx;
            }
            // context を callback に渡す
            set_vad_callback(instance, ctx, voice_start_callback, voice_end_callback, voice_did_continue_callback);
        }

        return reinterpret_cast<jlong>(instance);
    }

    // JNI: VAD インスタンスを破棄
    JNIEXPORT void JNICALL
    Java_io_codeconcept_realtimecutvadlibrary_VADWrapper_destroyVADInstance(JNIEnv *env, jobject obj, jlong instance)
    {
        if (instance)
        {
            VADInstanceHandle handle = reinterpret_cast<VADInstanceHandle>(instance);
            // VAD インスタンスの破棄
            destroy_vad_instance(handle);
            // マップから context を取り出して解放
            VadCallbackContext *ctx = nullptr;
            {
                std::lock_guard<std::mutex> lock(s_ctxMutex);
                auto it = s_ctxMap.find(handle);
                if (it != s_ctxMap.end())
                {
                    ctx = it->second;
                    s_ctxMap.erase(it);
                }
            }
            if (ctx)
            {
                env->DeleteGlobalRef(ctx->vadWrapperGlobalRef);
                delete ctx;
            }
        }
    }

    // JNI: サンプリングレート設定
    JNIEXPORT void JNICALL
    Java_io_codeconcept_realtimecutvadlibrary_VADWrapper_setVADSampleRate(JNIEnv *env, jobject obj, jlong instance, jint sampleRate)
    {
        set_vad_sample_rate(reinterpret_cast<VADInstanceHandle>(instance), sampleRate);
    }

    // JNI: モデルの設定
    JNIEXPORT void JNICALL
    Java_io_codeconcept_realtimecutvadlibrary_VADWrapper_setVADModel(JNIEnv *env, jobject obj, jlong instance, jint modelVersion, jstring modelPath)
    {
        const char *path = env->GetStringUTFChars(modelPath, nullptr);
        set_vad_model(reinterpret_cast<VADInstanceHandle>(instance), modelVersion, path);
        env->ReleaseStringUTFChars(modelPath, path);
    }

    // JNI: 閾値の設定
    JNIEXPORT void JNICALL
    Java_io_codeconcept_realtimecutvadlibrary_VADWrapper_setVADThreshold(JNIEnv *env, jobject obj, jlong instance,
                                                                         jfloat vadStartProb, jfloat vadEndProb, jfloat startTrueRatio, jfloat endFalseRatio, jint startFrameCount, jint endFrameCount)
    {
        set_vad_threshold(reinterpret_cast<VADInstanceHandle>(instance), vadStartProb, vadEndProb, startTrueRatio, endFalseRatio, startFrameCount, endFrameCount);
    }

    // JNI: 音声処理を実行
    JNIEXPORT void JNICALL
    Java_io_codeconcept_realtimecutvadlibrary_VADWrapper_processAudio(JNIEnv *env, jobject obj, jlong instance, jfloatArray audioData)
    {
        jsize length = env->GetArrayLength(audioData);
        jfloat *buffer = env->GetFloatArrayElements(audioData, nullptr);

        process_vad_audio(reinterpret_cast<VADInstanceHandle>(instance), buffer, length);

        env->ReleaseFloatArrayElements(audioData, buffer, 0);
    }

} // extern "C"