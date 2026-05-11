package com.ufi.agent

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
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

class MainActivity : FlutterActivity() {
    private val RECORDER_CHANNEL = "com.ufi.agent/audio_recorder"
    private val PLAYER_CHANNEL = "com.ufi.agent/audio_player"
    private val PERMISSION_REQUEST_CODE = 200

    private var mediaRecorder: MediaRecorder? = null
    private var currentFilePath: String? = null
    private var isRecording = false
    private var isPaused = false
    private var startTime: Long = 0
    private var pausedDuration: Long = 0
    private var lastPauseTime: Long = 0

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var currentPlayerPath: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private var recorderRunnable: Runnable? = null
    private var playerRunnable: Runnable? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val recorderChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, RECORDER_CHANNEL)
        recorderChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "startRecording" -> result.success(startRecording())
                "stopRecording" -> result.success(stopRecording())
                "pauseRecording" -> {
                    pauseRecording(); result.success(null)
                }

                "resumeRecording" -> {
                    resumeRecording(); result.success(null)
                }

                "cancelRecording" -> {
                    cancelRecording(); result.success(null)
                }

                "checkPermission" -> result.success(checkPermission())
                "requestPermission" -> {
                    requestPermission(); result.success(null)
                }

                "getRecordDuration" -> result.success(getRecordDuration())
                else -> result.notImplemented()
            }
        }

        val playerChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, PLAYER_CHANNEL)
        playerChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "play" -> {
                    val path = call.arguments as String
                    result.success(playAudio(path))
                }

                "pause" -> {
                    pauseAudio(); result.success(null)
                }

                "resume" -> {
                    resumeAudio(); result.success(null)
                }

                "stop" -> {
                    stopAudio(); result.success(null)
                }

                "seek" -> {
                    seekAudio(call.arguments as Int); result.success(null)
                }

                "getDuration" -> result.success(getAudioDuration())
                else -> result.notImplemented()
            }
        }
    }

    private fun checkPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() =
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)

    private fun startRecording(): Boolean {
        if (isRecording) return true
        if (!checkPermission()) {
            requestPermission(); return false
        }
        try {
            val outputDir = filesDir
            currentFilePath = File(outputDir, "recording_${System.currentTimeMillis()}.m4a").absolutePath
            mediaRecorder =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(currentFilePath)
                prepare()
                start()
                isRecording = true
                isPaused = false
                startTime = System.currentTimeMillis()
                pausedDuration = 0
                startRecorderUpdates()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace(); return false
        }
    }

    private fun stopRecording(): String? {
        if (!isRecording) return null
        stopRecorderUpdates()
        try {
            mediaRecorder?.stop(); mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        isRecording = false
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
                stopRecorderUpdates()
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
                startRecorderUpdates()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun cancelRecording() {
        stopRecorderUpdates()
        try {
            mediaRecorder?.stop(); mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        isRecording = false
        currentFilePath?.let { File(it).delete() }
        currentFilePath = null
    }

    private fun getRecordDuration(): Long {
        if (!isRecording) return 0
        val currentTime = if (isPaused) lastPauseTime else System.currentTimeMillis()
        return currentTime - startTime - pausedDuration
    }

    private fun startRecorderUpdates() {
        val channel = MethodChannel(flutterEngine!!.dartExecutor.binaryMessenger, RECORDER_CHANNEL)
        recorderRunnable = object : Runnable {
            override fun run() {
                if (isRecording && !isPaused) {
                    channel.invokeMethod("onDurationChanged", getRecordDuration().toInt())
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(recorderRunnable!!)
    }

    private fun stopRecorderUpdates() {
        recorderRunnable?.let { handler.removeCallbacks(it) }; recorderRunnable = null
    }

    private fun playAudio(path: String): Boolean {
        try {
            if (mediaPlayer != null && currentPlayerPath != path) stopAudio()
            if (mediaPlayer == null) mediaPlayer = MediaPlayer()
            mediaPlayer?.apply {
                setDataSource(path)
                prepare()
                start()
                isPlaying = true
                currentPlayerPath = path
                startPlayerUpdates()
                setOnCompletionListener {
                    isPlaying = false
                    stopPlayerUpdates()
                    MethodChannel(
                        flutterEngine!!.dartExecutor.binaryMessenger,
                        PLAYER_CHANNEL
                    ).invokeMethod("onComplete", null)
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace(); return false
        }
    }

    private fun pauseAudio() {
        try {
            mediaPlayer?.pause(); isPlaying = false; stopPlayerUpdates()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resumeAudio() {
        try {
            mediaPlayer?.start(); isPlaying = true; startPlayerUpdates()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAudio() {
        try {
            stopPlayerUpdates()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            currentPlayerPath = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun seekAudio(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAudioDuration(): Int = try {
        mediaPlayer?.duration ?: 0
    } catch (e: Exception) {
        0
    }

    private fun getPlayerPosition(): Int = try {
        mediaPlayer?.currentPosition ?: 0
    } catch (e: Exception) {
        0
    }

    private fun startPlayerUpdates() {
        val channel = MethodChannel(flutterEngine!!.dartExecutor.binaryMessenger, PLAYER_CHANNEL)
        playerRunnable = object : Runnable {
            override fun run() {
                if (isPlaying) {
                    channel.invokeMethod("onPositionChanged", getPlayerPosition())
                    channel.invokeMethod("onDurationChanged", getAudioDuration())
                    handler.postDelayed(this, 500)
                }
            }
        }
        handler.post(playerRunnable!!)
    }

    private fun stopPlayerUpdates() {
        playerRunnable?.let { handler.removeCallbacks(it) }; playerRunnable = null
    }

    override fun onDestroy() {
        stopRecorderUpdates()
        stopPlayerUpdates()
        if (isRecording) stopRecording()
        if (isPlaying || mediaPlayer != null) stopAudio()
        super.onDestroy()
    }
}
