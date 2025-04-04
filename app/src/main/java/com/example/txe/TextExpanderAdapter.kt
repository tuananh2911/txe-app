package com.example.txe

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class TextExpanderAdapter(
    private val onDeleteClick: (String) -> Unit
) : ListAdapter<Expander, TextExpanderAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expander, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val keyTextView: TextView = itemView.findViewById(R.id.keyTextView)
        private val valueTextView: TextView = itemView.findViewById(R.id.valueTextView)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(expander: Expander) {
            keyTextView.text = expander.shortcut
            valueTextView.text = expander.value
            deleteButton.setOnClickListener {
                onDeleteClick(expander.shortcut)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Expander>() {
        override fun areItemsTheSame(oldItem: Expander, newItem: Expander): Boolean {
            return oldItem.shortcut == newItem.shortcut
        }

        override fun areContentsTheSame(oldItem: Expander, newItem: Expander): Boolean {
            return oldItem == newItem
        }
    }
} 