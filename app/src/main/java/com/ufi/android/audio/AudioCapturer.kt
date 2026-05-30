package com.ufi.android.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioCapturer(private val scope: CoroutineScope) {
    companion object {
        private const val TAG = "AudioCapturer"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SIZE_MS = 100
        private const val CHUNK_SIZE = SAMPLE_RATE * CHUNK_SIZE_MS / 1000
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    var onAudioChunk: ((ShortArray) -> Unit)? = null

    fun startCapture() {
        if (_isCapturing.value) return

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK_SIZE * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            _isCapturing.value = true

            captureJob = scope.launch(Dispatchers.IO) {
                val buffer = ShortArray(CHUNK_SIZE)
                var peakCount = 0
                var peakSum = 0f

                while (isActive && _isCapturing.value) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        val data = if (read < buffer.size) buffer.copyOf(read) else buffer
                        onAudioChunk?.invoke(data)

                        val rms = calculateRms(data)
                        peakSum += rms
                        peakCount++
                        if (peakCount >= 10) {
                            _audioLevel.value = (peakSum / peakCount).coerceIn(0f, 1f)
                            peakSum = 0f
                            peakCount = 0
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "Capture error", e)
        }
    }

    fun stopCapture() {
        _isCapturing.value = false
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture", e)
        }
        audioRecord = null
        _audioLevel.value = 0f
    }

    private fun calculateRms(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0f
        for (sample in samples) {
            sum += (sample.toFloat() / Short.MAX_VALUE).coerceIn(-1f, 1f)
        }
        val avg = sum / samples.size
        var sqSum = 0f
        for (sample in samples) {
            val norm = (sample.toFloat() / Short.MAX_VALUE).coerceIn(-1f, 1f)
            sqSum += (norm - avg) * (norm - avg)
        }
        return kotlin.math.sqrt(sqSum / samples.size)
    }
}
