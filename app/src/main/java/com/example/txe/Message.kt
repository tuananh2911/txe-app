package com.example.txe

data class Message(
    val text: String,
    val userMessage: String = "", // Tin nhắn người dùng (nếu có)
    val isUserMessage: Boolean = true,
    val speakerInfo: Pair<String, String>? = null // Pair<word, language> cho phát âm
)