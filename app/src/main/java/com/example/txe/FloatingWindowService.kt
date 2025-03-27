package com.example.txe

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FloatingWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleButton: TextView
    private var chatWindow: View? = null
    private var messageList: RecyclerView? = null
    private var inputField: EditText? = null
    private var messageAdapter: MessageAdapter? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var chatWindowParams: WindowManager.LayoutParams? = null
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging = false
    private var lastClickTime: Long = 0
    private val CLICK_DELAY = 300L // milliseconds

    companion object {
        private const val TAG = "FloatingWindowService"
    }

    // BroadcastReceiver để nhận kết quả từ CommandService
    private val commandResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val result = intent?.getStringExtra("command_result") ?: "Không có kết quả"
            serviceScope.launch {
                messageAdapter?.updateLastMessage(result)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Service created")
        try {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Cần quyền hiển thị trên màn hình để hoạt động", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            setupChatWindow()
            setupBubbleButton()

            // Đăng ký BroadcastReceiver với cờ RECEIVER_NOT_EXPORTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    commandResultReceiver,
                    IntentFilter("COMMAND_RESULT"),
                    RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(commandResultReceiver, IntentFilter("COMMAND_RESULT"))
            }

            val commandIntent = Intent(this, CommandService::class.java)
            startService(commandIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Lỗi khởi tạo service: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupBubbleButton() {
        try {
            bubbleButton = LayoutInflater.from(this).inflate(R.layout.float_bubble, null) as TextView
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 100
            }

            bubbleButton.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        lastClickTime = System.currentTimeMillis()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        if (!isDragging && (kotlin.math.abs(deltaX) > 5 || kotlin.math.abs(deltaY) > 5)) {
                            isDragging = true
                            Log.d(TAG, "Dragging started")
                        }
                        if (isDragging) {
                            params.x = (initialX + deltaX).toInt()
                            params.y = (initialY + deltaY).toInt()
                            windowManager.updateViewLayout(view, params)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging && System.currentTimeMillis() - lastClickTime < CLICK_DELAY) {
                            toggleChatWindow()
                        }
                        isDragging = false
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(bubbleButton, params)
            Log.d(TAG, "Bubble button added to window")
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupBubbleButton", e)
            Toast.makeText(this, "Lỗi khởi tạo nút nổi: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupChatWindow() {
        try {
            chatWindow = LayoutInflater.from(this).inflate(R.layout.chat_window, null)
            messageList = chatWindow?.findViewById(R.id.messageList)
            inputField = chatWindow?.findViewById(R.id.inputField)
            val sendButton = chatWindow?.findViewById<View>(R.id.sendButton)
            val closeButton = chatWindow?.findViewById<ImageButton>(R.id.closeButton) // Thay Button bằng ImageButton

            messageAdapter = MessageAdapter()
            messageList?.apply {
                layoutManager = LinearLayoutManager(this@FloatingWindowService)
                adapter = messageAdapter
            }

            inputField?.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    handleCommand(inputField?.text.toString())
                    inputField?.text?.clear()
                    true
                } else false
            }

            sendButton?.setOnClickListener {
                handleCommand(inputField?.text.toString())
                inputField?.text?.clear()
            }

            closeButton?.setOnClickListener {
                chatWindow?.visibility = View.GONE
            }

            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            chatWindowParams = WindowManager.LayoutParams(
                screenWidth,
                screenHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                x = 0
                y = 0
            }

            chatWindow?.viewTreeObserver?.addOnGlobalLayoutListener {
                val rect = Rect()
                chatWindow?.getWindowVisibleDisplayFrame(rect)
                val keyboardHeight = screenHeight - rect.bottom

                chatWindowParams?.let { params ->
                    if (keyboardHeight > screenHeight * 0.15) {
                        // Adjust layout for keyboard
                        params.height = screenHeight - keyboardHeight
                        params.y = keyboardHeight
                        messageList?.setPadding(0, 0, 0, keyboardHeight)
                    } else {
                        // Reset layout when keyboard is hidden
                        params.height = screenHeight
                        params.y = 0
                        messageList?.setPadding(0, 0, 0, 0)
                    }
                    chatWindow?.let { windowManager.updateViewLayout(it, params) }
                }
            }

            chatWindow?.let {
                windowManager.addView(it, chatWindowParams)
                it.visibility = View.GONE
                Log.d(TAG, "Chat window added to window")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupChatWindow", e)
            Toast.makeText(this, "Lỗi khởi tạo cửa sổ chat: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleChatWindow() {
        chatWindow?.let { window ->
            window.visibility = if (window.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            if (window.visibility == View.VISIBLE) {
                inputField?.postDelayed({
                    inputField?.requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)
                }, 100)
            } else {
                // Hide keyboard when closing chat window
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(inputField?.windowToken, 0)
            }
        } ?: Log.w(TAG, "Chat window is null in toggleChatWindow")
    }

    private fun handleCommand(command: String) {
        if (command.isBlank()) return

        serviceScope.launch {
            try {
                messageAdapter?.addMessage(Message(command, "Đang xử lý..."))
                val commandIntent = Intent(this@FloatingWindowService, CommandService::class.java).apply {
                    action = "HANDLE_COMMAND"
                    putExtra("command", command)
                }
                startService(commandIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling command", e)
                messageAdapter?.updateLastMessage("Lỗi xử lý lệnh: ${e.message}")
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val accessibilityEnabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
            if (accessibilityEnabled == 1) {
                val serviceString = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                serviceString?.contains("${packageName}/${MyAccessibilityService::class.java.name}") ?: false
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service", e)
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            windowManager.removeView(bubbleButton)
            chatWindow?.let { windowManager.removeView(it) }
            unregisterReceiver(commandResultReceiver)
            val commandIntent = Intent(this, CommandService::class.java)
            stopService(commandIntent)
            Log.d(TAG, "Service destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
}