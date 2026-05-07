import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/llm_service.dart';
import '../models/chat_message.dart';

class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final TextEditingController _controller = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  @override
  void dispose() {
    _controller.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _sendMessage(LLMService service) {
    final text = _controller.text.trim();
    if (text.isEmpty) return;

    _controller.clear();
    service.sendMessage(text);
    _scrollToBottom();
  }

  void _scrollToBottom() {
    Future.delayed(const Duration(milliseconds: 100), () {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOutQuart,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      backgroundColor: colorScheme.surface,
      extendBodyBehindAppBar: true,
      appBar: AppBar(
        elevation: 0,
        scrolledUnderElevation: 0,
        backgroundColor: colorScheme.surface.withOpacity(0.7),
        flexibleSpace: ClipRect(
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
            child: Container(color: Colors.transparent),
          ),
        ),
        title: Text(
          'UFI AGENT',
          style: TextStyle(
            fontWeight: FontWeight.w900,
            fontFamily: 'JetBrainsMono',
            color: colorScheme.primary, // Твой Aqua акцент
            letterSpacing: 2.0,
          ),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.delete_sweep_outlined),
            onPressed: () => context.read<LLMService>().clearMessages(),
          ),
        ],
      ),
      body: Stack(
        children: [
          // Фоновый градиент для глубины
          Positioned.fill(
            child: Container(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    colorScheme.primary.withOpacity(0.05),
                    colorScheme.surface,
                  ],
                ),
              ),
            ),
          ),
          Column(
            children: [
              Expanded(
                child: Consumer<LLMService>(
                  builder: (context, service, _) {
                    if (service.messages.isEmpty) {
                      return const Center(
                        child: Text(
                          'Waiting for command...',
                          style: TextStyle(fontFamily: 'JetBrainsMono', color: Colors.grey),
                        ),
                      );
                    }
                    return ListView.builder(
                      controller: _scrollController,
                      padding: const EdgeInsets.fromLTRB(16, 110, 16, 16),
                      itemCount: service.messages.length,
                      itemBuilder: (context, index) => _MessageBubble(
                        message: service.messages[index],
                      ),
                    );
                  },
                ),
              ),
              _InputBar(
                controller: _controller,
                onSend: () => _sendMessage(context.read<LLMService>()),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  final ChatMessage message;
  const _MessageBubble({required this.message});

  @override
  Widget build(BuildContext context) {
    final isUser = message.isUser;
    final theme = Theme.of(context);

    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 16),
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
        constraints: BoxConstraints(
          maxWidth: MediaQuery.of(context).size.width * 0.8,
        ),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(20),
            topRight: const Radius.circular(20),
            bottomLeft: Radius.circular(isUser ? 20 : 4),
            bottomRight: Radius.circular(isUser ? 4 : 20),
          ),
          gradient: isUser
              ? LinearGradient(
                  colors: [theme.colorScheme.primary, theme.colorScheme.primaryContainer],
                )
              : null,
          color: isUser ? null : theme.colorScheme.secondaryContainer.withOpacity(0.8),
        ),
        child: Text(
          message.content,
          style: TextStyle(
            fontFamily: 'JetBrainsMono',
            fontSize: 14,
            fontWeight: isUser ? FontWeight.bold : FontWeight.normal,
            color: isUser ? theme.colorScheme.onPrimary : theme.colorScheme.onSecondaryContainer,
          ),
        ),
      ),
    );
  }
}

class _InputBar extends StatelessWidget {
  final TextEditingController controller;
  final VoidCallback onSend;

  const _InputBar({required this.controller, required this.onSend});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      child: Row(
        children: [
          Expanded(
            child: Container(
              decoration: BoxDecoration(
                color: colorScheme.secondaryContainer.withOpacity(0.5),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: colorScheme.outlineVariant.withOpacity(0.3)),
              ),
              child: TextField(
                controller: controller,
                style: const TextStyle(fontFamily: 'JetBrainsMono'),
                decoration: const InputDecoration(
                  hintText: '> system_input',
                  border: InputBorder.none,
                  contentPadding: EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                ),
                onSubmitted: (_) => onSend(),
              ),
            ),
          ),
          const SizedBox(width: 12),
          FloatingActionButton.small(
            onPressed: onSend,
            elevation: 0,
            backgroundColor: colorScheme.primary,
            child: Icon(Icons.bolt, color: colorScheme.onPrimary),
          ),
        ],
      ),
    );
  }
}
