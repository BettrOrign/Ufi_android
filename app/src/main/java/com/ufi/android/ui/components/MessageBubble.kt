package com.ufi.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ufi.android.data.model.ChatMessage
import com.ufi.android.data.model.MessageRole
import com.ufi.android.ui.theme.Accent
import com.ufi.android.ui.theme.DarkSurface
import com.ufi.android.ui.theme.DarkSurfaceVariant
import com.ufi.android.ui.theme.ErrorRed
import com.ufi.android.ui.theme.Success
import com.ufi.android.ui.theme.TextMuted
import com.ufi.android.ui.theme.TextPrimary
import com.ufi.android.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == MessageRole.USER
    val shape = if (isUser) {
        RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp)
    } else {
        RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
    }

    val bgColor = when {
        isUser -> Accent
        message.role == MessageRole.SYSTEM -> DarkSurfaceVariant
        else -> DarkSurface
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = timeFormat.format(Date(message.timestamp))

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically { it / 2 },
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            Row(
                modifier = Modifier.widthIn(max = 320.dp),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            ) {
                if (message.role == MessageRole.SYSTEM) {
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(bgColor)
                            .padding(10.dp),
                    ) {
                        Text(
                            text = message.text,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .clip(shape)
                            .background(bgColor)
                            .padding(12.dp),
                    ) {
                        if (!isUser) {
                            Text(
                                text = "Ufi",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.4.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            text = message.text,
                            color = if (isUser) TextPrimary else TextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = timeStr,
                            color = if (isUser) TextPrimary.copy(alpha = 0.5f) else TextMuted,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
            .background(DarkSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Accent),
            )
        }
    }
}
