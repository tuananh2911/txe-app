package com.example.txe

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    private val messages = mutableListOf<Message>()

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userMessageText: TextView = view.findViewById(R.id.userMessageText)
        val systemMessageText: TextView = view.findViewById(R.id.systemMessageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.message_item, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        
        // Reset visibility
        holder.userMessageText.visibility = View.GONE
        holder.systemMessageText.visibility = View.GONE

        if (message.isUserMessage) {
            holder.userMessageText.apply {
                visibility = View.VISIBLE
                text = message.text
            }
        } else {
            holder.systemMessageText.apply {
                visibility = View.VISIBLE
                text = message.text
            }
        }
    }

    override fun getItemCount() = messages.size

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(response: String) {
        if (messages.isNotEmpty()) {
            val lastMessage = messages.last()
            if (lastMessage.isUserMessage) {
                // Add system response as a new message
                addMessage(Message(response, "", false))
            }
        }
    }
} 