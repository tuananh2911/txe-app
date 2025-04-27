package com.example.txe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun BubbleIcon(isChatOpen: Boolean, isLongPress: Boolean) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .shadow(4.dp, CircleShape)
            .background(
                if (isChatOpen) Color.White else Color.Gray.copy(alpha = 0.2f),
                CircleShape
            )
            .alpha(if (!isChatOpen && !isLongPress) 0.5f else 1.0f),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Chat,
            contentDescription = "Chat Bubble",
            tint = if (isLongPress) Color.Red else if (isChatOpen) Color(0xFF00FFFF) else Color.Gray,
            modifier = Modifier.size(32.dp)
        )
    }
}