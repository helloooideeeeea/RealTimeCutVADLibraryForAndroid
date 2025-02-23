# **RealTime Cut VAD Library for Android**

A real-time **Voice Activity Detection (VAD)** library for **Android** using **Silero models**. This library enables efficient, real-time voice detection, making it ideal for applications that require voice-based features.

---

## **Features**

✅ **Real-time Voice Activity Detection (VAD)**  
✅ **Supports Silero Model Versions v4 and v5**  
✅ **Customizable audio sample rates (8, 16, 24, 48 kHz)**  
✅ **Outputs WAV data with automatic sample rate conversion to 16 kHz**  
✅ **Lightweight and optimized for Android**  
✅ **Available via JitPack**  

---

## **Sample Android App Demo**

Check out the sample Android app demonstrating real-time VAD:

[Sample Android App Demo]()

---

## **Installation**

### **Using JitPack**

1. **Add JitPack to `settings.gradle.kts`**
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

2. **Add the dependency to `app/build.gradle.kts`**
```kotlin
dependencies {
    implementation("com.github.helloooideeeeea:RealTimeCutVADLibraryForAndroid:1.0.0@aar")
}
```

---

## **Usage**

### **1. Initialize VAD in `MainActivity`**
```kotlin
import io.codeconcept.realtimecutvadlibrary.VADWrapper
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log

class MainActivity : AppCompatActivity() {
    private var vadWrapper: VADWrapper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize VAD Wrapper
        vadWrapper = VADWrapper(this)
        vadWrapper?.setVADModel(VADWrapper.SileroModelVersion.V4)

        // Set VAD sample rate based on input sample rate
        when (sampleRate) {
            48000 -> vadWrapper?.setVADSampleRate(VADWrapper.SampleRate.SAMPLERATE_48)
            24000 -> vadWrapper?.setVADSampleRate(VADWrapper.SampleRate.SAMPLERATE_24)
            16000 -> vadWrapper?.setVADSampleRate(VADWrapper.SampleRate.SAMPLERATE_16)
            8000  -> vadWrapper?.setVADSampleRate(VADWrapper.SampleRate.SAMPLERATE_8)
            else  -> {
                Log.e("VAD", "Unsupported sample rate: $sampleRate")
                return
            }
        }

        // Set VAD callback
        vadWrapper?.setVADCallback(object : VADCallback {
            override fun onVoiceStart() {
                Log.d("VAD", "✅ onVoiceStart() called")
                onStatusChange(RecordingStatus.TALKING, null)
            }

            override fun onVoiceEnd(wavData: ByteArray?) {
                Log.d("VAD", "✅ onVoiceEnd() called. wavData length: ${wavData?.size ?: 0}")
                onStatusChange(RecordingStatus.RUNNING, wavData) // 🔹 waveAudioData を渡す
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        vadWrapper?.release()
    }
}
```

### **2. Understanding `setVADCallback`**
`setVADCallback` is used to register a callback that gets notified when voice activity starts or ends.

- `onVoiceStart()`: Triggered when voice is detected.
- `onVoiceEnd(wavData: ByteArray?)`: Triggered when voice stops, providing a WAV file as a byte array.

This enables real-time processing of voice input, allowing applications to act on detected speech events.

---

## Configuration Options

### Sample Rates
You can set the audio sample rate using `setSamplerate`:

- `.SAMPLERATE_8`  (8 kHz)
- `.SAMPLERATE_16` (16 kHz)
- `.SAMPLERATE_24` (24 kHz)
- `.SAMPLERATE_48` (48 kHz)

### Silero Model Versions
Choose between Silero model versions:

- `.v4` - Silero Model Version 4
- `.v5` - Silero Model Version 5

### **VAD Threshold Configuration**

Customize VAD detection sensitivity using `setVADThresholdWithVadStartDetectionProbability()`:

```kotlin
vadWrapper.setVADThreshold(0.7F, 0.7F, 0.8F, 0.95F, 10, 57)
↓

    0.7F,  // Start detection probability threshold
    0.7F,  // End detection probability threshold
    0.8F,  // True positive ratio for voice start
    0.95F, // False positive ratio for voice end
    10,    // Frames to confirm voice start (0.32s)
    57     // Frames to confirm voice end (1.792s)

```

