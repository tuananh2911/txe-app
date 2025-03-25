package com.example.txe

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingWindowService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleButton: TextView
    private var chatWindow: View? = null
    private var messageList: RecyclerView? = null
    private var inputField: EditText? = null
    private var messageAdapter: MessageAdapter? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var apiService: ApiService? = null
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging = false
    private var lastClickTime: Long = 0
    private val CLICK_DELAY = 300L // milliseconds
    private var chatWindowParams: WindowManager.LayoutParams? = null
    private var commandService: CommandService? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d("FloatingWindowService", "Service created")
        try {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Cần quyền hiển thị trên màn hình để hoạt động", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            setupChatWindow()
            setupBubbleButton()

            // Start CommandService
            val commandIntent = Intent(this, CommandService::class.java)
            startService(commandIntent)
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "Error in onCreate", e)
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = 100

            bubbleButton.setOnTouchListener { view, event ->
                Log.d("FloatingWindowService", "Touch event: ${event.action}")
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d("FloatingWindowService", "Touch down")
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
                        
                        if (!isDragging && (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5)) {
                            isDragging = true
                            Log.d("FloatingWindowService", "Dragging started")
                        }
                        
                        if (isDragging) {
                            params.x = initialX + deltaX.toInt()
                            params.y = initialY + deltaY.toInt()
                            try {
                                windowManager.updateViewLayout(view, params)
                            } catch (e: Exception) {
                                Log.e("FloatingWindowService", "Error updating layout", e)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        Log.d("FloatingWindowService", "Touch up, isDragging: $isDragging")
                        if (!isDragging) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastClickTime < CLICK_DELAY) {
                                toggleChatWindow()
                            }
                        }
                        isDragging = false
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(bubbleButton, params)
            Log.d("FloatingWindowService", "Bubble button added to window")
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "Error in setupBubbleButton", e)
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
            val closeButton = chatWindow?.findViewById<Button>(R.id.closeButton)

            messageAdapter = MessageAdapter()
            messageList?.layoutManager = LinearLayoutManager(this)
            messageList?.adapter = messageAdapter

            inputField?.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    handleCommand(inputField?.text.toString())
                    inputField?.text?.clear()
                }
                true
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
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
            chatWindowParams?.gravity = Gravity.BOTTOM
            chatWindowParams?.x = 0
            chatWindowParams?.y = 0

            // Add global layout listener to detect keyboard
            chatWindow?.viewTreeObserver?.addOnGlobalLayoutListener {
                val rect = Rect()
                chatWindow?.getWindowVisibleDisplayFrame(rect)
                val keyboardHeight = screenHeight - rect.bottom

                if (keyboardHeight > screenHeight * 0.15) {
                    // Keyboard is visible
                    chatWindowParams?.height = screenHeight - keyboardHeight
                    chatWindowParams?.y = keyboardHeight
                } else {
                    // Keyboard is hidden
                    chatWindowParams?.height = screenHeight
                    chatWindowParams?.y = 0
                }

                try {
                    chatWindow?.let { windowManager.updateViewLayout(it, chatWindowParams) }
                } catch (e: Exception) {
                    Log.e("FloatingWindowService", "Error updating chat window layout", e)
                }
            }

            windowManager.addView(chatWindow, chatWindowParams)
            chatWindow?.visibility = View.GONE
            Log.d("FloatingWindowService", "Chat window added to window")
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "Error in setupChatWindow", e)
        }
    }

    private fun toggleChatWindow() {
        try {
            chatWindow?.let { window ->
                window.visibility = if (window.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                if (window.visibility == View.VISIBLE) {
                    inputField?.requestFocus()
                }
            }
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "Error in toggleChatWindow", e)
        }
    }

    private fun handleCommand(command: String) {
        if (command.isBlank()) return

        serviceScope.launch {
            try {
                messageAdapter?.addMessage(Message(command, "Đang xử lý..."))
                // Send command to CommandService through Intent
                val commandIntent = Intent(this@FloatingWindowService, CommandService::class.java).apply {
                    action = "HANDLE_COMMAND"
                    putExtra("command", command)
                }
                startService(commandIntent)
            } catch (e: Exception) {
                Log.e("FloatingWindowService", "Error handling command", e)
                messageAdapter?.updateLastMessage("Lỗi xử lý lệnh: ${e.message}")
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED
        )
        if (accessibilityEnabled == 1) {
            val serviceString = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            serviceString?.let {
                return it.contains("${packageName}/${MyAccessibilityService::class.java.name}")
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            windowManager.removeView(bubbleButton)
            chatWindow?.let { windowManager.removeView(it) }
            // Stop CommandService
            val commandIntent = Intent(this, CommandService::class.java)
            stopService(commandIntent)
        } catch (e: Exception) {
            Log.e("FloatingWindowService", "Error in onDestroy", e)
        }
    }
}