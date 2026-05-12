import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../models/chat_message.dart';

class LLMService extends ChangeNotifier {
  final List<ChatMessage> _messages = [];
  final Set<String> _pendingAudioFiles = {};
  bool _isLoading = false;
  String? _error;
  String _apiKey = '';

  final String _model = 'qwen/qwen3-32b';
  final String _apiUrl = 'https://api.groq.com/openai/v1/chat/completions';

  LLMService() {
    _loadApiKey();
  }

  Future<void> _loadApiKey() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      _apiKey = prefs.getString('groq_api_key') ?? '';
      notifyListeners();
    } catch (e) {
      debugPrint('Error loading API key: $e');
    }
  }

  Future<void> setApiKey(String key) async {
    try {
      _apiKey = key;
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('groq_api_key', key);
      notifyListeners();
    } catch (e) {
      debugPrint('Error saving API key: $e');
      _error = 'Failed to save API key';
      notifyListeners();
    }
  }

  String get apiKey => _apiKey;

  List<ChatMessage> get messages => List.unmodifiable(_messages);
  bool get isLoading => _isLoading;
  String? get error => _error;

  Future<void> sendMessage(String content) async {
    if (content.trim().isEmpty) return;
    if (_apiKey.isEmpty) {
      _error = 'API key not configured';
      notifyListeners();
      return;
    }

    // Add user message
    _messages.add(ChatMessage(role: 'user', content: content));
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      // Build messages list for API
      final messagesPayload = _messages
          .map((m) => {
                'role': m.role,
                'content': m.content,
              })
          .toList();

      final response = await http.post(
        Uri.parse(_apiUrl),
        headers: {
          'Authorization': 'Bearer $_apiKey',
          'Content-Type': 'application/json',
        },
        body: jsonEncode({
          'model': _model,
          'messages': messagesPayload,
        }),
      ).timeout(
        const Duration(seconds: 30),
        onTimeout: () => throw TimeoutException('API request timeout'),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final assistantMessage =
            data['choices'][0]['message']['content'] as String;
        _messages
            .add(ChatMessage(role: 'assistant', content: assistantMessage));
        
        // Clean up any pending audio files after successful message
        await _cleanupAudioFiles();
      } else if (response.statusCode == 401) {
        _error = 'Invalid API key';
      } else if (response.statusCode == 429) {
        _error = 'API rate limit exceeded';
      } else {
        _error = 'API Error: ${response.statusCode}';
      }
    } on TimeoutException {
      _error = 'Request timeout - check your connection';
    } catch (e) {
      _error = 'Network error: $e';
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  void addAudioMessage(String audioPath) {
    _messages.add(ChatMessage(
        role: 'user', content: '🎤 Voice message', audioPath: audioPath));
    _pendingAudioFiles.add(audioPath);
    notifyListeners();
  }

  Future<void> _cleanupAudioFiles() async {
    for (final filePath in _pendingAudioFiles) {
      try {
        final file = File(filePath);
        if (await file.exists()) {
          await file.delete();
          debugPrint('Cleaned up audio file: $filePath');
        }
      } catch (e) {
        debugPrint('Error deleting audio file $filePath: $e');
      }
    }
    _pendingAudioFiles.clear();
  }

  Future<void> deleteAudioMessage(String audioPath) async {
    try {
      final file = File(audioPath);
      if (await file.exists()) {
        await file.delete();
      }
      _pendingAudioFiles.remove(audioPath);
      notifyListeners();
    } catch (e) {
      debugPrint('Error deleting audio message: $e');
    }
  }

  void clearMessages() {
    _messages.clear();
    _error = null;
    _pendingAudioFiles.clear();
    notifyListeners();
  }

  @override
  void dispose() {
    _cleanupAudioFiles();
    super.dispose();
  }
}
