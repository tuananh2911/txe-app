package com.example.txe

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class ExpanderAdapter(
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<ExpanderAdapter.ExpanderViewHolder>() {

    private val expanders = mutableListOf<Expander>()

    fun submitList(newExpanders: List<Expander>) {
        expanders.clear()
        expanders.addAll(newExpanders)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpanderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expander, parent, false)
        return ExpanderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpanderViewHolder, position: Int) {
        holder.bind(expanders[position])
    }

    override fun getItemCount() = expanders.size

    inner class ExpanderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val keyTextView: TextView = itemView.findViewById(R.id.keyTextView)
        private val valueTextView: TextView = itemView.findViewById(R.id.valueTextView)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)

        fun bind(expander: Expander) {
            keyTextView.text = expander.shortcut
            valueTextView.text = expander.value
            deleteButton.setOnClickListener {
                onDeleteClick(expander.shortcut)
            }
        }
    }
} 