# Ufi Agent

LLM Chat Agent mobile application built with Flutter.

## Setup

### 1. Configure API Key

Create `lib/services/api_config.dart`:

```dart
class ApiConfig {
  static const String apiKey = 'YOUR_GROQ_API_KEY';
  static const String model = 'qwen/qwen3-32b';
  static const String apiUrl = 'https://api.groq.com/openai/v1/chat/completions';
}
```

### 2. Run locally

```bash
cd Apk
flutter pub get
flutter run
```

### 3. Build APK

```bash
flutter build apk --release
```

APK location: `build/app/outputs/flutter-apk/app-release.apk`

## Project Structure

```
Apk/
├── lib/
│   ├── main.dart              # App entry point
│   ├── models/
│   │   └── chat_message.dart  # Message model
│   ├── services/
│   │   └── llm_service.dart   # Groq API integration
│   └── screens/
│       └── chat_screen.dart    # Main chat UI
├── pubspec.yaml
└── analysis_options.yaml
```

## Features

- Send messages to LLM (Groq API)
- Display conversation history
- Loading indicator while waiting for response
- Error handling with on-screen messages
- Clear conversation button

## Requirements

- Flutter SDK 3.7+
- Groq API key
- Internet permission (AndroidManifest.xml)

## Android Permissions

Add to `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET"/>
```
