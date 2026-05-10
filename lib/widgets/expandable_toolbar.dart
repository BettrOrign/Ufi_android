import 'package:flutter/material.dart';

/// A single tool button in the expandable toolbar.
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

/// Reusable expandable input bar with tool buttons.
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
        children: widget.tools.map((tool) {
          return _ToolButton(
            icon: tool.icon,
            label: tool.label,
            color: tool.color ?? colorScheme.primary,
            onTap: tool.onTap,
          );
        }).toList(),
      ),
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

class _ToolButton extends StatelessWidget {
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
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Tooltip(
      message: label,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(12),
          child: Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: colorScheme.secondaryContainer.withOpacity(0.5),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: colorScheme.outlineVariant.withOpacity(0.3),
              ),
            ),
            child: Icon(icon, color: color, size: 24),
          ),
        ),
      ),
    );
  }
}
