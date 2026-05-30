package com.ufi.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufi.android.data.model.ChatMessage
import com.ufi.android.data.websocket.ConnectionState
import com.ufi.android.ui.components.MessageBubble
import com.ufi.android.ui.components.SettingsDialog
import com.ufi.android.ui.components.ThinkingIndicator
import com.ufi.android.ui.theme.Accent
import com.ufi.android.ui.theme.DarkBackground
import com.ufi.android.ui.theme.DarkBorder
import com.ufi.android.ui.theme.DarkSurface
import com.ufi.android.ui.theme.DarkSurfaceVariant
import com.ufi.android.ui.theme.ErrorRed
import com.ufi.android.ui.theme.Success
import com.ufi.android.ui.theme.TextMuted
import com.ufi.android.ui.theme.TextPrimary
import com.ufi.android.ui.theme.TextSecondary
import com.ufi.android.viewmodel.ChatViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.messages.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkBackground)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("U", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text("Ufi", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

            Spacer(Modifier.weight(1f))

            StatusDot(connectionState)

            Spacer(Modifier.width(4.dp))
            Text(
                text = when (connectionState) {
                    ConnectionState.CONNECTED -> "ready"
                    ConnectionState.CONNECTING -> "connect"
                    ConnectionState.ERROR -> "error"
                    ConnectionState.DISCONNECTED -> "offline"
                },
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )

            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showSettings = true }) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (messages.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearChat() }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Clear chat",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Service bar
        if (connectionState == ConnectionState.CONNECTED) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .padding(horizontal = 16.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AI", color = Success, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(4.dp).clip(CircleShape).background(Success))
                Spacer(Modifier.weight(1f))
                Text("VOICE", color = TextMuted, fontSize = 9.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, letterSpacing = 0.5.sp)
            }
        }

        // Error banner
        AnimatedVisibility(
            visible = connectionState == ConnectionState.ERROR,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                text = "Connection error. Check your API key.",
                color = ErrorRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x0DFF6B6B))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // Chat area
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                WelcomeView(
                    connectionState = connectionState,
                    isListening = isListening,
                    onMicClick = { viewModel.toggleMic() },
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = messages,
                        key = { it.id },
                    ) { message ->
                        MessageBubble(message = message)
                    }

                    if (isThinking) {
                        item {
                            ThinkingIndicator(
                                modifier = Modifier.animateItemPlacement(),
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Mic button
            val micBg = if (isListening) Accent.copy(alpha = 0.15f) else Color.Transparent
            IconButton(
                onClick = { viewModel.toggleMic() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(micBg),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (isListening) Accent else TextMuted,
                ),
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Voice input", modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(6.dp))

            // Text input
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Type a message...", color = TextMuted, fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = DarkBorder,
                    cursorColor = Accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendText(textInput)
                            textInput = ""
                        }
                    },
                ),
            )

            Spacer(Modifier.width(6.dp))

            // Send button
            val canSend = textInput.isNotBlank() && settings.hasApiKey
            IconButton(
                onClick = {
                    viewModel.sendText(textInput)
                    textInput = ""
                },
                enabled = canSend,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (canSend) Accent else DarkSurface),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (canSend) Color.White else TextMuted.copy(alpha = 0.3f),
                ),
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onDismiss = { showSettings = false },
            onSave = { newSettings ->
                viewModel.updateSettings(newSettings)
                showSettings = false
            },
        )
    }
}

@Composable
private fun WelcomeView(
    connectionState: ConnectionState,
    isListening: Boolean,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Mic button
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    if (isListening) Accent.copy(alpha = 0.2f)
                    else DarkSurface
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "Start listening",
                tint = if (isListening) Accent else TextMuted,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (isListening) "Listening..." else "Voice Assistant",
            color = TextSecondary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (!isListening && connectionState == ConnectionState.DISCONNECTED) {
                if (false) "Enter API key in settings" else "Tap the microphone or type to start."
            } else {
                "Tap the microphone or type to start."
            },
            color = TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )

        if (connectionState != ConnectionState.CONNECTED) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Settings → add Gemini API key",
                color = Accent.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun StatusDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.CONNECTED -> Success
        ConnectionState.CONNECTING -> Accent
        ConnectionState.ERROR -> ErrorRed
        ConnectionState.DISCONNECTED -> TextMuted
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}
