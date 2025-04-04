package com.example.txe

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ShortcutAdapter(
    private var shortcuts: List<Pair<String, String>>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<ShortcutAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val shortcutText: TextView = view.findViewById(R.id.shortcutText)
        val valueText: TextView = view.findViewById(R.id.valueText)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shortcut, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (shortcut, value) = shortcuts[position]
        holder.shortcutText.text = shortcut
        holder.valueText.text = value
        holder.deleteButton.setOnClickListener {
            onDeleteClick(shortcut)
        }
    }

    override fun getItemCount() = shortcuts.size

    fun updateShortcuts(newShortcuts: List<Pair<String, String>>) {
        shortcuts = newShortcuts
        notifyDataSetChanged()
    }
} 