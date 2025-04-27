package com.example.txe

data class Message(
    val text: String,
    val imagePath: String = "", // Đường dẫn tới ảnh (nếu có)
    val userMessage: String = "", // Tin nhắn người dùng (nếu có)
    val isUserMessage: Boolean = true,
    val speakerInfo: Pair<String, String>? = null // Pair<word, language> cho phát âm
)