package com.example.txe

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
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
import android.widget.FrameLayout
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
    private lateinit var bubbleButton: FrameLayout
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
    private var commandService: CommandService? = null
    private var isBound = false

    companion object {
        private const val TAG = "FloatingWindowService"
    }

    // ServiceConnection để bind với CommandService
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            val binder = service as CommandService.LocalBinder
            commandService = binder.getService()
            isBound = true
            Log.d(TAG, "CommandService bound successfully")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            commandService = null
            isBound = false
        }
    }

    // BroadcastReceiver để nhận kết quả từ CommandService
    private val commandResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received broadcast with action: ${intent?.action}")
            val result = intent?.getStringExtra("command_result") ?: "Không có kết quả"
            Log.d(TAG, "Received command result: $result")
            serviceScope.launch {
                try {
                    messageAdapter?.updateLastMessage(result)
                    messageList?.scrollToPosition(messageAdapter?.itemCount?.minus(1) ?: 0)
                    Log.d(TAG, "Updated message adapter with result")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating message", e)
                }
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

            // Đăng ký BroadcastReceiver
            val filter = IntentFilter("COMMAND_RESULT")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(commandResultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(commandResultReceiver, filter)
            }
            Log.d(TAG, "Registered command result receiver with filter: ${filter.actionsIterator().asSequence().toList()}")

            // Bind với CommandService
            val intent = Intent(this, CommandService::class.java)
            startService(intent) // Thêm dòng này
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "Binding to CommandService")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Lỗi khởi tạo service: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupBubbleButton() {
        try {
            bubbleButton = LayoutInflater.from(this).inflate(R.layout.float_bubble, null) as FrameLayout
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
                if (chatWindow?.visibility == View.VISIBLE) {
                    // Nếu chat window đang mở, chỉ cho phép click không cho kéo
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            lastClickTime = System.currentTimeMillis()
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (System.currentTimeMillis() - lastClickTime < CLICK_DELAY) {
                                toggleChatWindow()
                            }
                            true
                        }
                        else -> true
                    }
                } else {
                    // Nếu chat window đóng, cho phép kéo bình thường
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
            val minimizeButton = chatWindow?.findViewById<ImageButton>(R.id.minimizeButton)

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

            minimizeButton?.setOnClickListener {
                chatWindow?.visibility = View.GONE
            }

            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // Lấy chiều cao của status bar
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarHeight = if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else {
                0
            }

            // Thiết lập kích thước cửa sổ chat
            val chatWindowWidth = (screenWidth * 0.95).toInt() // 95% chiều rộng màn hình
            val chatWindowHeight = (screenHeight * 0.7).toInt() // 70% chiều cao màn hình

            chatWindowParams = WindowManager.LayoutParams(
                chatWindowWidth,
                chatWindowHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = statusBarHeight + 80 // Để trống chỗ cho bong bóng
            }

            chatWindow?.viewTreeObserver?.addOnGlobalLayoutListener {
                val rect = Rect()
                chatWindow?.getWindowVisibleDisplayFrame(rect)
                val keyboardHeight = screenHeight - rect.bottom

                if (keyboardHeight > screenHeight * 0.15) {
                    // Khi bàn phím hiện
                    // Di chuyển cả bong bóng và cửa sổ chat lên trên để tránh bàn phím
                    val bubbleParams = bubbleButton.layoutParams as WindowManager.LayoutParams
                    bubbleParams.y = statusBarHeight + 16 // Giữ khoảng cách với status bar
                    windowManager.updateViewLayout(bubbleButton, bubbleParams)

                    chatWindowParams?.let { params ->
                        params.height = screenHeight - keyboardHeight - (statusBarHeight + 80)
                        params.y = statusBarHeight + 80 // Giữ khoảng cách với bong bóng
                        messageList?.setPadding(0, 0, 0, 0)
                        chatWindow?.let { windowManager.updateViewLayout(it, params) }
                    }
                } else {
                    // Khi bàn phím ẩn
                    chatWindowParams?.let { params ->
                        params.height = chatWindowHeight
                        params.y = statusBarHeight + 80
                        messageList?.setPadding(0, 0, 0, 0)
                        chatWindow?.let { windowManager.updateViewLayout(it, params) }
                    }
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
            val isOpening = window.visibility != View.VISIBLE
            window.visibility = if (isOpening) View.VISIBLE else View.GONE

            if (isOpening) {
                // Lấy chiều cao của status bar
                val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
                val statusBarHeight = if (resourceId > 0) {
                    resources.getDimensionPixelSize(resourceId)
                } else {
                    0
                }

                // Di chuyển bong bóng vào giữa màn hình, dưới status bar
                val bubbleParams = bubbleButton.layoutParams as WindowManager.LayoutParams
                val displayMetrics = resources.displayMetrics
                bubbleParams.x = (displayMetrics.widthPixels - bubbleButton.width) / 2
                bubbleParams.y = statusBarHeight + 16 // Cách status bar 16dp
                windowManager.updateViewLayout(bubbleButton, bubbleParams)

                // Đặt cửa sổ chat ở giữa màn hình, bắt đầu từ dưới bong bóng
                chatWindowParams?.let { params ->
                    params.y = statusBarHeight + 80 // Để trống chỗ cho bong bóng (80dp)
                    params.height = if (window.height > 0) window.height else params.height
                    windowManager.updateViewLayout(window, params)
                }

                // Hiện bàn phím và focus vào ô input
                inputField?.postDelayed({
                    inputField?.requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)
                }, 100)
            } else {
                // Ẩn bàn phím khi đóng cửa sổ chat
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(inputField?.windowToken, 0)
            }
        } ?: Log.w(TAG, "Chat window is null in toggleChatWindow")
    }

    private fun handleCommand(command: String) {
        if (command.isBlank()) return

        serviceScope.launch {
            try {
                Log.d(TAG, "Handling command: $command")
                messageAdapter?.addMessage(Message(command, "Đang xử lý..."))
                messageList?.scrollToPosition(messageAdapter?.itemCount?.minus(1) ?: 0)
                
                if (isBound && commandService != null) {
                    // Gửi lệnh trực tiếp đến CommandService
                    Log.d(TAG, "Sending command directly to CommandService: $command")
                    commandService?.processCommand(command)
                } else {
                    // Fallback: sử dụng intent
                    val commandIntent = Intent(this@FloatingWindowService, CommandService::class.java)
                    commandIntent.action = "COMMAND_DIRECT"
                    commandIntent.putExtra("command", command)
                    startService(commandIntent)
                    Log.d(TAG, "Started CommandService with command")
                }
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
                serviceString?.contains("${packageName}") ?: false
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service", e)
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Unbind service
            if (isBound) {
                unbindService(serviceConnection)
                isBound = false
                Log.d(TAG, "Unbound from CommandService")
            }

            // Unregister the receiver first
            try {
                unregisterReceiver(commandResultReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }

            // Remove views
            try {
                windowManager.removeView(bubbleButton)
                chatWindow?.let { windowManager.removeView(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing views", e)
            }

            // Stop the command service
            try {
                val commandIntent = Intent(this, CommandService::class.java)
                stopService(commandIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping command service", e)
            }

            Log.d(TAG, "Service destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }
}