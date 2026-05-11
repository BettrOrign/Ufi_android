import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

enum PlayerState {
  idle,
  playing,
  paused,
}

class AudioPlayerService extends ChangeNotifier {
  static const _channel = MethodChannel('com.ufi.agent/audio_player');

  PlayerState _state = PlayerState.idle;
  String? _currentPath;
  Duration _position = Duration.zero;
  Duration _duration = Duration.zero;

  PlayerState get state => _state;
  bool get isPlaying => _state == PlayerState.playing;
  bool get isPaused => _state == PlayerState.paused;
  Duration get position => _position;
  Duration get duration => _duration;
  String? get currentPath => _currentPath;

  AudioPlayerService() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  Future<dynamic> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case 'onPositionChanged':
        _position = Duration(milliseconds: call.arguments as int? ?? 0);
        notifyListeners();
        break;
      case 'onDurationChanged':
        _duration = Duration(milliseconds: call.arguments as int? ?? 0);
        notifyListeners();
        break;
      case 'onComplete':
        _state = PlayerState.idle;
        _position = Duration.zero;
        notifyListeners();
        break;
    }
  }

  Future<bool> play(String path) async {
    try {
      if (_currentPath != path) {
        await stop();
      }
      final result = await _channel.invokeMethod<bool>('play', path);
      if (result == true) {
        _currentPath = path;
        _state = PlayerState.playing;
        notifyListeners();
      }
      return result ?? false;
    } catch (e) {
      debugPrint('Error playing audio: $e');
      return false;
    }
  }

  Future<void> pause() async {
    try {
      await _channel.invokeMethod('pause');
      _state = PlayerState.paused;
      notifyListeners();
    } catch (e) {
      debugPrint('Error pausing audio: $e');
    }
  }

  Future<void> resume() async {
    try {
      await _channel.invokeMethod('resume');
      _state = PlayerState.playing;
      notifyListeners();
    } catch (e) {
      debugPrint('Error resuming audio: $e');
    }
  }

  Future<void> stop() async {
    try {
      await _channel.invokeMethod('stop');
      _state = PlayerState.idle;
      _position = Duration.zero;
      _currentPath = null;
      notifyListeners();
    } catch (e) {
      debugPrint('Error stopping audio: $e');
    }
  }

  Future<void> seek(Duration position) async {
    try {
      await _channel.invokeMethod('seek', position.inMilliseconds);
      _position = position;
      notifyListeners();
    } catch (e) {
      debugPrint('Error seeking audio: $e');
    }
  }

  String formatDuration(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, '0');
    final minutes = twoDigits(duration.inMinutes.remainder(60));
    final seconds = twoDigits(duration.inSeconds.remainder(60));
    return '$minutes:$seconds';
  }

  @override
  void dispose() {
    stop();
    _channel.setMethodCallHandler(null);
    super.dispose();
  }
}
