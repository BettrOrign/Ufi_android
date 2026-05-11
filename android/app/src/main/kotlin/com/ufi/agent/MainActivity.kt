package com.ufi.agent

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.IOException

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.ufi.agent/audio_recorder"
    private val PERMISSION_REQUEST_CODE = 200

    private var mediaRecorder: MediaRecorder? = null
    private var currentFilePath: String? = null
    private var isRecording = false
    private var isPaused = false
    private var startTime: Long = 0
    private var pausedDuration: Long = 0
    private var lastPauseTime: Long = 0

    private val handler = Handler(Looper.getMainLooper())
    private var durationRunnable: Runnable? = null
    private var methodChannel: MethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "startRecording" -> {
                    val success = startRecording()
                    result.success(success)
                }

                "stopRecording" -> {
                    val path = stopRecording()
                    result.success(path)
                }

                "pauseRecording" -> {
                    pauseRecording()
                    result.success(null)
                }

                "resumeRecording" -> {
                    resumeRecording()
                    result.success(null)
                }

                "cancelRecording" -> {
                    cancelRecording()
                    result.success(null)
                }

                "checkPermission" -> {
                    val granted = checkPermission()
                    result.success(granted)
                }

                "requestPermission" -> {
                    requestPermission()
                    result.success(null)
                }

                "getRecordDuration" -> {
                    result.success(getRecordDuration())
                }

                else -> result.notImplemented()
            }
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun startRecording(): Boolean {
        if (isRecording) return true

        if (!checkPermission()) {
            requestPermission()
            return false
        }

        try {
            val outputDir = filesDir
            val timestamp = System.currentTimeMillis()
            currentFilePath = File(outputDir, "recording_$timestamp.m4a").absolutePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(currentFilePath)

                try {
                    prepare()
                    start()
                    isRecording = true
                    isPaused = false
                    startTime = System.currentTimeMillis()
                    pausedDuration = 0
                    startDurationUpdates()
                } catch (e: IOException) {
                    e.printStackTrace()
                    release()
                    mediaRecorder = null
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun stopRecording(): String? {
        if (!isRecording) return null

        stopDurationUpdates()

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mediaRecorder = null
        isRecording = false
        isPaused = false

        val path = currentFilePath
        currentFilePath = null

        return path
    }

    private fun pauseRecording() {
        if (!isRecording || isPaused) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                isPaused = true
                lastPauseTime = System.currentTimeMillis()
                stopDurationUpdates()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resumeRecording() {
        if (!isRecording || !isPaused) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                isPaused = false
                pausedDuration += System.currentTimeMillis() - lastPauseTime
                startDurationUpdates()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun cancelRecording() {
        stopDurationUpdates()

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mediaRecorder = null
        isRecording = false
        isPaused = false

        // Delete the file
        currentFilePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
        currentFilePath = null
    }

    private fun getRecordDuration(): Long {
        if (!isRecording) return 0

        val currentTime = if (isPaused) lastPauseTime else System.currentTimeMillis()
        return currentTime - startTime - pausedDuration
    }

    private fun startDurationUpdates() {
        durationRunnable = object : Runnable {
            override fun run() {
                if (isRecording && !isPaused) {
                    val duration = getRecordDuration()
                    methodChannel?.invokeMethod("onDurationChanged", duration)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(durationRunnable!!)
    }

    private fun stopDurationUpdates() {
        durationRunnable?.let { handler.removeCallbacks(it) }
        durationRunnable = null
    }

    override fun onDestroy() {
        stopDurationUpdates()
        if (isRecording) {
            stopRecording()
        }
        super.onDestroy()
    }
}
