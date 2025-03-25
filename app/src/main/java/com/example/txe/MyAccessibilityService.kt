package com.example.txe

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent

class MyAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "MyAccessibilityService"
        private const val EVENT_TYPE_TEXT_CHANGED = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
    }

    private var apiService: ApiService? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private val textExpansions = mapOf(
        "brb" to "be right back",
        "omw" to "on my way",
        "ttyl" to "talk to you later",
        "np" to "no problem",
        "ty" to "thank you",
        "yw" to "you're welcome",
        "idk" to "I don't know",
        "imo" to "in my opinion",
        "fyi" to "for your information",
        "asap" to "as soon as possible"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            Log.d(TAG, "Received accessibility event: ${event.eventType}")
            if (event.eventType == EVENT_TYPE_TEXT_CHANGED) {
                val node = event.source ?: run {
                    Log.w(TAG, "Event source is null")
                    return
                }
                val text = event.text?.firstOrNull()?.toString() ?: run {
                    Log.w(TAG, "Event text is null or empty")
                    return
                }
                
                Log.d(TAG, "Processing text: $text")
                // Check if the text matches any of our expansions
                textExpansions.forEach { (shortcut, expansion) ->
                    if (text.endsWith(shortcut)) {
                        Log.d(TAG, "Found matching shortcut: $shortcut -> $expansion")
                        // Replace the shortcut with the expansion
                        val newText = text.substring(0, text.length - shortcut.length) + expansion
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                        })
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onAccessibilityEvent", e)
        }
    }

    private fun handleSpecialCommand(command: String) {
        Log.d(TAG, "Handling special command: $command")
        serviceScope.launch {
            try {
                when {
                    command == "/thoitiet" -> {
                        Log.d(TAG, "Processing weather command")
                        val weatherInfo = apiService?.getWeatherInfo() ?: "Lỗi khởi tạo service"
                        showToast(weatherInfo)
                    }
                    command.startsWith("/tygia") -> {
                        Log.d(TAG, "Processing exchange rate command")
                        val parts = command.split(" ")
                        val currencyCode = if (parts.size > 1) parts[1] else ""
                        val exchangeRateInfo = apiService?.getExchangeRate(currencyCode) ?: "Lỗi khởi tạo service"
                        showToast(exchangeRateInfo)
                    }
                    command == "/amlich" -> {
                        Log.d(TAG, "Processing lunar date command")
                        val lunarInfo = apiService?.getLunarDate() ?: "Lỗi khởi tạo service"
                        showToast(lunarInfo)
                    }
                    command.startsWith("/phatnguoi") -> {
                        Log.d(TAG, "Processing traffic violations command")
                        val parts = command.split(" ")
                        if (parts.size > 1) {
                            val plateNumber = parts[1]
                            val violationsInfo = apiService?.getTrafficViolations(plateNumber) ?: "Lỗi khởi tạo service"
                            showToast(violationsInfo)
                        } else {
                            showToast("Vui lòng nhập biển số xe. Ví dụ: /phatnguoi 30A-12345")
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unsupported command: $command")
                        showToast("Lệnh không được hỗ trợ: $command")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling command: $command", e)
                showToast("Lỗi xử lý lệnh: ${e.message}")
            }
        }
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "Showing toast: $message")
                Toast.makeText(this@MyAccessibilityService, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error showing toast", e)
            }
        }
    }

    override fun onInterrupt() {
        try {
            Log.w(TAG, "Service interrupted")
            Toast.makeText(this, "Text Expander service interrupted", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onInterrupt", e)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        try {
            // Initialize ApiService with context
            apiService = ApiService(applicationContext)
            Log.d(TAG, "ApiService initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ApiService", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility Service destroyed")
    }
}