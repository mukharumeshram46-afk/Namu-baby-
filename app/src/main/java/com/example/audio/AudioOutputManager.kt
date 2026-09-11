package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class AudioOutputManager(
    private val scope: CoroutineScope,
    private val onPlaybackStateChanged: (Boolean) -> Unit,
    private val onOutputAmplitudeChanged: (Float) -> Unit
) {
    companion object {
        private const val TAG = "AudioOutputManager"
        const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private var playbackJob: Job? = null
    @Volatile
    var isPlaying: Boolean = false
        private set

    init {
        initAudioTrack()
        startPlaybackLoop()
    }

    private fun initAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = minBufferSize * 2

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AUDIO_FORMAT)
            .setChannelMask(CHANNEL_CONFIG)
            .build()

        audioTrack = AudioTrack(
            audioAttributes,
            audioFormat,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack?.play()
    }

    private fun startPlaybackLoop() {
        playbackJob = scope.launch(Dispatchers.IO) {
            val shortBuffer = ShortArray(1024)
            while (isActive) {
                val chunk = audioQueue.receive()
                if (chunk.isNotEmpty()) {
                    if (!isPlaying) {
                        isPlaying = true
                        onPlaybackStateChanged(true)
                    }

                    // Compute RMS for model voice visualizer
                    val samples = chunk.size / 2
                    if (samples > 0) {
                        ByteBuffer.wrap(chunk)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .asShortBuffer()
                            .get(shortBuffer, 0, minOf(shortBuffer.size, samples))

                        var sumSquare = 0.0
                        val count = minOf(shortBuffer.size, samples)
                        for (i in 0 until count) {
                            val norm = shortBuffer[i].toDouble() / 32768.0
                            sumSquare += norm * norm
                        }
                        val rms = sqrt(sumSquare / count).toFloat()
                        onOutputAmplitudeChanged(rms)
                    }

                    // Write to AudioTrack
                    audioTrack?.write(chunk, 0, chunk.size)
                }

                if (audioQueue.isEmpty && isPlaying) {
                    isPlaying = false
                    onPlaybackStateChanged(false)
                    onOutputAmplitudeChanged(0f)
                }
            }
        }
    }

    /**
     * Feed incoming 24kHz PCM16 audio bytes from Gemini Live.
     */
    fun enqueueAudio(pcmChunk: ByteArray) {
        if (pcmChunk.isNotEmpty()) {
            audioQueue.trySend(pcmChunk)
        }
    }

    /**
     * BARGE-IN / INTERRUPTION:
     * Immediately stops and flushes all pending audio so the user isn't forced to wait!
     */
    fun flushAndInterrupt() {
        Log.i(TAG, "Flushing and interrupting audio output")
        // Drain channel
        while (audioQueue.tryReceive().isSuccess) {
            // drained
        }

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.w(TAG, "AudioTrack flush error: ${e.message}")
        }

        isPlaying = false
        onPlaybackStateChanged(false)
        onOutputAmplitudeChanged(0f)
    }

    fun release() {
        flushAndInterrupt()
        playbackJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack: ${e.message}")
        }
        audioTrack = null
    }
}
