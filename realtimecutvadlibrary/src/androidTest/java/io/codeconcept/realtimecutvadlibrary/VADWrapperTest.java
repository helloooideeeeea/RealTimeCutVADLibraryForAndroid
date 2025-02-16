package io.codeconcept.realtimecutvadlibrary;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;
import java.nio.*;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class VADWrapperTest {
    private VADWrapper vadWrapper;
    private Context context;

    @Before
    public void setUp()  {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        vadWrapper = new VADWrapper(context);
    }

    @After
    public void tearDown() {
        vadWrapper.release();
    }

    @Test
    public void testProcessAudio() throws IOException {
        assertNotNull("VAD instance should be initialized", vadWrapper);
        vadWrapper.setVADSampleRate(VADWrapper.SampleRate.SAMPLERATE_16);
        vadWrapper.setVADModel(VADWrapper.SileroModelVersion.V4);
//        vadWrapper.setVADThreshold(0.7F, 0.7F, 0.8F, 0.8F, 30, 20);
        vadWrapper.setVADCallback(new VADCallback() {
            @Override
            public void onVoiceStart() {
                Log.d("VADWrapperTest", "✅ onVoiceStart() called");
            }

            @Override
            public void onVoiceEnd(byte[] wavData) {
                Log.d("VADWrapperTest", "✅ onVoiceEnd() called. wavData length: " + (wavData != null ? wavData.length : 0));
            }
        });

        // PCM ファイルをキャッシュディレクトリにコピー
        File pcmFile = ResourceHelper.copyRawResourceToFile(context, io.codeconcept.realtimecutvadlibrary.test.R.raw.audio_16khz_32bitfloat, "pcm");
        // PCM をロード
        float[] audioData = loadPCMFile(pcmFile);

        vadWrapper.processAudio(audioData);
    }

    private float[] loadPCMFile(File file) throws IOException {

        int byteSize = (int) file.length();
        int sampleCount = byteSize / 4; // 32bit (4 bytes) float PCM の場合

        float[] audioSamples = new float[sampleCount];
        byte[] buffer = new byte[byteSize];

        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(buffer);
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < sampleCount; i++) {
            audioSamples[i] = byteBuffer.getFloat();
        }
        return audioSamples;
    }
}
