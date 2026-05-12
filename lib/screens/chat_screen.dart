import 'dart:ui';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/llm_service.dart';
import '../services/audio_recorder_service.dart';
import '../services/audio_player_service.dart';
import '../models/chat_message.dart';
import '../widgets/expandable_toolbar.dart';
import './settings_screen.dart';

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

  void _showFeatureSnackBar(String feature) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('$feature coming soon...'),
        duration: const Duration(seconds: 2),
      ),
    );
  }

  void _handleMicTap(BuildContext context) {
    final recorder = context.read<AudioRecorderService>();
    final llmService = context.read<LLMService>();
    final messenger = ScaffoldMessenger.of(context);

    if (recorder.isRecording) {
      recorder.stopRecording().then((path) {
        if (path != null) {
          llmService.addAudioMessage(path);
          messenger.showSnackBar(
            const SnackBar(
              content: Text('Recording added to chat'),
              duration: Duration(seconds: 2),
            ),
          );
        }
      });
    } else {
      recorder.startRecording().then((success) {
        if (!success) {
          messenger.showSnackBar(
            const SnackBar(
              content: Text(
                  'Failed to start recording. Check microphone permission.'),
              duration: Duration(seconds: 3),
            ),
          );
        }
      });
    }
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
            color: colorScheme.primary,
            letterSpacing: 2.0,
          ),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const SettingsScreen()),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.delete_sweep_outlined),
            onPressed: () => context.read<LLMService>().clearMessages(),
          ),
        ],
      ),
      body: Stack(
        children: [
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
                    if (service.apiKey.isEmpty && service.messages.isEmpty) {
                      return Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              Icons.key_outlined,
                              size: 48,
                              color: colorScheme.primary.withOpacity(0.5),
                            ),
                            const SizedBox(height: 16),
                            const Text(
                              'Configure API Key',
                              style: TextStyle(
                                fontFamily: 'JetBrainsMono',
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            const SizedBox(height: 8),
                            const Text(
                              'Tap the settings icon to add your Groq API key',
                              style: TextStyle(
                                fontFamily: 'JetBrainsMono',
                                color: Colors.grey,
                              ),
                            ),
                            const SizedBox(height: 24),
                            ElevatedButton.icon(
                              onPressed: () => Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (_) => const SettingsScreen(),
                                ),
                              ),
                              icon: const Icon(Icons.settings),
                              label: const Text('Open Settings'),
                            ),
                          ],
                        ),
                      );
                    }

                    if (service.error != null && service.error!.isNotEmpty) {
                      return Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              Icons.error_outline,
                              size: 48,
                              color: colorScheme.error.withOpacity(0.7),
                            ),
                            const SizedBox(height: 16),
                            Text(
                              'Error',
                              style: TextStyle(
                                fontFamily: 'JetBrainsMono',
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                                color: colorScheme.error,
                              ),
                            ),
                            const SizedBox(height: 8),
                            Padding(
                              padding:
                                  const EdgeInsets.symmetric(horizontal: 24),
                              child: Text(
                                service.error!,
                                textAlign: TextAlign.center,
                                style: const TextStyle(
                                  fontFamily: 'JetBrainsMono',
                                  color: Colors.grey,
                                ),
                              ),
                            ),
                          ],
                        ),
                      );
                    }

                    if (service.messages.isEmpty) {
                      return Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              Icons.chat_bubble_outline,
                              size: 48,
                              color: colorScheme.primary.withOpacity(0.3),
                            ),
                            const SizedBox(height: 16),
                            const Text(
                              'Waiting for command...',
                              style: TextStyle(
                                fontFamily: 'JetBrainsMono',
                                color: Colors.grey,
                              ),
                            ),
                          ],
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
              ExpandableInputBar(
                controller: _controller,
                onSend: () => _sendMessage(context.read<LLMService>()),
                tools: [
                  ToolbarTool(
                    icon: Icons.mic,
                    label: 'Voice',
                    onTap: () => _handleMicTap(context),
                  ),
                  ToolbarTool(
                    icon: Icons.image,
                    label: 'Photo',
                    onTap: () => _showFeatureSnackBar('Photo'),
                  ),
                  ToolbarTool(
                    icon: Icons.attach_file,
                    label: 'File',
                    onTap: () => _showFeatureSnackBar('File'),
                  ),
                  ToolbarTool(
                    icon: Icons.delete_outline,
                    label: 'Clear',
                    onTap: () => context.read<LLMService>().clearMessages(),
                  ),
                ],
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _MessageBubble extends StatefulWidget {
  final ChatMessage message;

  const _MessageBubble({required this.message});

  @override
  State<_MessageBubble> createState() => _MessageBubbleState();
}

class _MessageBubbleState extends State<_MessageBubble>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _slideAnimation;
  late Animation<double> _fadeAnimation;
  late Animation<double> _scaleAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 400),
      vsync: this,
    );

    _slideAnimation = Tween<double>(begin: 50, end: 0).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOutQuart),
    );

    _fadeAnimation = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOut),
    );

    _scaleAnimation = Tween<double>(begin: 0.85, end: 1).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOutQuart),
    );

    _controller.forward();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final isUser = widget.message.isUser;
    final theme = Theme.of(context);

    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        return Transform.translate(
          offset: Offset(isUser ? _slideAnimation.value : -_slideAnimation.value, 0),
          child: Opacity(
            opacity: _fadeAnimation.value,
            child: Transform.scale(
              scale: _scaleAnimation.value,
              alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
              child: child,
            ),
          ),
        );
      },
      child: Align(
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
                    colors: [
                      theme.colorScheme.primary,
                      theme.colorScheme.primaryContainer,
                    ],
                  )
                : null,
            color: isUser
                ? null
                : theme.colorScheme.secondaryContainer.withOpacity(0.8),
            boxShadow: [
              BoxShadow(
                color: (isUser
                        ? theme.colorScheme.primary
                        : theme.colorScheme.secondaryContainer)
                    .withOpacity(0.15),
                blurRadius: 8,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: widget.message.hasAudio
              ? _AudioPlayerWidget(audioPath: widget.message.audioPath!)
              : Text(
                  widget.message.content,
                  style: TextStyle(
                    fontFamily: 'JetBrainsMono',
                    fontSize: 14,
                    fontWeight: isUser ? FontWeight.bold : FontWeight.normal,
                    color: isUser
                        ? theme.colorScheme.onPrimary
                        : theme.colorScheme.onSecondaryContainer,
                  ),
                ),
        ),
      ),
    );
  }
}

