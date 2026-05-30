package com.ufi.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufi.android.data.model.Settings
import com.ufi.android.data.model.VOICES
import com.ufi.android.ui.theme.Accent
import com.ufi.android.ui.theme.DarkBackground
import com.ufi.android.ui.theme.DarkBorder
import com.ufi.android.ui.theme.DarkSurface
import com.ufi.android.ui.theme.TextMuted
import com.ufi.android.ui.theme.TextPrimary
import com.ufi.android.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settings: Settings,
    onDismiss: () -> Unit,
    onSave: (Settings) -> Unit,
) {
    var apiKey by remember(settings) { mutableStateOf(settings.apiKey) }
    var serverUrl by remember(settings) { mutableStateOf(settings.serverUrl) }
    var voiceName by remember(settings) { mutableStateOf(settings.voiceName) }
    var systemPrompt by remember(settings) { mutableStateOf(settings.systemPrompt) }
    var voiceExpanded by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBackground,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Settings",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API Key", color = TextMuted) },
                placeholder = { Text("AIza...", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL (optional)", color = TextMuted) },
                placeholder = { Text("http://192.168.1.x:3000", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
            )

            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = voiceExpanded,
                onExpandedChange = { voiceExpanded = !voiceExpanded },
            ) {
                OutlinedTextField(
                    value = voiceName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Voice", color = TextMuted) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = fieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = voiceExpanded,
                    onDismissRequest = { voiceExpanded = false },
                ) {
                    VOICES.forEach { (name, desc) ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(name, color = TextPrimary, fontSize = 14.sp)
                                    Text(desc, color = TextMuted, fontSize = 11.sp)
                                }
                            },
                            onClick = {
                                voiceName = name
                                voiceExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System Prompt", color = TextMuted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                colors = fieldColors(),
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(settings.copy(
                            apiKey = apiKey.trim(),
                            serverUrl = serverUrl.trim(),
                            voiceName = voiceName,
                            systemPrompt = systemPrompt,
                        ))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = DarkBorder,
    cursorColor = Accent,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextMuted,
    focusedContainerColor = DarkSurface,
    unfocusedContainerColor = DarkSurface,
)
