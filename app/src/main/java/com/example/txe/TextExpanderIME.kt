package com.example.txe

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.view.WindowManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ArrayAdapter
import android.view.ViewGroup.LayoutParams
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.WindowManager.LayoutParams as WindowLayoutParams
import android.view.KeyEvent
import android.view.View.OnClickListener
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.Space

class TextExpanderIME : InputMethodService() {
    private var expanderManager: TextExpanderManager? = null
    private var currentInputConnection: InputConnection? = null
    private var currentText = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        expanderManager = TextExpanderManager(this)
    }

    override fun onCreateInputView(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.LTGRAY)
            setPadding(4, 4, 4, 4)
        }

        // Text display area
        val textDisplay = TextView(this).apply {
            background = android.graphics.drawable.ColorDrawable(Color.WHITE)
            setPadding(8, 8, 8, 8)
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 4
            }
        }

        // Keyboard layout
        val keyboardLayout = GridLayout(this).apply {
            columnCount = 10
            rowCount = 4
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Add keys
        val keys = listOf(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
            "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
            "a", "s", "d", "f", "g", "h", "j", "k", "l", "⌫",
            "⇧", "z", "x", "c", "v", "b", "n", "m", " ", "↵"
        )

        keys.forEach { key ->
            val button = Button(this).apply {
                text = key
                setOnClickListener {
                    when (key) {
                        "⌫" -> {
                            if (currentText.isNotEmpty()) {
                                currentText.deleteCharAt(currentText.length - 1)
                                textDisplay.text = currentText.toString()
                            }
                        }
                        "↵" -> {
                            val text = currentText.toString()
                            currentInputConnection?.commitText(text, 1)
                            currentText.clear()
                            textDisplay.text = ""
                        }
                        "⇧" -> {
                            // Handle shift key
                        }
                        else -> {
                            currentText.append(key)
                            textDisplay.text = currentText.toString()
                        }
                    }
                }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(2, 2, 2, 2)
                }
            }
            keyboardLayout.addView(button)
        }

        // Add expander button
        val expanderButton = Button(this).apply {
            text = "📝"
            setOnClickListener {
                val text = currentText.toString()
                val expander = expanderManager?.getExpanders()?.find { it.key == text }
                if (expander != null) {
                    currentText.clear()
                    currentText.append(expander.value)
                    textDisplay.text = currentText.toString()
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
                topMargin = 4
            }
        }

        layout.addView(textDisplay)
        layout.addView(keyboardLayout)
        layout.addView(expanderButton)

        return layout
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentInputConnection = currentInputConnection
    }

    override fun onFinishInput() {
        super.onFinishInput()
        currentInputConnection = null
    }

    override fun onDestroy() {
        super.onDestroy()
    }
} 