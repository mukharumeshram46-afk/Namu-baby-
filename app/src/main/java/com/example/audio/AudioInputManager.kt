package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class AudioInputManager(
    private val scope: CoroutineScope,
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onUserSpeechDetected: () -> Unit,
    private val onAmplitudeChanged: (Float) -> Unit
) {
    companion object {
        private const val TAG = "AudioInputManager"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
        private const val SPEECH_AMPLITUDE_THRESHOLD = 0.08f
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    @Volatile
    var isRecording: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (isRecording) return true

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size for AudioRecord")
            return false
        }

        val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to MIC if VOICE_COMMUNICATION is restricted by OEM
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return false
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(2048)
                val shortBuffer = ShortArray(1024)

                while (isActive && isRecording) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        // Deliver PCM16 chunk
                        val chunk = buffer.copyOf(readBytes)
                        onAudioChunk(chunk)

                        // Measure RMS amplitude
                        ByteBuffer.wrap(buffer, 0, readBytes)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .get(shortBuffer, 0, readBytes / 2)

                        var sumSquare = 0.0
                        val samples = readBytes / 2
                        for (i in 0 until samples) {
                            val norm = shortBuffer[i].toDouble() / 32768.0
                            sumSquare += norm * norm
                        }
                        val rms = sqrt(sumSquare / samples).toFloat()
                        onAmplitudeChanged(rms)

                        if (rms > SPEECH_AMPLITUDE_THRESHOLD) {
                            onUserSpeechDetected()
                        }
                    }
                }
            }
            Log.i(TAG, "Audio recording started at 16kHz mono PCM")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting AudioRecord: ${e.message}", e)
            stopRecording()
            return false
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord: ${e.message}")
        }
        audioRecord = null
        onAmplitudeChanged(0f)
    }
}
