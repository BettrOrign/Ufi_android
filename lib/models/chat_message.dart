class ChatMessage {
  final String role;
  final String content;
  final DateTime timestamp;
  final String? audioPath;

  ChatMessage({
    required this.role,
    required this.content,
    DateTime? timestamp,
    this.audioPath,
  }) : timestamp = timestamp ?? DateTime.now();

  bool get isUser => role == 'user';
  bool get isAssistant => role == 'assistant';
  bool get hasAudio => audioPath != null && audioPath!.isNotEmpty;
}
