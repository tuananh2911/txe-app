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
fun BubbleIcon() {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(48.dp) // Kích thước cố định 48dp
            .shadow(4.dp, CircleShape) // Bóng đổ nhẹ
            .background(Color.Gray.copy(alpha = 0.2f), CircleShape) // Nền xám mờ
            .alpha(if (isPressed) 1.0f else 0.5f) // Độ mờ thay đổi khi nhấn
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isPressed = true },
                    onDragEnd = { isPressed = false },
                    onDragCancel = { isPressed = false },
                    onDrag = { _, _ -> } // Kéo thả được xử lý ở setOnTouchListener
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Chat, // Icon chat mặc định
            contentDescription = "Chat Bubble",
            tint = Color.Gray, // Màu xám
            modifier = Modifier.size(24.dp) // Icon nhỏ hơn kích thước nền
        )
    }
}