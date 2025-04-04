package com.example.txe

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.view.Gravity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.widget.PopupWindow
import android.provider.UserDictionary
import android.database.Cursor

class TxEInputMethodService : InputMethodService() {
    private lateinit var dictionaryManager: DictionaryManager
    private var currentText = StringBuilder()
    private var keyboardView: View? = null
    private var popupWindow: PopupWindow? = null
    private var suggestionAdapter: SuggestionAdapter? = null
    private var suggestionsListView: ListView? = null

    companion object {
        private const val TAG = "TxEInputMethodService"
    }

    override fun onCreate() {
        super.onCreate()
        dictionaryManager = DictionaryManager(this)
        Log.d(TAG, "Service created")
    }

    override fun onCreateInputView(): View {
        keyboardView = LayoutInflater.from(this).inflate(R.layout.keyboard_layout, null)
        setupKeyboardButtons()
        return keyboardView!!
    }

    private fun setupKeyboardButtons() {
        // Setup letter buttons
        for (char in 'A'..'Z') {
            val buttonId = resources.getIdentifier("button_${char.toLowerCase()}", "id", packageName)
            keyboardView?.findViewById<Button>(buttonId)?.setOnClickListener {
                currentText.append(char)
                updateSuggestions()
            }
        }

        // Setup space button
        keyboardView?.findViewById<Button>(R.id.button_space)?.setOnClickListener {
            currentText.append(" ")
            updateSuggestions()
        }

        // Setup delete button
        keyboardView?.findViewById<Button>(R.id.button_delete)?.setOnClickListener {
            if (currentText.isNotEmpty()) {
                currentText.deleteCharAt(currentText.length - 1)
                updateSuggestions()
            } else {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
        }

        // Setup enter button
        keyboardView?.findViewById<Button>(R.id.button_enter)?.setOnClickListener {
            currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }
    }

    private fun updateSuggestions() {
        val text = currentText.toString()
        Log.d(TAG, "Updating suggestions for text: $text")
        
        if (text.isEmpty()) {
            hideSuggestions()
            return
        }

        val suggestions = getSuggestionsFromUserDictionary(text)
        Log.d(TAG, "Found ${suggestions.size} suggestions: $suggestions")
        
        if (suggestions.isNotEmpty()) {
            showSuggestions(suggestions)
        } else {
            hideSuggestions()
        }
    }

    private fun getSuggestionsFromUserDictionary(prefix: String): List<String> {
        val suggestions = mutableListOf<String>()
        try {
            val cursor: Cursor? = contentResolver.query(
                UserDictionary.Words.CONTENT_URI,
                arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.SHORTCUT),
                "${UserDictionary.Words.SHORTCUT} LIKE ?",
                arrayOf("$prefix%"),
                "${UserDictionary.Words.FREQUENCY} DESC"
            )

            cursor?.use {
                val wordIndex = it.getColumnIndex(UserDictionary.Words.WORD)
                val shortcutIndex = it.getColumnIndex(UserDictionary.Words.SHORTCUT)
                
                Log.d(TAG, "Cursor count: ${it.count}")
                
                while (it.moveToNext()) {
                    val word = it.getString(wordIndex)
                    val shortcut = it.getString(shortcutIndex)
                    Log.d(TAG, "Found word: $word with shortcut: $shortcut")
                    suggestions.add(word)
                }
            } ?: Log.e(TAG, "Cursor is null")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying User Dictionary", e)
        }
        return suggestions.take(5)
    }

    private fun showSuggestions(suggestions: List<String>) {
        Log.d(TAG, "Showing suggestions: $suggestions")
        if (popupWindow == null) {
            val popupView = LayoutInflater.from(this).inflate(R.layout.suggestions_popup, null)
            suggestionsListView = popupView.findViewById(R.id.suggestions_list)
            popupWindow = PopupWindow(
                popupView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                setBackgroundDrawable(ColorDrawable(Color.WHITE))
                elevation = 10f
            }
        }

        suggestionAdapter = SuggestionAdapter(suggestions) { suggestion ->
            Log.d(TAG, "Selected suggestion: $suggestion")
            // Insert the suggestion
            currentInputConnection?.commitText(suggestion, 1)
            currentText.clear()
            hideSuggestions()
        }
        suggestionsListView?.adapter = suggestionAdapter

        // Show popup above the keyboard
        keyboardView?.let { view ->
            popupWindow?.showAsDropDown(view, 0, -view.height, Gravity.TOP)
        }
    }

    private fun hideSuggestions() {
        popupWindow?.dismiss()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentText.clear()
        Log.d(TAG, "Input view started")
    }
}

private class SuggestionAdapter(
    private var items: List<String>
) : BaseAdapter() {
    private var onItemClick: ((String) -> Unit)? = null

    constructor(items: List<String>, onItemClick: (String) -> Unit) : this(items) {
        this.onItemClick = onItemClick
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(parent?.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = items[position]
        
        view.setOnClickListener {
            onItemClick?.invoke(items[position])
        }
        
        return view
    }
} 