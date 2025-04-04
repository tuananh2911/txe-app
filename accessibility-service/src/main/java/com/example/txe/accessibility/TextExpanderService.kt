package com.example.txe.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle
import android.util.Log
import android.content.Intent
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TextExpanderService : AccessibilityService() {
    companion object {
        private const val TAG = "TextExpanderService"
        private const val EVENT_TYPE_TEXT_CHANGED = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        private const val PREFS_NAME = "text_expander_prefs"
        private const val KEY_EXPANDERS = "expanders"
    }

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

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

                // Check for text expansions
                val expansions = getExpanderMap()
                expansions.forEach { (shortcut, expansion) ->
                    val trigger = "$shortcut " // Chuỗi cần kiểm tra (shortcut + dấu cách)
                    if (text.endsWith(trigger)) {
                        Log.d(TAG, "Found matching shortcut: $shortcut -> $expansion")
                        // Replace the shortcut with the expansion
                        val newText = text.dropLast(trigger.length) + expansion
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

    private fun getExpanderMap(): Map<String, String> {
        val json = prefs?.getString(KEY_EXPANDERS, "[]")
        val type = object : TypeToken<List<TextExpander>>() {}.type
        val expanders = gson.fromJson<List<TextExpander>>(json, type) ?: emptyList()
        return expanders.associate { it.key to it.value }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility Service destroyed")
    }
} 