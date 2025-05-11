package com.example.txe

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TextExpanderAccessibilityService : AccessibilityService() {
    private val TAG = "TextExpanderService"
    private val shortcuts = mutableMapOf<String, String>()
    private val commandPrefix = "jj"
    private val commandSuffix = "jj"
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private lateinit var dictionaryManager: DictionaryManager
    private var lastProcessedText = ""
    private var currentNodeInfo: AccessibilityNodeInfo? = null
    private lateinit var resultReceiver: BroadcastReceiver

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service connected")
        dictionaryManager = DictionaryManager(this)
        // Tải phím tắt từ UserDictionary
        serviceScope.launch {
            val expanders = dictionaryManager.getAllWords()
            shortcuts.clear()
            expanders.forEach { expander ->
                shortcuts[expander.shortcut] = expander.value
            }
            Log.d(TAG, "Loaded shortcuts: $shortcuts")
        }
        // Đăng ký BroadcastReceiver để nhận kết quả từ CommandService
        resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "COMMAND_RESULT") {
                    val result = intent.getStringExtra("command_result") ?: return
                    Log.d(TAG, "Received command result: $result")
                    currentNodeInfo?.let { nodeInfo ->
                        val bundle = Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                result
                            )
                        }
                        nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                        Log.d(TAG, "Inserted command result: $result")
                    }
                }
            }
        }
        val filter = IntentFilter("COMMAND_RESULT")
        registerReceiver(resultReceiver, filter, RECEIVER_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        val nodeInfo = event.source ?: return
        val text = event.text?.joinToString() ?: return

        // Xử lý xóa văn bản
        if (text.length < lastProcessedText.length) {
            lastProcessedText = text
            return
        }
        // Tránh xử lý lặp lại
        if (text == lastProcessedText) return
        lastProcessedText = text

        Log.d(TAG, "Text changed: $text")

        // Kiểm tra phím tắt
        shortcuts.forEach { (shortcut, expanded) ->
            if (text.endsWith(shortcut)) {
                replaceText(nodeInfo, text, shortcut, expanded)
                return
            }
        }

        // Kiểm tra lệnh với các biến thể jj, Jj, JJ, jJ
        val commandRegex = Regex("${Regex.escape(commandPrefix)}(.+?)${Regex.escape(commandSuffix)}", RegexOption.IGNORE_CASE)
        commandRegex.find(text)?.let { match ->
            val command = match.groupValues[1]
            handleCommand(nodeInfo, text, command)
        }
    }

    private fun replaceText(nodeInfo: AccessibilityNodeInfo, original: String, shortcut: String, expanded: String) {
        try {
            val newText = original.removeSuffix(shortcut) + expanded
            val bundle = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            if (!nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)) {
                Log.w(TAG, "ACTION_SET_TEXT failed, trying clipboard fallback")
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Expanded Text", newText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Đã sao chép: $newText", Toast.LENGTH_SHORT).show()
            }
            Log.d(TAG, "Replaced '$shortcut' with '$expanded'")
            lastProcessedText = newText
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing text", e)
            Toast.makeText(this, "Lỗi mở rộng văn bản: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCommand(nodeInfo: AccessibilityNodeInfo, original: String, command: String) {
        try {
            val newText = original.replace(Regex("${Regex.escape(commandPrefix)}$command${Regex.escape(commandSuffix)}", RegexOption.IGNORE_CASE), "")

            val formatCommand = "jj$command"
            Log.d(TAG, "Sent command '$formatCommand' to CommandService")

            currentNodeInfo = nodeInfo
            val intent = Intent(this, CommandService::class.java).apply {
                action = "COMMAND_DIRECT"
                putExtra("command", formatCommand)
            }
            startService(intent) ?: run {
                Log.e(TAG, "Failed to start CommandService")
                Toast.makeText(this, "Lỗi: Không thể gửi lệnh", Toast.LENGTH_SHORT).show()
            }

            val bundle = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            Log.d(TAG, "Sent command '$command' to CommandService")
            lastProcessedText = newText
        } catch (e: Exception) {
            Log.e(TAG, "Error handling command", e)
            Toast.makeText(this, "Lỗi xử lý lệnh: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(resultReceiver)
        Log.d(TAG, "Accessibility Service destroyed")
    }
}