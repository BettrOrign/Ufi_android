import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/audio_recorder_service.dart';

class ToolbarTool {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final Color? color;

  const ToolbarTool({
    required this.icon,
    required this.label,
    required this.onTap,
    this.color,
  });
}

class ExpandableInputBar extends StatefulWidget {
  final TextEditingController controller;
  final VoidCallback onSend;
  final List<ToolbarTool> tools;
  final bool? isExpanded;
  final ValueChanged<bool>? onExpandedChanged;
  final String hintText;

  const ExpandableInputBar({
    super.key,
    required this.controller,
    required this.onSend,
    this.tools = const [],
    this.isExpanded,
    this.onExpandedChanged,
    this.hintText = '> system_input',
  });

  @override
  State<ExpandableInputBar> createState() => _ExpandableInputBarState();
}

class _ExpandableInputBarState extends State<ExpandableInputBar> {
  late bool _isExpanded;

  @override
  void initState() {
    super.initState();
    _isExpanded = widget.isExpanded ?? false;
  }

  @override
  void didUpdateWidget(ExpandableInputBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isExpanded != null && widget.isExpanded != _isExpanded) {
      setState(() => _isExpanded = widget.isExpanded!);
    }
  }

  void _toggle() {
    final newState = !_isExpanded;
    setState(() => _isExpanded = newState);
    widget.onExpandedChanged?.call(newState);
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const _RecordingIndicator(),
        AnimatedSize(
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeInOut,
          alignment: Alignment.topCenter,
          child: _isExpanded && widget.tools.isNotEmpty
              ? _buildToolsRow(colorScheme)
              : const SizedBox.shrink(),
        ),
        AnimatedContainer(
          duration: const Duration(milliseconds: 300),
          height: _isExpanded ? 1 : 0,
          margin: EdgeInsets.symmetric(horizontal: _isExpanded ? 16 : 0),
          color: colorScheme.outlineVariant.withOpacity(0.3),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
          child: Row(
            children: [
              _ToggleButton(
                isExpanded: _isExpanded,
                onToggle: _toggle,
                colorScheme: colorScheme,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Container(
                  decoration: BoxDecoration(
                    color: colorScheme.secondaryContainer.withOpacity(0.5),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: colorScheme.outlineVariant.withOpacity(0.3),
                    ),
                  ),
                  child: TextField(
                    controller: widget.controller,
                    style: const TextStyle(fontFamily: 'JetBrainsMono'),
                    decoration: InputDecoration(
                      hintText: widget.hintText,
                      border: InputBorder.none,
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 20,
                        vertical: 12,
                      ),
                      hintStyle: TextStyle(
                        fontFamily: 'JetBrainsMono',
                        color: colorScheme.onSurface.withOpacity(0.3),
                      ),
                    ),
                    onSubmitted: (_) => widget.onSend(),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              FloatingActionButton.small(
                onPressed: widget.onSend,
                elevation: 0,
                backgroundColor: colorScheme.primary,
                child: Icon(Icons.bolt, color: colorScheme.onPrimary),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildToolsRow(ColorScheme colorScheme) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          ...widget.tools.map((tool) {
            return _ToolButton(
              icon: tool.icon,
              label: tool.label,
              color: tool.color ?? colorScheme.primary,
              onTap: tool.onTap,
            );
          }),
        ],
      ),
    );
  }
}

class _RecordingIndicator extends StatelessWidget {
  const _RecordingIndicator();

  @override
  Widget build(BuildContext context) {
    return Consumer<AudioRecorderService>(
      builder: (context, recorder, _) {
        if (!recorder.isRecording) {
          return const SizedBox.shrink();
        }

        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          margin: const EdgeInsets.only(bottom: 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 12,
                height: 12,
                decoration: const BoxDecoration(
                  color: Colors.red,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                'Recording ${recorder.formatDuration(recorder.recordDuration)}',
                style: const TextStyle(
                  fontFamily: 'JetBrainsMono',
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: Colors.red,
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _ToggleButton extends StatelessWidget {
  final bool isExpanded;
  final VoidCallback onToggle;
  final ColorScheme colorScheme;

  const _ToggleButton({
    required this.isExpanded,
    required this.onToggle,
    required this.colorScheme,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onToggle,
        borderRadius: BorderRadius.circular(12),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeInOut,
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: isExpanded
                ? colorScheme.primary.withOpacity(0.15)
                : colorScheme.secondaryContainer.withOpacity(0.5),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: isExpanded
                  ? colorScheme.primary.withOpacity(0.3)
                  : colorScheme.outlineVariant.withOpacity(0.3),
            ),
          ),
          child: AnimatedRotation(
            duration: const Duration(milliseconds: 300),
            turns: isExpanded ? 0.125 : 0,
            child: Icon(
              Icons.add_circle_outline,
              color: colorScheme.primary,
              size: 24,
            ),
          ),
        ),
      ),
    );
  }
}

class _ToolButton extends StatefulWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;

  const _ToolButton({
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
  });

  @override
  State<_ToolButton> createState() => _ToolButtonState();
}

class _ToolButtonState extends State<_ToolButton>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 200),
      vsync: this,
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Tooltip(
      message: widget.label,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: () {
            _controller.forward().then((_) {
              _controller.reverse();
            });
            widget.onTap();
          },
          onHover: (hovering) {
            if (hovering) {
              _controller.forward();
            } else {
              _controller.reverse();
            }
          },
          borderRadius: BorderRadius.circular(12),
          child: ScaleTransition(
            scale: Tween<double>(begin: 1.0, end: 1.1).animate(
              CurvedAnimation(parent: _controller, curve: Curves.easeOutQuart),
            ),
            child: Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: colorScheme.secondaryContainer.withOpacity(0.5),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: colorScheme.outlineVariant.withOpacity(0.3),
                ),
              ),
              child: Icon(widget.icon, color: widget.color, size: 24),
            ),
          ),
        ),
      ),
    );
  }
}