### **Threshold Explanation**
- **Start detection probability threshold (0.7)**: The VAD model must predict speech probability above this threshold to trigger voice start.
- **End detection probability threshold (0.7)**: The VAD model must predict speech probability below this threshold to trigger voice end.
- **True positive ratio for voice start (0.8)**: 80% of frames in a given window must be speech for voice activity to begin.
- **False positive ratio for voice end (0.95)**: 95% of frames in a given window must be silence for voice activity to end.
- **Start frame count (10 frames ≈ 0.32s)**: Number of frames required to confirm voice activity.
- **End frame count (57 frames ≈ 1.792s)**: Number of frames required to confirm silence before stopping voice detection.


#### **Important Notes:**
- **Default Thresholds for Silero v4**: If you do not configure the VAD thresholds manually, the library will use default thresholds optimized for Silero model **v4**.
- **Silero v5 Performance**: The performance of Silero model **v5** may vary, and it is recommended to experiment with different thresholds to achieve optimal results. For more information, refer to the [related discussion](https://github.com/SYSTRAN/faster-whisper/issues/934#issuecomment-2439340290).

---

## Algorithm Explanation

### ONNX Runtime for Silero VAD
This library leverages **ONNX Runtime (C++)** to run the Silero VAD models efficiently. By utilizing ONNX Runtime, the library achieves high-performance inference across different platforms (iOS/macOS), ensuring fast and accurate voice activity detection.

### Why Use WebRTC's Audio Processing Module (APM)?
This library utilizes WebRTC's APM for several key reasons:

- **High-pass Filtering**: Removes low-frequency noise.
- **Noise Suppression**: Reduces background noise for clearer voice detection.
- **Gain Control**: Adaptive digital gain control enhances audio levels.
- **Sample Rate Conversion**: Silero VAD requires a sample rate of 16 kHz, and APM ensures conversion from other sample rates (8, 24, or 48 kHz).

### Audio Processing Workflow

1. **Input Audio Configuration**: The library supports sample rates of 8 kHz, 16 kHz, 24 kHz, and 48 kHz.
2. **Audio Preprocessing**:
   - The audio is split into chunks based on the sample rate.
   - APM processes these chunks with filters and gain adjustments.
   - Audio is converted to 16 kHz for Silero VAD compatibility.

3. **Voice Activity Detection**:
   - The processed audio chunks are passed to Silero VAD.
   - VAD outputs a probability score indicating voice activity.

4. **Algorithm for Voice Detection**:
   - **Voice Start Detection**: When the VAD probability exceeds the threshold, a pre-buffer stores audio frames to capture speech onset.
   - **Voice End Detection**: Once silence is detected over a set number of frames, recording stops, and the audio is output as WAV data.

5. **Output**:
   - The resulting audio data is provided as WAV with a sample rate of 16 kHz.

### WebRTC APM Configuration

The following configurations are applied to optimize voice detection:

```cpp
config.gain_controller1.enabled = true;
config.gain_controller1.mode = webrtc::AudioProcessing::Config::GainController1::kAdaptiveDigital;
config.gain_controller2.enabled = true;
config.high_pass_filter.enabled = true;
config.noise_suppression.enabled = true;
config.transient_suppression.enabled = true;
config.voice_detection.enabled = false;
```

---

## **Additional Resources**
- **[RealTimeCutVADCXXLibrary](https://github.com/helloooideeeeea/RealTimeCutVADCXXLibrary)**

---

## **License**
This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

---

## **📌 Summary**
| Feature | Details |
|---------|---------|
| **Library Name** | `RealTimeCutVADLibrary` |
| **Platform** | Android |
| **Voice Detection** | Real-time |
| **Supported Models** | Silero v4 & v5 |
| **Sample Rates** | 8kHz, 16kHz, 24kHz, 48kHz |
| **Output Format** | WAV (16 kHz) |
| **Noise Reduction** | WebRTC APM |
| **Installation** | JitPack (`implementation` via Gradle) |

🚀 **Now you can add real-time voice activity detection to your Android app with ease!** 🎉