class _AudioPlayerWidget extends StatelessWidget {
  final String audioPath;

  const _AudioPlayerWidget({required this.audioPath});

  @override
  Widget build(BuildContext context) {
    return Consumer<AudioPlayerService>(
      builder: (context, player, _) {
        final isPlaying = player.isPlaying && player.currentPath == audioPath;
        final position = isPlaying ? player.position : Duration.zero;
        final duration = isPlaying ? player.duration : Duration.zero;

        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton(
                  icon: Icon(
                    isPlaying ? Icons.pause_circle : Icons.play_circle,
                    color: Theme.of(context).colorScheme.primary,
                    size: 40,
                  ),
                  onPressed: () {
                    if (isPlaying) {
                      player.pause();
                    } else if (player.currentPath == audioPath &&
                        player.isPaused) {
                      player.resume();
                    } else {
                      player.play(audioPath);
                    }
                  },
                ),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (isPlaying || player.currentPath == audioPath) ...[
                        SliderTheme(
                          data: SliderThemeData(
                            trackHeight: 4,
                            thumbShape: const RoundSliderThumbShape(
                                enabledThumbRadius: 6),
                            overlayShape: const RoundSliderOverlayShape(
                                overlayRadius: 12),
                          ),
                          child: Slider(
                            value: duration.inMilliseconds > 0
                                ? position.inMilliseconds /
                                    duration.inMilliseconds
                                : 0,
                            onChanged: (value) {
                              final newPosition = Duration(
                                milliseconds:
                                    (value * duration.inMilliseconds).round(),
                              );
                              player.seek(newPosition);
                            },
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 16),
                          child: Text(
                            '${_formatDuration(position)} / ${_formatDuration(duration)}',
                            style: TextStyle(
                              fontFamily: 'JetBrainsMono',
                              fontSize: 10,
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSecondaryContainer,
                            ),
                          ),
                        ),
                      ] else
                        const Text(
                          'Audio message',
                          style: TextStyle(
                            fontFamily: 'JetBrainsMono',
                            fontSize: 12,
                          ),
                        ),
                    ],
                  ),
                ),
                IconButton(
                  icon: Icon(
                    Icons.delete_outline,
                    color: Theme.of(context).colorScheme.error,
                    size: 24,
                  ),
                  onPressed: () {
                    if (player.currentPath == audioPath) {
                      player.stop();
                    }
                    context.read<LLMService>().deleteAudioMessage(audioPath);
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Recording deleted'),
                        duration: Duration(seconds: 2),
                      ),
                    );
                  },
                ),
              ],
            ),
          ],
        );
      },
    );
  }

  String _formatDuration(Duration d) {
    String twoDigits(int n) => n.toString().padLeft(2, '0');
    return '${twoDigits(d.inMinutes.remainder(60))}:${twoDigits(d.inSeconds.remainder(60))}';
  }
}
