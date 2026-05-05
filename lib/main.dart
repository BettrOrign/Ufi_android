import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'services/llm_service.dart';
import 'screens/chat_screen.dart';

void main() {
  runApp(
    ChangeNotifierProvider(
      create: (_) => LLMService(),
      child: const UfiAgentApp(),
    ),
  );
}

class UfiAgentApp extends StatelessWidget {
  const UfiAgentApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Ufi Agent',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const ChatScreen(),
    );
  }
}
