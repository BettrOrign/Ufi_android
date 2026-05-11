import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:record/record.dart';

enum RecorderState {
  idle,
  recording,
  paused,
}

class AudioRecorderService extends ChangeNotifier {
  final AudioRecorder _recorder = AudioRecorder();
  RecorderState _state = RecorderState.idle;
  String? _currentFilePath;
  Duration _recordDuration = Duration.zero;
  Timer? _durationTimer;

  RecorderState get state => _state;
  bool get isRecording => _state == RecorderState.recording;
  bool get isPaused => _state == RecorderState.paused;
  bool get isIdle => _state == RecorderState.idle;
  Duration get recordDuration => _recordDuration;
  String? get currentFilePath => _currentFilePath;

  Future<bool> _requestPermission() async {
    final status = await Permission.microphone.request();
    return status.isGranted;
  }

  Future<bool> _checkPermission() async {
    return await Permission.microphone.isGranted;
  }

  Future<bool> startRecording() async {
    try {
      // Check/request permission
      if (!await _checkPermission()) {
        if (!await _requestPermission()) {
          debugPrint('Microphone permission denied');
          return false;
        }
      }

      // Check if recorder is available
      if (!await _recorder.hasPermission()) {
        debugPrint('Recorder permission denied');
        return false;
      }

      // Stop any existing recording
      if (_state != RecorderState.idle) {
        await stopRecording();
      }

      // Generate file path
      final directory = await getApplicationDocumentsDirectory();
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      _currentFilePath = '${directory.path}/recording_$timestamp.m4a';

      // Configure and start recording
      await _recorder.start(
        const RecordConfig(
          encoder: AudioEncoder.aacLc,
          bitRate: 128000,
          sampleRate: 44100,
        ),
        path: _currentFilePath!,
      );

      _state = RecorderState.recording;
      _recordDuration = Duration.zero;
      _startDurationTimer();
      notifyListeners();

      debugPrint('Recording started: $_currentFilePath');
      return true;
    } catch (e) {
      debugPrint('Error starting recording: $e');
      _state = RecorderState.idle;
      notifyListeners();
      return false;
    }
  }

  Future<String?> stopRecording() async {
    try {
      _stopDurationTimer();

      if (_state == RecorderState.idle) {
        return null;
      }

      final path = await _recorder.stop();
      _state = RecorderState.idle;
      notifyListeners();

      debugPrint('Recording stopped: $path');
      return path;
    } catch (e) {
      debugPrint('Error stopping recording: $e');
      _state = RecorderState.idle;
      notifyListeners();
      return null;
    }
  }

  Future<void> pauseRecording() async {
    try {
      if (_state != RecorderState.recording) return;

      await _recorder.pause();
      _stopDurationTimer();
      _state = RecorderState.paused;
      notifyListeners();
      debugPrint('Recording paused');
    } catch (e) {
      debugPrint('Error pausing recording: $e');
    }
  }

  Future<void> resumeRecording() async {
    try {
      if (_state != RecorderState.paused) return;

      await _recorder.resume();
      _startDurationTimer();
      _state = RecorderState.recording;
      notifyListeners();
      debugPrint('Recording resumed');
    } catch (e) {
      debugPrint('Error resuming recording: $e');
    }
  }

  Future<void> cancelRecording() async {
    try {
      _stopDurationTimer();
      await _recorder.cancel();
      _state = RecorderState.idle;

      // Delete the file if it exists
      if (_currentFilePath != null) {
        final file = File(_currentFilePath!);
        if (await file.exists()) {
          await file.delete();
          debugPrint('Recording cancelled and file deleted');
        }
      }

      _currentFilePath = null;
      _recordDuration = Duration.zero;
      notifyListeners();
    } catch (e) {
      debugPrint('Error cancelling recording: $e');
      _state = RecorderState.idle;
      notifyListeners();
    }
  }

  void _startDurationTimer() {
    _durationTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      _recordDuration += const Duration(seconds: 1);
      notifyListeners();
    });
  }

  void _stopDurationTimer() {
    _durationTimer?.cancel();
    _durationTimer = null;
  }

  String formatDuration(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, '0');
    final minutes = twoDigits(duration.inMinutes.remainder(60));
    final seconds = twoDigits(duration.inSeconds.remainder(60));
    if (duration.inHours > 0) {
      final hours = twoDigits(duration.inHours);
      return '$hours:$minutes:$seconds';
    }
    return '$minutes:$seconds';
  }

  @override
  Future<void> dispose() async {
    _stopDurationTimer();
    await _recorder.dispose();
    super.dispose();
  }
}
