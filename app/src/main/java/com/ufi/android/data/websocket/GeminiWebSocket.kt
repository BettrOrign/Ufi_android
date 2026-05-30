package com.ufi.android.data.websocket

import android.util.Base64
import android.util.Log
import com.ufi.android.data.model.ToolDefinitions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

data class GeminiAudioChunk(
    val pcmData: ShortArray,
    val sampleRate: Int,
)

data class GeminiResponse(
    val text: String = "",
    val audio: List<GeminiAudioChunk> = emptyList(),
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val turnComplete: Boolean = false,
    val inputTranscription: String = "",
)

data class ToolCallInfo(
    val id: String,
    val name: String,
    val args: JSONObject,
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

class GeminiWebSocket(
    private val scope: CoroutineScope,
) {
    companion object {
        private const val WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val TAG = "GeminiWS"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val AUDIO_SAMPLE_RATE = 16000
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var apiKey: String = ""
    private var systemPrompt: String = ""
    private var voiceName: String = "Charon"

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _incomingText = MutableStateFlow("")
    val incomingText: StateFlow<String> = _incomingText.asStateFlow()

    private val _incomingAudio = MutableStateFlow<GeminiAudioChunk?>(null)
    val incomingAudio: StateFlow<GeminiAudioChunk?> = _incomingAudio.asStateFlow()

    private val _toolCalls = MutableStateFlow<List<ToolCallInfo>>(emptyList())
    val toolCalls: StateFlow<List<ToolCallInfo>> = _toolCalls.asStateFlow()

    private val _turnComplete = MutableStateFlow(false)
    val turnComplete: StateFlow<Boolean> = _turnComplete.asStateFlow()

    private val _inputTranscription = MutableStateFlow("")
    val inputTranscription: StateFlow<String> = _inputTranscription.asStateFlow()

    private val accumulatedAudio = mutableListOf<GeminiAudioChunk>()

    fun configure(apiKey: String, systemPrompt: String, voiceName: String) {
        this.apiKey = apiKey
        this.systemPrompt = systemPrompt
        this.voiceName = voiceName
    }

    fun connect() {
        if (apiKey.isBlank()) {
            Log.e(TAG, "API key not configured")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        disconnect()
        _connectionState.value = ConnectionState.CONNECTING
        _incomingText.value = ""
        _incomingAudio.value = null
        _toolCalls.value = emptyList()
        _turnComplete.value = false
        _inputTranscription.value = ""
        accumulatedAudio.clear()

        val request = Request.Builder()
            .url(WS_URL)
            .header("X-Goog-Api-Key", apiKey)
            .build()

        Log.d(TAG, "Connecting to Gemini WS...")
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                _connectionState.value = ConnectionState.CONNECTED
                _errorMessage.value = null
                sendSetup(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                val detail = when {
                    t.message?.contains("Unable to resolve host") == true ->
                        "Network: cannot reach Gemini API (no internet?)"
                    t.message?.contains("SSL") == true ->
                        "SSL error: ${t.message}"
                    response != null ->
                        "HTTP ${response.code}: ${response.message}"
                    else ->
                        t.message ?: "Unknown error"
                }
                Log.e(TAG, "WebSocket failure: $detail", t)
                _errorMessage.value = detail
                _connectionState.value = ConnectionState.ERROR
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client closing")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _incomingText.value = ""
        _incomingAudio.value = null
        _toolCalls.value = emptyList()
        _turnComplete.value = false
        _inputTranscription.value = ""
        accumulatedAudio.clear()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            connect()
        }
    }

    private fun sendSetup(ws: WebSocket) {
        val setup = JSONObject()
        val setupInner = JSONObject()
        setupInner.put("model", "models/gemini-2.5-flash-native-audio-latest")

        val systemInstruction = JSONObject()
        val parts = JSONArray()
        parts.put(JSONObject().put("text", systemPrompt))
        systemInstruction.put("parts", parts)
        setupInner.put("systemInstruction", systemInstruction)

        val generationConfig = JSONObject()
        generationConfig.put("responseModalities", JSONArray(listOf("AUDIO")))
        val speechConfig = JSONObject()
        val voiceConfig = JSONObject()
        val prebuiltVoiceConfig = JSONObject()
        prebuiltVoiceConfig.put("voiceName", voiceName)
        voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig)
        speechConfig.put("voiceConfig", voiceConfig)
        generationConfig.put("speechConfig", speechConfig)
        setupInner.put("generationConfig", generationConfig)

        setupInner.put("tools", ToolDefinitions.getGeminiTools())
        setup.put("setup", setupInner)

        ws.send(setup.toString())
        Log.d(TAG, "Setup sent: $setupInner")
    }

    fun sendText(text: String) {
        val msg = webSocket ?: return

        val clientContent = JSONObject()
        val clientContentInner = JSONObject()
        val turns = JSONArray()
        val turn = JSONObject()
        turn.put("role", "user")
        val parts = JSONArray()
        parts.put(JSONObject().put("text", text))
        turn.put("parts", parts)
        turns.put(turn)
        clientContentInner.put("turns", turns)
        clientContentInner.put("turnComplete", true)
        clientContent.put("clientContent", clientContentInner)
        msg.send(clientContent.toString())

        sendSilence()
    }

    fun sendAudioChunk(pcm16: ShortArray) {
        val msg = webSocket ?: return
        val bytes = ByteBuffer.allocate(pcm16.size * 2)
        bytes.order(ByteOrder.LITTLE_ENDIAN)
        pcm16.forEach { bytes.putShort(it) }
        val base64 = Base64.encodeToString(bytes.array(), Base64.NO_WRAP)

        val realtimeInput = JSONObject()
        val realtimeInner = JSONObject()
        val mediaChunks = JSONArray()
        val chunk = JSONObject()
        chunk.put("data", base64)
        chunk.put("mimeType", "audio/pcm;rate=16000")
        mediaChunks.put(chunk)
        realtimeInner.put("mediaChunks", mediaChunks)
        realtimeInput.put("realtimeInput", realtimeInner)
        msg.send(realtimeInput.toString())
    }

    private fun sendSilence() {
        val silence = ShortArray(1600)
        val bytes = ByteBuffer.allocate(silence.size * 2)
        bytes.order(ByteOrder.LITTLE_ENDIAN)
        silence.forEach { bytes.putShort(it) }
        val base64 = Base64.encodeToString(bytes.array(), Base64.NO_WRAP)

        val realtimeInput = JSONObject()
        val realtimeInner = JSONObject()
        val mediaChunks = JSONArray()
        val chunk = JSONObject()
        chunk.put("data", base64)
        chunk.put("mimeType", "audio/pcm;rate=16000")
        mediaChunks.put(chunk)
        realtimeInner.put("mediaChunks", mediaChunks)
        realtimeInput.put("realtimeInput", realtimeInner)
        webSocket?.send(realtimeInput.toString())
    }

    fun sendToolResponse(functionResponses: List<ToolResponseItem>) {
        val msg = webSocket ?: return
        val toolResponse = JSONObject()
        val toolResponseInner = JSONObject()
        val responses = JSONArray()
        for (fr in functionResponses) {
            val resp = JSONObject()
            resp.put("id", fr.id)
            resp.put("name", fr.name)
            val responseObj = JSONObject()
            responseObj.put("response", fr.response)
            resp.put("response", responseObj)
            responses.put(resp)
        }
        toolResponseInner.put("functionResponses", responses)
        toolResponse.put("toolResponse", toolResponseInner)
        msg.send(toolResponse.toString())
        Log.d(TAG, "Tool responses sent: ${functionResponses.size}")
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)

            if (json.has("setupComplete")) {
                Log.d(TAG, "Setup complete!")
                return
            }

            if (json.has("serverContent")) {
                handleServerContent(json.getJSONObject("serverContent"))
            }

            if (json.has("toolCall")) {
                handleToolCall(json.getJSONObject("toolCall"))
            }

            if (json.has("usageMetadata")) {
                Log.d(TAG, "Usage: ${json.getJSONObject("usageMetadata")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}", e)
        }
    }

    private fun handleServerContent(sc: JSONObject) {
        if (sc.has("modelTurn")) {
            val modelTurn = sc.getJSONObject("modelTurn")
            if (modelTurn.has("parts")) {
                val parts = modelTurn.getJSONArray("parts")
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("inlineData")) {
                        val inlineData = part.getJSONObject("inlineData")
                        val data = inlineData.getString("data")
                        val mimeType = inlineData.optString("mimeType", "")
                        val sampleRate = if (mimeType.contains("24000")) 24000 else 24000
                        handleAudioData(data, sampleRate)
                    }
                    if (part.has("text")) {
                        val text = part.getString("text")
                        _incomingText.value = _incomingText.value + text
                    }
                }
            }
        }

        if (sc.has("outputTranscription")) {
            val ot = sc.getJSONObject("outputTranscription")
            if (ot.has("text")) {
                val text = ot.getString("text")
                _incomingText.value = _incomingText.value + text
            }
        }

        if (sc.has("inputTranscription")) {
            val it = sc.getJSONObject("inputTranscription")
            if (it.has("text")) {
                _inputTranscription.value = it.getString("text")
            }
        }

        if (sc.optBoolean("turnComplete")) {
            _turnComplete.value = true
        }
    }

    private fun handleAudioData(base64Data: String, sampleRate: Int) {
        try {
            val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val shorts = ShortArray(bytes.size / 2)
            for (i in shorts.indices) {
                shorts[i] = buffer.getShort()
            }
            accumulatedAudio.add(GeminiAudioChunk(shorts, sampleRate))
            _incomingAudio.value = GeminiAudioChunk(shorts, sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "Audio decode error: ${e.message}")
        }
    }

    private fun handleToolCall(tc: JSONObject) {
        if (tc.has("functionCalls")) {
            val calls = tc.getJSONArray("functionCalls")
            val toolCalls = mutableListOf<ToolCallInfo>()
            for (i in 0 until calls.length()) {
                val call = calls.getJSONObject(i)
                val id = call.getString("id")
                val name = call.getString("name")
                val args = call.optJSONObject("args") ?: JSONObject()
                toolCalls.add(ToolCallInfo(id, name, args))
            }
            _toolCalls.value = toolCalls
        }
    }
}

data class ToolResponseItem(
    val id: String,
    val name: String,
    val response: Any,
)
