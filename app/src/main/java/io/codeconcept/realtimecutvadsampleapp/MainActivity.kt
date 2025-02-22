package io.codeconcept.realtimecutvadsampleapp

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.media.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.activity.compose.setContent
import io.codeconcept.realtimecutvadlibrary.VADCallback
import io.codeconcept.realtimecutvadlibrary.VADWrapper
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow

enum class RecordingStatus {
    TALKING, RUNNING, OFF
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MicRecordingScreen()
        }
    }
}

private val recordingStatusFlow = MutableStateFlow(RecordingStatus.RUNNING)

@Composable
fun MicRecordingScreen() {
    val context = LocalContext.current
    val recordingStatus by recordingStatusFlow.collectAsState() // 🔹 `StateFlow` を監視
    var waveAudioData by remember { mutableStateOf<ByteArray?>(null) } // 🔹 録音データの状態管理

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Now the user has just granted permission, so start capturing here
            startVADProcessing(context) { status, wavData ->
                recordingStatusFlow.value = status
                wavData?.let { waveAudioData = it }
            }
        } else {
            Log.e("Permission", "Microphone permission denied!")
        }
    }

    // アプリ起動時にパーミッションチェック & リクエスト
    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            // permission already granted
            startVADProcessing(context) { status, wavData ->
                recordingStatusFlow.value = status
                wavData?.let { waveAudioData = it }
            }
        }
    }

    // UI レイアウト
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Microphone Status: ${recordingStatus.name}")

            Spacer(modifier = Modifier.height(16.dp))

            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Microphone",
                tint = when (recordingStatus) {
                    RecordingStatus.TALKING -> Color.Red
                    RecordingStatus.RUNNING -> Color.Green
                    RecordingStatus.OFF -> Color.Black
                },
                modifier = Modifier
                    .size(100.dp)
                    .background(Color.LightGray, shape = CircleShape)
                    .padding(16.dp)
            )

            // 🔹 waveAudioData が null でない場合、ボタンを表示
            if (waveAudioData != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        recordingStatusFlow.value = RecordingStatus.OFF
                        playWav(context, waveAudioData!!) {
                            recordingStatusFlow.value = RecordingStatus.RUNNING
                        }
                    }) {
                        Text("▶ 再生")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { shareAudio(context, waveAudioData!!) }) {
                        Text("📤 シェア")
                    }
                }
            }
        }
    }
}

private var vadJob: Job? = null  // 🔹 コルーチンのジョブ

// 🔹 VAD を用いたリアルタイム音声処理 (Float Audio Points 取得)
fun startVADProcessing(
    context: Context,
    onStatusChange: (RecordingStatus, ByteArray?) -> Unit
) {
    val sampleRate = getNativeSampleRate(context)  // 🔹 デバイスのネイティブサンプルレートを取得
    Log.d("VAD", "Sample Rate: $sampleRate")
    val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_FLOAT
    )

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        Log.e("Permission", "Microphone permission not granted!")
        return
    }

    val vadWrapper = VADWrapper(context)
    vadWrapper.setVADModel(VADWrapper.SileroModelVersion.V4)

    // 🔹 VAD に適したサンプルレートを設定
    when (sampleRate) {
        48000 -> vadWrapper.setVADSampleRate(VADWrapper.SampleRate.SAMPLERATE_48)
        24000 -> vadWrapper.setVADSampleRate(VADWrapper.SampleRate.SAMPLERATE_24)
        16000 -> vadWrapper.setVADSampleRate(VADWrapper.SampleRate.SAMPLERATE_16)
        8000  -> vadWrapper.setVADSampleRate(VADWrapper.SampleRate.SAMPLERATE_8)
        else  -> {
            Log.e("VAD", "Unsupported sample rate: $sampleRate")
            return
        }
    }

    vadWrapper.setVADCallback(object : VADCallback {
        override fun onVoiceStart() {
            Log.d("VAD", "✅ onVoiceStart() called")
            onStatusChange(RecordingStatus.TALKING, null)
        }

        override fun onVoiceEnd(wavData: ByteArray?) {
            Log.d("VAD", "✅ onVoiceEnd() called. wavData length: ${wavData?.size ?: 0}")
            onStatusChange(RecordingStatus.RUNNING, wavData) // 🔹 waveAudioData を渡す
        }
    })

    val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_FLOAT,
        bufferSize
    )

    audioRecord.startRecording()

    // 🔹 既存のコルーチンがあればキャンセルする
    vadJob?.cancel()
    vadJob = CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            if (recordingStatusFlow.value != RecordingStatus.OFF) {
                val buffer = FloatArray(bufferSize / 4)
//            Log.d("VAD", "buffer: ${buffer.size}")
                val readSize = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
//            Log.d("VAD", "ReadSize: ${readSize}")
                if (readSize > 0) {
//                Log.d("VAD", "Audio Buffer: ${buffer.joinToString(", ")}")
                    vadWrapper.processAudio(buffer)
                }
            }
        }
    }
}

fun getNativeSampleRate(context: Context): Int {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toInt() ?: 44100
}

// 🔹 録音データの再生処理
fun playWav(context: Context, wavData: ByteArray, onComplete: () -> Unit) {
    try {
        // 🔹 WAV データを一時ファイルに保存
        val tempFile = File.createTempFile("temp_audio", ".wav", context.cacheDir)
        FileOutputStream(tempFile).use { it.write(wavData) }

        // 🔹 MediaPlayer で再生
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(context, Uri.fromFile(tempFile))
            prepare()
            start()
            setOnCompletionListener {
                onComplete()
            }
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// 🔹 音声データのシェア処理
fun shareAudio(context: Context, audioData: ByteArray) {
    // 一時ファイルに保存
    val file = File(context.cacheDir, "recorded_audio.wav")
    file.writeBytes(audioData)

    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/wav"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(Intent.createChooser(shareIntent, "Share audio"))
}