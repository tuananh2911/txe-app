package com.example.txe

data class Message(
    val text: String,
    var response: String = "",
    val isUserMessage: Boolean = true
) {
    fun createSystemResponse(): Message {
        return Message(response, "", false)
    }
} 