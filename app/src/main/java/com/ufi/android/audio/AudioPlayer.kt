package com.ufi.android.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue

class AudioPlayer(private val scope: CoroutineScope) {
    companion object {
        private const val TAG = "AudioPlayer"
        private const val SAMPLE_RATE = 24000
        private const val MAX_QUEUE_SIZE = 200
        private const val MAX_BATCH_SIZE = 50
    }

    private var audioTrack: AudioTrack? = null
    private val queue = ConcurrentLinkedQueue<ShortArray>()
    private var playbackJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun enqueuePcm(shorts: ShortArray) {
        if (queue.size > MAX_QUEUE_SIZE) {
            val excess = queue.size - MAX_QUEUE_SIZE
            repeat(excess) { queue.poll() }
        }
        queue.add(shorts)
        ensurePlayback()
    }

    private fun ensurePlayback() {
        if (playbackJob?.isActive == true) return

        playbackJob = scope.launch(Dispatchers.IO) {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val attrs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            } else null

            audioTrack = AudioTrack(
                attrs ?: AudioAttributes.Builder().build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                0
            )

            try {
                audioTrack?.play()
                _isPlaying.value = true

                val batchBuffer = ByteBuffer.allocate(SAMPLE_RATE * 2 * MAX_BATCH_SIZE)
                batchBuffer.order(ByteOrder.LITTLE_ENDIAN)

                while (isActive) {
                    val chunk = queue.poll() ?: break
                    batchBuffer.clear()
                    for (s in chunk) {
                        batchBuffer.putShort(s)
                    }
                    batchBuffer.flip()
                    val bytes = ByteArray(batchBuffer.remaining())
                    batchBuffer.get(bytes)
                    audioTrack?.write(bytes, 0, bytes.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
            } finally {
                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null
                _isPlaying.value = false
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        queue.clear()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback", e)
        }
        audioTrack = null
        _isPlaying.value = false
    }
}
