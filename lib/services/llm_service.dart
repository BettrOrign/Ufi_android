import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../models/chat_message.dart';

class LLMService extends ChangeNotifier {
  final List<ChatMessage> _messages = [];
  bool _isLoading = false;
  String? _error;
  String _apiKey = '';

  final String _model = 'qwen/qwen3-32b';
  final String _apiUrl = 'https://api.groq.com/openai/v1/chat/completions';

  LLMService() {
    _loadApiKey();
  }

  Future<void> _loadApiKey() async {
    final prefs = await SharedPreferences.getInstance();
    _apiKey = prefs.getString('groq_api_key') ?? '';
    notifyListeners();
  }

  Future<void> setApiKey(String key) async {
    _apiKey = key;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('groq_api_key', key);
    notifyListeners();
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
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final assistantMessage =
            data['choices'][0]['message']['content'] as String;
        _messages
            .add(ChatMessage(role: 'assistant', content: assistantMessage));
      } else {
        _error = 'API Error: ${response.statusCode}';
      }
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
    notifyListeners();
  }

  void clearMessages() {
    _messages.clear();
    _error = null;
    notifyListeners();
  }
}
