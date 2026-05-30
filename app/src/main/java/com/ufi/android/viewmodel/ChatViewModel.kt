package com.ufi.android.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ufi.android.audio.AudioCapturer
import com.ufi.android.audio.AudioPlayer
import com.ufi.android.data.ToolDispatcher
import com.ufi.android.data.model.ChatMessage
import com.ufi.android.data.model.MessageRole
import com.ufi.android.data.model.Settings
import com.ufi.android.data.model.VOICES
import com.ufi.android.data.websocket.ConnectionState
import com.ufi.android.data.websocket.GeminiWebSocket
import com.ufi.android.data.websocket.ToolCallInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ChatViewModel"
        private const val PREFS_NAME = "ufi_settings"
        private const val MAX_MESSAGES = 100
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, 0)

    private val geminiWs = GeminiWebSocket(viewModelScope)
    val audioCapturer = AudioCapturer(viewModelScope)
    val audioPlayer = AudioPlayer(viewModelScope)

    private val toolDispatcher = ToolDispatcher(
        scope = viewModelScope,
        onDisplayText = { text ->
            addMessage(ChatMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                text = text,
            ))
        },
        onDisplayCard = { type, data ->
            val text = data.optString("text", data.optString("code", ""))
            addMessage(ChatMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                text = "[${type.uppercase()}]\n$text",
            ))
        },
        onShowImage = { source ->
            addMessage(ChatMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                text = "🖼️ $source",
            ))
        },
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _lastRawMessage = MutableStateFlow<String?>(null)
    val lastRawMessage: StateFlow<String?> = _lastRawMessage.asStateFlow()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private var accumulatedAssistantText = ""
    private var currentAssistantMsgId: String? = null

    val audioLevel = audioCapturer.audioLevel
    val isPlaying = audioPlayer.isPlaying

    init {
        val s = _settings.value
        toolDispatcher.serverUrl = s.serverUrl

        if (s.hasApiKey) connect()

        viewModelScope.launch {
            geminiWs.connectionState.collect { _connectionState.value = it }
        }
        viewModelScope.launch {
            geminiWs.errorMessage.collect { _errorMessage.value = it }
        }
        viewModelScope.launch {
            geminiWs.lastRawMessage.collect { _lastRawMessage.value = it }
        }
        viewModelScope.launch {
            geminiWs.incomingText.collect { text ->
                if (text.isNotBlank()) {
                    _isThinking.value = false
                    accumulatedAssistantText = text
                    displayAssistantText()
                }
            }
        }
        viewModelScope.launch {
            geminiWs.incomingAudio.collect { audio ->
                if (audio != null) {
                    audioPlayer.enqueuePcm(audio.pcmData)
                }
            }
        }
        viewModelScope.launch {
            geminiWs.toolCalls.collect { tools ->
                if (tools.isNotEmpty()) {
                    _isThinking.value = true
                    handleToolCalls(tools)
                }
            }
        }
        viewModelScope.launch {
            geminiWs.turnComplete.collect { complete ->
                if (complete) finalizeAssistantTurn()
            }
        }
        viewModelScope.launch {
            geminiWs.inputTranscription.collect { transcription ->
                if (transcription.isNotBlank()) {
                    addMessage(ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.USER,
                        text = transcription,
                    ))
                    _isThinking.value = true
                }
            }
        }
    }

    fun connect() {
        val s = _settings.value
        if (!s.hasApiKey) return

        geminiWs.configure(s.apiKey, s.systemPrompt, s.voiceName)
        geminiWs.connect()
        _connectionState.value = ConnectionState.CONNECTING
    }

    fun disconnect() {
        geminiWs.disconnect()
        audioCapturer.stopCapture()
        audioPlayer.stopPlayback()
        _isListening.value = false
        _isThinking.value = false
    }

    fun sendText(text: String) {
        if (!_settings.value.hasApiKey) return
        if (text.isBlank()) return

        addMessage(ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            text = text,
        ))
        _isThinking.value = true
        accumulatedAssistantText = ""
        currentAssistantMsgId = null
        geminiWs.sendText(text)
    }

    fun toggleMic() {
        if (_isListening.value) {
            audioCapturer.stopCapture()
            _isListening.value = false
        } else {
            if (!_settings.value.hasApiKey) return
            audioCapturer.onAudioChunk = { chunk ->
                geminiWs.sendAudioChunk(chunk)
            }
            audioCapturer.startCapture()
            _isListening.value = true
            _isThinking.value = true
            accumulatedAssistantText = ""
            currentAssistantMsgId = null
        }
    }

    fun updateSettings(newSettings: Settings) {
        _settings.value = newSettings
        saveSettings(newSettings)
        toolDispatcher.serverUrl = newSettings.serverUrl
        if (newSettings.hasApiKey && _connectionState.value != ConnectionState.CONNECTED) {
            connect()
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        accumulatedAssistantText = ""
        currentAssistantMsgId = null
        _isThinking.value = false
    }

    private fun handleToolCalls(toolCalls: List<ToolCallInfo>) {
        toolDispatcher.dispatch(toolCalls) { responses ->
            geminiWs.sendToolResponse(responses)
        }
    }

    private fun addMessage(message: ChatMessage) {
        val current = _messages.value.toMutableList()
        current.add(message)
        if (current.size > MAX_MESSAGES) {
            current.removeAt(0)
        }
        _messages.value = current
    }

    private fun displayAssistantText() {
        val current = _messages.value.toMutableList()
        if (currentAssistantMsgId == null) {
            val msgId = UUID.randomUUID().toString()
            currentAssistantMsgId = msgId
            current.add(ChatMessage(
                id = msgId,
                role = MessageRole.ASSISTANT,
                text = accumulatedAssistantText,
            ))
        } else {
            val idx = current.indexOfLast { it.id == currentAssistantMsgId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(text = accumulatedAssistantText)
            }
        }
        _messages.value = current
    }

    private fun finalizeAssistantTurn() {
        currentAssistantMsgId = null
        accumulatedAssistantText = ""
        _isThinking.value = false
    }

    private fun loadSettings(): Settings {
        return Settings(
            apiKey = prefs.getString("api_key", "") ?: "",
            voiceName = prefs.getString("voice_name", "Charon") ?: "Charon",
            systemPrompt = prefs.getString("system_prompt", DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT,
            serverUrl = prefs.getString("server_url", "") ?: "",
        )
    }

    private fun saveSettings(settings: Settings) {
        prefs.edit()
            .putString("api_key", settings.apiKey)
            .putString("voice_name", settings.voiceName)
            .putString("system_prompt", settings.systemPrompt)
            .putString("server_url", settings.serverUrl)
            .apply()
    }

    private val DEFAULT_SYSTEM_PROMPT: String
        get() = com.ufi.android.data.model.DEFAULT_SYSTEM_PROMPT

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
