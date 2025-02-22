package io.codeconcept.realtimecutvadlibrary;

import android.content.Context;

import java.io.File;


public class VADWrapper {
    static {
        System.loadLibrary("RealtimeCutVadLibraryForAndroid");
    }

    private long vadInstance; // C++ の VADInstanceHandle を格納
    private VADCallback callback; // Java 側のコールバック
    private final Context context;

    public VADWrapper(Context context) {
        this.context = context;
        vadInstance = createVADInstance();
    }

    private native long createVADInstance();
    private native void destroyVADInstance(long instance);
    private native void processAudio(long instance, float[] audioData);
    private native void setVADSampleRate(long instance, int sampleRate);
    private native void setVADModel(long instance, int modelVersion, String modelPath);
    private native void setVADThreshold(long instance, float vadStartProb, float vadEndProb,
                                       float startTrueRatio, float endFalseRatio,
                                       int startFrameCount, int endFrameCount);


    // Java Enum for Silero Model Versions
    public enum SileroModelVersion {
        V4(0),
        V5(1);

        private final int value;

        SileroModelVersion(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public void setVADModel(SileroModelVersion modelVersion) {
        if (vadInstance != 0) {
            File file = getModelFilePath(context, modelVersion);
            if (file != null) {
                setVADModel(vadInstance, modelVersion.getValue(), file.getAbsolutePath());
            }
        }
    }

    private File getModelFilePath(Context context, SileroModelVersion modelVersion) {
        int rawResId = (modelVersion == SileroModelVersion.V5) ? R.raw.silero_vad_v5 : R.raw.silero_vad;
        String extension = "onnx";
        return ResourceHelper.copyRawResourceToFile(context, rawResId, extension);
    }

    // Java Enum for sample rates
    public enum SampleRate {
        SAMPLERATE_8(0),
        SAMPLERATE_16(1),
        SAMPLERATE_24(2),
        SAMPLERATE_48(3);

        private final int value;

        SampleRate(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public void setVADSampleRate(SampleRate sampleRate) {
        if (vadInstance != 0) {
            setVADSampleRate(vadInstance, sampleRate.getValue());
        }
    }

    public void setVADThreshold(float vadStartProb, float vadEndProb, float startTrueRatio,
                                float endFalseRatio, int startFrameCount, int endFrameCount) {
        if (vadInstance != 0) {
            setVADThreshold(vadInstance, vadStartProb, vadEndProb, startTrueRatio, endFalseRatio, startFrameCount, endFrameCount);
        }
    }

    public void processAudio(float[] audioData) {
        if (vadInstance != 0) {
            processAudio(vadInstance, audioData);
        }
    }


    public void release() {
        if (vadInstance != 0) {
            destroyVADInstance(vadInstance);
            vadInstance = 0;
        }
    }

    public void setVADCallback(VADCallback callback) {
        this.callback = callback;
    }

    // JNI から呼び出すためのメソッド
    private void onVoiceStart() {
        if (callback != null) {
            callback.onVoiceStart();
        }
    }

    private void onVoiceEnd(byte[] wavData) {
        if (callback != null) {
            callback.onVoiceEnd(wavData);
        }
    }
}
