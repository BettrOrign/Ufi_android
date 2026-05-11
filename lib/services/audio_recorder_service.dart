import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

enum RecorderState {
  idle,
  recording,
  paused,
}

class AudioRecorderService extends ChangeNotifier {
  static const _channel = MethodChannel('com.ufi.agent/audio_recorder');

  RecorderState _state = RecorderState.idle;
  String? _currentFilePath;
  Duration _recordDuration = Duration.zero;

  RecorderState get state => _state;
  bool get isRecording => _state == RecorderState.recording;
  bool get isPaused => _state == RecorderState.paused;
  bool get isIdle => _state == RecorderState.idle;
  Duration get recordDuration => _recordDuration;
  String? get currentFilePath => _currentFilePath;

  AudioRecorderService() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  Future<dynamic> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'onDurationChanged':
        final duration = call.arguments as int?;
        if (duration != null) {
          _recordDuration = Duration(milliseconds: duration);
          notifyListeners();
        }
        break;
    }
  }

  Future<bool> checkPermission() async {
    try {
      final result = await _channel.invokeMethod<bool>('checkPermission');
      return result ?? false;
    } catch (e) {
      debugPrint('Error checking permission: $e');
      return false;
    }
  }

  Future<void> requestPermission() async {
    try {
      await _channel.invokeMethod('requestPermission');
    } catch (e) {
      debugPrint('Error requesting permission: $e');
    }
  }

  Future<bool> startRecording() async {
    try {
      if (!await checkPermission()) {
        await requestPermission();
        await Future.delayed(const Duration(milliseconds: 500));
        if (!await checkPermission()) {
          debugPrint('Microphone permission denied');
          return false;
        }
      }

      if (_state != RecorderState.idle) {
        await stopRecording();
      }

      final result = await _channel.invokeMethod<bool>('startRecording');
      if (result == true) {
        _state = RecorderState.recording;
        _recordDuration = Duration.zero;
        notifyListeners();
        debugPrint('Recording started');
        return true;
      }
      return false;
    } catch (e) {
      debugPrint('Error starting recording: $e');
      _state = RecorderState.idle;
      notifyListeners();
      return false;
    }
  }

  Future<String?> stopRecording() async {
    try {
      if (_state == RecorderState.idle) {
        return null;
      }

      final path = await _channel.invokeMethod<String>('stopRecording');
      _state = RecorderState.idle;
      _currentFilePath = path;
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
      await _channel.invokeMethod('pauseRecording');
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
      await _channel.invokeMethod('resumeRecording');
      _state = RecorderState.recording;
      notifyListeners();
      debugPrint('Recording resumed');
    } catch (e) {
      debugPrint('Error resuming recording: $e');
    }
  }

  Future<void> cancelRecording() async {
    try {
      await _channel.invokeMethod('cancelRecording');
      _state = RecorderState.idle;
      _currentFilePath = null;
      _recordDuration = Duration.zero;
      notifyListeners();
      debugPrint('Recording cancelled');
    } catch (e) {
      debugPrint('Error cancelling recording: $e');
      _state = RecorderState.idle;
      notifyListeners();
    }
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
  void dispose() {
    _channel.setMethodCallHandler(null);
    super.dispose();
  }
}
