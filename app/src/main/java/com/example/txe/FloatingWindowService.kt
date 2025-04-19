package com.example.txe

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Outline
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FloatingWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleButton: View // Thay FrameLayout bằng View để tương thích với ComposeView
    private var chatWindow: ComposeView? = null
    private val messages = mutableStateListOf<Message>()
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var chatWindowParams: WindowManager.LayoutParams? = null
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var savedStateRegistryController: SavedStateRegistryController
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging = false
    private var lastClickTime: Long = 0
    private val CLICK_DELAY = 300L
    private var commandService: CommandService? = null
    private var isBound = false
    private var isReceiverRegistered = false

    companion object {
        private const val TAG = "FloatingWindowService"
    }

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

    private val commandResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received broadcast with action: ${intent?.action}")
            val result = intent?.getStringExtra("command_result") ?: "Không có kết quả"
            Log.d(TAG, "Received command result: $result")
            serviceScope.launch {
                try {
                    val speakerRegex = Regex("\\[SPEAKER:([^:]+):([^]]+)]")
                    val match = speakerRegex.find(result)
                    if (match != null) {
                        val word = match.groupValues[1]
                        val language = match.groupValues[2]
                        val cleanedResult = result.replace(speakerRegex, "").trim()
                        messages.add(Message(cleanedResult, "", false, Pair(word, language)))
                    } else {
                        messages.add(Message(result, "", false))
                    }
                    Log.d(TAG, "System message added. Current messages size: ${messages.size}")
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

            lifecycleRegistry = LifecycleRegistry(this)
            savedStateRegistryController = SavedStateRegistryController.create(this)
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            setupChatWindow()
            setupBubbleButton()

            val filter = IntentFilter("COMMAND_RESULT")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(commandResultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(commandResultReceiver, filter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "Registered command result receiver")

            lifecycleRegistry.currentState = Lifecycle.State.STARTED

            val intent = Intent(this, CommandService::class.java)
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupBubbleButton() {
        try {
            val bubbleComposeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingWindowService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
                setContent {
                    MaterialTheme {
                        BubbleIcon(isChatOpen = chatWindow?.visibility == View.VISIBLE)
                    }
                }
            }

            val size = (48 * resources.displayMetrics.density).toInt()
            val params = WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 16
                y = 100
            }

            bubbleComposeView.setOnTouchListener { view, event ->
                Log.d(TAG, "Touch event: ${event.action}") 
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        lastClickTime = System.currentTimeMillis()
                        Log.d(TAG, "ACTION_DOWN: initialX=$initialX, initialY=$initialY")
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        if (!isDragging && (kotlin.math.abs(deltaX) > 5 || kotlin.math.abs(deltaY) > 5)) {
                            isDragging = true
                            Log.d(TAG, "Started dragging")
                        }
                        if (isDragging) {
                            params.x = (initialX - deltaX).toInt()
                            params.y = (initialY + deltaY).toInt()
                            windowManager.updateViewLayout(view, params)
                            Log.d(TAG, "ACTION_MOVE: deltaX=$deltaX, x=${params.x}, y=${params.y}")
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            val screenWidth = resources.displayMetrics.widthPixels
                            params.x = if (params.x > screenWidth / 2 - size / 2) screenWidth - size - 16 else 16
                            windowManager.updateViewLayout(view, params)
                            Log.d(TAG, "ACTION_UP: Dragged to x=${params.x}")
                        } else if (System.currentTimeMillis() - lastClickTime < CLICK_DELAY) {
                            Log.d(TAG, "Click detected, toggling chat window")
                            toggleChatWindow()
                            bubbleComposeView.setContent {
                                MaterialTheme {
                                    BubbleIcon(isChatOpen = chatWindow?.visibility == View.VISIBLE)
                                }
                            }
                        }
                        isDragging = false
                        true
                    }
                    else -> false
                }
            }

            bubbleButton = bubbleComposeView
            windowManager.addView(bubbleButton, params)
            Log.d(TAG, "Bubble button added to window with Compose")
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupBubbleButton", e)
            Toast.makeText(this, "Lỗi khởi tạo nút nổi: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    @Composable
    fun BubbleIcon(isChatOpen: Boolean) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(4.dp, CircleShape)
                .background(
                    if (isChatOpen) Color.White else Color.Gray.copy(alpha = 0.2f), // Nền trắng khi mở, xám mờ khi đóng
                    CircleShape
                )
                .alpha(if (!isChatOpen) 0.5f else 1.0f), // Chỉ mờ khi chat đóng
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "Chat Bubble",
                tint = if (isChatOpen) Color(0xFF00FFFF) else Color.Gray, // Cyan khi mở, xám khi đóng
                modifier = Modifier.size(32.dp)
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupChatWindow() {
        try {
            val composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingWindowService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
                setContent {
                    MaterialTheme {
                        ChatWindowContent(
                            messages = messages,
                            onMinimize = { chatWindow?.visibility = View.GONE },
                            onSendCommand = { command -> handleCommand(command) }
                        )
                    }
                }
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, 16f * resources.displayMetrics.density)
                    }
                }
            }

            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val statusBarHeight = resources.getDimensionPixelSize(
                resources.getIdentifier("status_bar_height", "dimen", "android")
            )
            val chatWindowWidth = (screenWidth * 0.95).toInt()
            val chatWindowHeight = (screenHeight * 0.7).toInt()

            chatWindowParams = WindowManager.LayoutParams(
                chatWindowWidth,
                chatWindowHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = statusBarHeight + 80
            }

            chatWindow = composeView
            chatWindow?.let {
                windowManager.addView(it, chatWindowParams)
                it.visibility = View.GONE
                Log.d(TAG, "Chat window added to window")
            }

            chatWindow?.viewTreeObserver?.addOnGlobalLayoutListener {
                val rect = android.graphics.Rect()
                chatWindow?.getWindowVisibleDisplayFrame(rect)
                val keyboardHeight = screenHeight - rect.bottom
                chatWindowParams?.let { params ->
                    if (keyboardHeight > screenHeight * 0.15) {
                        params.height = chatWindowHeight - keyboardHeight
                    } else {
                        params.height = chatWindowHeight
                    }
                    if (params.height < 100) {
                        params.height = 100
                    }
                    chatWindow?.let { window ->
                        window.clipToOutline = true
                        windowManager.updateViewLayout(window, params)
                        window.invalidateOutline()
                        window.invalidate()
                        Log.d(TAG, "KeyboardHeight: $keyboardHeight, WindowHeight: ${params.height}, Y: ${params.y}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupChatWindow", e)
            Toast.makeText(this, "Lỗi khởi tạo cửa sổ chat: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleChatWindow() {
        chatWindow?.let { window ->
            val isOpening = window.visibility != View.VISIBLE
            Log.d(TAG, "Toggling chat window: isOpening=$isOpening, current visibility=${window.visibility}")
            window.visibility = if (isOpening) View.VISIBLE else View.GONE

            if (isOpening) {
                val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
                val statusBarHeight = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0

                val bubbleParams = bubbleButton.layoutParams as WindowManager.LayoutParams
                val displayMetrics = resources.displayMetrics
                bubbleParams.x = (displayMetrics.widthPixels - bubbleButton.width) / 2
                bubbleParams.y = statusBarHeight + 16
                windowManager.updateViewLayout(bubbleButton, bubbleParams)

                chatWindowParams?.let { params ->
                    params.y = statusBarHeight + 80
                    params.height = if (window.height > 0) window.height else params.height
                    windowManager.updateViewLayout(window, params)
                }
            }
            // Cập nhật lại bubble button để thay đổi màu icon
            (bubbleButton as ComposeView).setContent {
                MaterialTheme {
                    BubbleIcon(isChatOpen = window.visibility == View.VISIBLE)
                }
            }
        } ?: Log.w(TAG, "Chat window is null in toggleChatWindow")
    }

    private fun handleCommand(command: String) {
        if (command.isBlank()) return
        serviceScope.launch {
            try {
                Log.d(TAG, "Handling command: $command")
                messages.add(Message(command, "", true))
                Log.d(TAG, "User message added. Current messages size: ${messages.size}")
                if (isBound && commandService != null) {
                    commandService?.processCommand(command)
                } else {
                    val commandIntent = Intent(this@FloatingWindowService, CommandService::class.java)
                    commandIntent.action = "COMMAND_DIRECT"
                    commandIntent.putExtra("command", command)
                    startService(commandIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling command", e)
                messages.add(Message("Lỗi xử lý lệnh: ${e.message}", "", false))
                Log.d(TAG, "Error message added. Current messages size: ${messages.size}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            savedStateRegistryController.performSave(Bundle())

            if (isReceiverRegistered) {
                unregisterReceiver(commandResultReceiver)
                isReceiverRegistered = false
                Log.d(TAG, "Unregistered command result receiver")
            }

            if (isBound) {
                unbindService(serviceConnection)
                isBound = false
                Log.d(TAG, "Unbound from CommandService")
            }

            try {
                windowManager.removeView(bubbleButton)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing bubbleButton", e)
            }
            chatWindow?.let {
                windowManager.removeView(it)
                chatWindow = null
            }

            val commandIntent = Intent(this, CommandService::class.java)
            stopService(commandIntent)

            Log.d(TAG, "Service destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
}