package com.example.txe

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
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
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource

class FloatingWindowService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private var selectionRectParams: WindowManager.LayoutParams? = null
    private var isSelectionRectVisible = false
    private var selectionRectX = 0f
    private var selectionRectY = 0f
    private var selectionRectWidth = 300f
    private var selectionRectHeight = 300f
    private var isResizing = false
    private var resizeCorner: String? = null // "topLeft", "topRight", "bottomLeft", "bottomRight"
    private var initialTouchXForRect = 0f
    private var initialTouchYForRect = 0f
    private var initialRectX = 0f
    private var initialRectY = 0f
    private var initialRectWidth = 0f
    private var initialRectHeight = 0f
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleButton: View
    private var chatWindow: ComposeView? = null
    private var dimOverlay: ComposeView? = null
    private var overlayBitmap by mutableStateOf<Bitmap?>(null)
    private var overlayCanvas: Canvas? = null
    private var forceRedraw by mutableStateOf(0)
    private var clearedArea: Bitmap? = null
    private val messages = mutableStateListOf<Message>()
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var chatWindowParams: WindowManager.LayoutParams? = null
    private var dimOverlayParams: WindowManager.LayoutParams? = null
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var savedStateRegistryController: SavedStateRegistryController
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging = false
    private var isLongPress = false
    private var lastClickTime: Long = 0
    private var lastDoubleTapTime: Long = 0
    private val CLICK_DELAY = 300L
    private val LONG_PRESS_DELAY = 2000L
    private val DOUBLE_TAP_DELAY = 300L
    private var commandService: CommandService? = null
    private var isBound = false
    private var isReceiverRegistered = false
    private var longPressHandler: Handler? = null
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isCapturing = false
    private var isScreenCapturePermissionGranted = false
    private var screenCaptureResultCode: Int? = null
    private var screenCaptureData: Intent? = null
    private var statusBarHeight = 0

    companion object {
        private const val TAG = "FloatingWindowService"
        private const val NOTIFICATION_CHANNEL_ID = "FloatingWindowServiceChannel"
        private const val NOTIFICATION_ID = 2
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
                        messages.add(Message(cleanedResult, "", "", false, Pair(word, language)))
                    } else {
                        messages.add(Message(result, "", "", false))
                    }
                    Log.d(TAG, "System message added. Current messages size: ${messages.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating message", e)
                }
            }
        }
    }

    private val screenCaptureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SCREEN_CAPTURE_PERMISSION") {
                val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>("data")
                if (resultCode == Activity.RESULT_OK && data != null) {
                    Log.d(TAG, "Received screen capture permission")
                    val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    try {
                        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                        isScreenCapturePermissionGranted = true
                        screenCaptureResultCode = resultCode
                        screenCaptureData = data
                        setupScreenCapture()
                        showDimOverlay()
                        Log.d(TAG, "Dim overlay shown after permission")
                        Toast.makeText(this@FloatingWindowService, "Hiển thị khung chọn khu vực", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing MediaProjection", e)
                        messages.add(Message("Lỗi khởi tạo chụp màn hình: ${e.message}", "", "", false))
                    }
                } else {
                    Log.e(TAG, "Screen capture permission denied")
                    messages.add(Message("Lỗi: Quyền chụp màn hình bị từ chối", "", "", false))
                    isScreenCapturePermissionGranted = false
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
            startForegroundService()

            if (!Settings.canDrawOverlays(this)) {
                Log.e(TAG, "Quyền overlay chưa được cấp")
                Toast.makeText(this, "Cần quyền hiển thị trên màn hình để hoạt động", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }
            Log.d(TAG, "Quyền overlay đã được cấp")

            // Calculate status bar height
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            statusBarHeight = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
            Log.d(TAG, "Status bar height: $statusBarHeight")

            lifecycleRegistry = LifecycleRegistry(this)
            savedStateRegistryController = SavedStateRegistryController.create(this)
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            longPressHandler = Handler(Looper.getMainLooper())
            setupChatWindow()
            setupDimOverlay()
            setupBubbleButton()

            val filter = IntentFilter().apply {
                addAction("COMMAND_RESULT")
                addAction("SCREEN_CAPTURE_PERMISSION")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(commandResultReceiver, filter, RECEIVER_NOT_EXPORTED)
                registerReceiver(screenCaptureReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(commandResultReceiver, filter)
                registerReceiver(screenCaptureReceiver, filter)
            }
            isReceiverRegistered = true
            Log.d(TAG, "Registered receivers")

            lifecycleRegistry.currentState = Lifecycle.State.STARTED

            val intent = Intent(this, CommandService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Floating Window Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Floating Window Service")
            .setContentText("Running for screen capture")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Foreground service started")
    }

    private fun setupScreenCapture() {
        try {
            val metrics = getFullScreenMetrics()
            val width = metrics.first
            val height = metrics.second
            val density = resources.displayMetrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            isCapturing = true
            Log.d(TAG, "Screen capture setup: width=$width, height=$height, format=RGBA_8888")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up screen capture", e)
            messages.add(Message("Lỗi khởi tạo chụp màn hình: ${e.message}", "", "", false))
            isScreenCapturePermissionGranted = false
        }
    }

    private fun getFullScreenMetrics(): Pair<Int, Int> {
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            return Pair(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            display.getRealMetrics(displayMetrics)
            return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }

    @Composable
    fun DimOverlayContent(bitmap: Bitmap?) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Dim Overlay",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun captureSelectedArea() {
        try {
            bubbleButton.visibility = View.GONE
            dimOverlay?.visibility = View.GONE
            Log.d(TAG, "Bubble button and dim overlay hidden before screen capture")
            Log.d(TAG, "Visibility check - bubbleButton: ${bubbleButton.visibility}, dimOverlay: ${dimOverlay?.visibility}")

            Handler(Looper.getMainLooper()).postDelayed({
                if (dimOverlay?.visibility != View.GONE) {
                    Log.e(TAG, "Dim overlay not hidden properly before capture - dimOverlay: ${dimOverlay?.visibility}")
                    messages.add(Message("Lỗi: Không thể ẩn lớp mờ trước khi chụp", "", "", false))
                    bubbleButton.visibility = View.VISIBLE
                    return@postDelayed
                }

                val screenBitmap = captureScreen()
                if (screenBitmap == null) {
                    Log.e(TAG, "Không thể chụp màn hình")
                    messages.add(Message("Lỗi: Không thể chụp màn hình", "", "", false))
                    bubbleButton.visibility = View.VISIBLE
                    dimOverlay?.visibility = View.VISIBLE
                    return@postDelayed
                }

                bubbleButton.visibility = View.VISIBLE
                Log.d(TAG, "Bubble button shown after screen capture")

                Log.d(TAG, "Capturing area: x=$selectionRectX, y=$selectionRectY, width=$selectionRectWidth, height=$selectionRectHeight")
                Log.d(TAG, "Screen bitmap: width=${screenBitmap.width}, height=${screenBitmap.height}")

                val x = selectionRectX.toInt()
                val y = (selectionRectY - statusBarHeight).toInt() // Adjust for status bar
                val width = selectionRectWidth.toInt()
                val height = selectionRectHeight.toInt()

                val safeX = maxOf(0, minOf(x, screenBitmap.width - width))
                val safeY = maxOf(0, minOf(y, screenBitmap.height - height))
                val safeWidth = minOf(width, screenBitmap.width - safeX)
                val safeHeight = minOf(height, screenBitmap.height - safeY)

                Log.d(TAG, "Safe area: safeX=$safeX, safeY=$safeY, safeWidth=$safeWidth, safeHeight=$safeHeight")

                val croppedBitmap = Bitmap.createBitmap(screenBitmap, safeX, safeY, safeWidth, safeHeight)
                screenBitmap.recycle()

                clearedArea = croppedBitmap

                val file = File(cacheDir, "selected_area.png")
                FileOutputStream(file).use { out ->
                    clearedArea?.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val debugFile = File(getExternalFilesDir(null), "selected_area_debug.png")
                FileOutputStream(debugFile).use { out ->
                    clearedArea?.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Log.d(TAG, "Debug image saved at: ${debugFile.absolutePath}")

                serviceScope.launch {
                    messages.add(Message("Khu vực được chụp:", debugFile.absolutePath, "", false))
                    val translationService = TranslationService(this@FloatingWindowService)
                    val result = translationService.translateImage(file)
                    messages.add(Message(result ?: "Lỗi dịch ảnh", "", "", false))
                    Log.d(TAG, "Translation result added to messages: $result")
                    if (chatWindow?.visibility != View.VISIBLE) {
                        toggleChatWindow()
                    }
                }

                stopScreenCapture()
            }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing selected area", e)
            messages.add(Message("Lỗi chụp ảnh: ${e.message}", "", "", false))
            hideDimOverlay()
        }
    }

    private fun captureScreen(): Bitmap? {
        if (!isCapturing || imageReader == null) {
            Log.e(TAG, "Screen capture not initialized")
            return null
        }
        try {
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                Log.e(TAG, "No image available from ImageReader")
                return null
            }

            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val width = image.width
            val height = image.height

            Log.d(TAG, "ImageReader data: width=$width, height=$height, pixelStride=$pixelStride, rowStride=$rowStride, buffer remaining=${buffer.remaining()}")

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            val rowPadding = rowStride - pixelStride * width
            if (rowPadding == 0) {
                bitmap.copyPixelsFromBuffer(buffer)
            } else {
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                var offset = 0
                for (i in 0 until height) {
                    for (j in 0 until width) {
                        val r = bytes[offset].toInt() and 0xFF
                        val g = bytes[offset + 1].toInt() and 0xFF
                        val b = bytes[offset + 2].toInt() and 0xFF
                        val a = bytes[offset + 3].toInt() and 0xFF
                        val pixel = (a shl 24) or (r shl 16) or (g shl 8) or b
                        bitmap.setPixel(j, i, pixel)
                        offset += pixelStride
                    }
                    offset += rowPadding
                }
            }

            image.close()
            Log.d(TAG, "Screen captured: width=$width, height=$height, bitmap=$bitmap")
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screen", e)
            return null
        }
    }

    private fun stopScreenCapture() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            virtualDisplay = null
            imageReader = null
            isCapturing = false
            Log.d(TAG, "Screen capture stopped, keeping mediaProjection")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping screen capture", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupDimOverlay() {
        try {
            // Get full screen dimensions including system bars
            val (screenWidth, screenHeight) = getFullScreenMetrics()
            if (screenWidth <= 0 || screenHeight <= 0) {
                Log.e(TAG, "Kích thước màn hình không hợp lệ: width=$screenWidth, height=$screenHeight")
                Toast.makeText(this, "Lỗi: Kích thước màn hình không hợp lệ", Toast.LENGTH_LONG).show()
                return
            }

            selectionRectX = (screenWidth - selectionRectWidth) / 2
            selectionRectY = (screenHeight - selectionRectHeight) / 2 + statusBarHeight
            Log.d(TAG, "Initial selection rect: x=$selectionRectX, y=$selectionRectY, width=$selectionRectWidth, height=$selectionRectHeight")

            overlayBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
            overlayCanvas = Canvas(overlayBitmap!!)

            dimOverlay = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@FloatingWindowService)
                setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
                setContent {
                    MaterialTheme {
                        DimOverlayContent(bitmap = overlayBitmap)
                    }
                }
            }

            dimOverlayParams = WindowManager.LayoutParams(
                screenWidth,
                screenHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            // Add touch listener for dragging and resizing
            dimOverlay?.setOnTouchListener { _, event ->
                val cornerSize = 70f
                val screenWidthFloat = screenWidth.toFloat()
                val screenHeightFloat = screenHeight.toFloat()

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialTouchXForRect = event.rawX
                        initialTouchYForRect = event.rawY
                        initialRectX = selectionRectX
                        initialRectY = selectionRectY
                        initialRectWidth = selectionRectWidth
                        initialRectHeight = selectionRectHeight

                        resizeCorner = when {
                            event.rawX < selectionRectX + cornerSize && event.rawY < selectionRectY + cornerSize -> "topLeft"
                            event.rawX > selectionRectX + selectionRectWidth - cornerSize && event.rawY < selectionRectY + cornerSize -> "topRight"
                            event.rawX < selectionRectX + cornerSize && event.rawY > selectionRectY + selectionRectHeight - cornerSize -> "bottomLeft"
                            event.rawX > selectionRectX + selectionRectWidth - cornerSize && event.rawY > selectionRectY + selectionRectHeight - cornerSize -> "bottomRight"
                            else -> null
                        }
                        isResizing = resizeCorner != null
                        if (!isResizing) {
                            isDragging = true
                        }
                        Log.d(TAG, "Touch down: x=${event.rawX}, y=${event.rawY}, resizeCorner=$resizeCorner, isResizing=$isResizing, isDragging=$isDragging")

                        // Detect double tap
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastDoubleTapTime < DOUBLE_TAP_DELAY) {
                            Log.d(TAG, "Double tap detected, capturing area")
                            captureSelectedArea()
                            hideDimOverlay()
                            lastDoubleTapTime = 0
                        } else {
                            lastDoubleTapTime = currentTime
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchXForRect
                        val deltaY = event.rawY - initialTouchYForRect
                        Log.d(TAG, "Touch move: deltaX=$deltaX, deltaY=$deltaY")

                        if (isDragging) {
                            selectionRectX = (initialRectX + deltaX).coerceIn(0f, screenWidthFloat - selectionRectWidth)
                            selectionRectY = (initialRectY + deltaY).coerceIn(statusBarHeight.toFloat(), screenHeightFloat - selectionRectHeight)
                        } else if (isResizing) {
                            when (resizeCorner) {
                                "topLeft" -> {
                                    selectionRectX = initialRectX + deltaX
                                    selectionRectY = initialRectY + deltaY
                                    selectionRectWidth = initialRectWidth - deltaX
                                    selectionRectHeight = initialRectHeight - deltaY
                                }
                                "topRight" -> {
                                    selectionRectY = initialRectY + deltaY
                                    selectionRectWidth = initialRectWidth + deltaX
                                    selectionRectHeight = initialRectHeight - deltaY
                                }
                                "bottomLeft" -> {
                                    selectionRectX = initialRectX + deltaX
                                    selectionRectWidth = initialRectWidth - deltaX
                                    selectionRectHeight = initialRectHeight + deltaY
                                }
                                "bottomRight" -> {
                                    selectionRectWidth = initialRectWidth + deltaX
                                    selectionRectHeight = initialRectHeight + deltaY
                                }
                            }
                            selectionRectWidth = maxOf(100f, selectionRectWidth)
                            selectionRectHeight = maxOf(100f, selectionRectHeight)
                            selectionRectX = maxOf(0f, minOf(selectionRectX, screenWidthFloat - selectionRectWidth))
                            selectionRectY = maxOf(statusBarHeight.toFloat(), minOf(selectionRectY, screenHeightFloat - selectionRectHeight))
                        }
                        Log.d(TAG, "Updated selection rect: x=$selectionRectX, y=$selectionRectY, width=$selectionRectWidth, height=$selectionRectHeight")
                        updateDimOverlay()
                        dimOverlay?.invalidate()
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        isResizing = false
                        isDragging = false
                        resizeCorner = null
                        Log.d(TAG, "Touch up: isResizing=$isResizing, isDragging=$isDragging")
                        true
                    }
                    else -> false
                }
            }

            dimOverlay?.visibility = View.GONE
            windowManager.addView(dimOverlay, dimOverlayParams)
            Log.d(TAG, "Dim overlay added to window, dimOverlay=$dimOverlay")
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupDimOverlay", e)
            Toast.makeText(this, "Lỗi khởi tạo lớp mờ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDimOverlay() {
        if (!isScreenCapturePermissionGranted) {
            Log.d(TAG, "Starting screen capture permission request")
            ScreenCaptureActivity.start(this)
        } else {
            if (mediaProjection == null && screenCaptureResultCode != null && screenCaptureData != null) {
                try {
                    val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = projectionManager.getMediaProjection(screenCaptureResultCode!!, screenCaptureData!!)
                    Log.d(TAG, "MediaProjection reinitialized")
                } catch (e: Exception) {
                    Log.e(TAG, "Error reinitializing MediaProjection", e)
                    messages.add(Message("Lỗi khởi tạo lại MediaProjection: ${e.message}", "", "", false))
                    isScreenCapturePermissionGranted = false
                    ScreenCaptureActivity.start(this)
                    return
                }
            }
            if (mediaProjection != null) {
                setupScreenCapture()
                dimOverlay?.visibility = View.VISIBLE
                isSelectionRectVisible = true
                updateDimOverlay()
                Log.d(TAG, "Dim overlay shown, visibility=${dimOverlay?.visibility}")
                Toast.makeText(this, "Lớp mờ được hiển thị, nhấn đúp để chụp", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "MediaProjection is null, requesting permission")
                ScreenCaptureActivity.start(this)
            }
        }
    }

    private fun hideDimOverlay() {
        dimOverlay?.visibility = View.GONE
        overlayBitmap?.eraseColor(android.graphics.Color.BLACK and 0xB0FFFFFF.toInt())
        isSelectionRectVisible = false
        Log.d(TAG, "Dim overlay hidden")
    }

    private fun updateDimOverlay() {
        overlayCanvas?.let { canvas ->
            // Clear the bitmap with a semi-transparent black overlay
            canvas.drawColor(android.graphics.Color.BLACK and 0xB0FFFFFF.toInt(), PorterDuff.Mode.SRC)

            // Paint for clearing the selected area (transparent)
            val clearPaint = Paint().apply {
                color = android.graphics.Color.TRANSPARENT
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                isAntiAlias = true
            }

            // Paint for drawing the corner lines (Google Lens style)
            val cornerPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 6f // Slightly thicker for visibility
                isAntiAlias = true
            }

            // Coordinates for the transparent area
            val clearX = selectionRectX
            val clearY = selectionRectY
            val clearWidth = selectionRectWidth
            val clearHeight = selectionRectHeight

            // Clear the selected area to make it transparent
            canvas.drawRect(
                clearX,
                clearY,
                clearX + clearWidth,
                clearY + clearHeight,
                clearPaint
            )

            // Define corner line length and offset
            val cornerLineLength = 30f // Length of the corner lines
            val cornerOffset = 5f // Small offset to create a gap at the corners

            // Top-left corner: Draw two lines forming an "L" shape
            // Horizontal line (left to right)
            canvas.drawLine(
                clearX - cornerOffset,
                clearY,
                clearX + cornerLineLength,
                clearY,
                cornerPaint
            )
            // Vertical line (top to bottom)
            canvas.drawLine(
                clearX,
                clearY - cornerOffset,
                clearX,
                clearY + cornerLineLength,
                cornerPaint
            )

            // Top-right corner
            // Horizontal line (right to left)
            canvas.drawLine(
                clearX + clearWidth - cornerLineLength,
                clearY,
                clearX + clearWidth + cornerOffset,
                clearY,
                cornerPaint
            )
            // Vertical line (top to bottom)
            canvas.drawLine(
                clearX + clearWidth,
                clearY - cornerOffset,
                clearX + clearWidth,
                clearY + cornerLineLength,
                cornerPaint
            )

            // Bottom-left corner
            // Horizontal line (left to right)
            canvas.drawLine(
                clearX - cornerOffset,
                clearY + clearHeight,
                clearX + cornerLineLength,
                clearY + clearHeight,
                cornerPaint
            )
            // Vertical line (bottom to top)
            canvas.drawLine(
                clearX,
                clearY + clearHeight - cornerLineLength,
                clearX,
                clearY + clearHeight + cornerOffset,
                cornerPaint
            )

            // Bottom-right corner
            // Horizontal line (right to left)
            canvas.drawLine(
                clearX + clearWidth - cornerLineLength,
                clearY + clearHeight,
                clearX + clearWidth + cornerOffset,
                clearY + clearHeight,
                cornerPaint
            )
            // Vertical line (bottom to top)
            canvas.drawLine(
                clearX + clearWidth,
                clearY + clearHeight - cornerLineLength,
                clearX + clearWidth,
                clearY + clearHeight + cornerOffset,
                cornerPaint
            )

            Log.d(TAG, "Updated overlay: cleared area with Google Lens-style corners at x=$clearX, y=$clearY, width=$clearWidth, height=$clearHeight")
            forceRedraw++
            dimOverlay?.invalidate()
            dimOverlay?.requestLayout()
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
                        BubbleIcon(
                            isChatOpen = chatWindow?.visibility == View.VISIBLE,
                            isLongPress = isLongPress
                        )
                    }
                }
            }

            val size = (48 * resources.displayMetrics.density).toInt()
            val params = WindowManager.LayoutParams(
                size,
                size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 16
                y = 100
            }

            bubbleComposeView.setOnTouchListener { view, event ->
                Log.d(TAG, "Touch event: ${event.action}, isLongPress=$isLongPress, isDragging=$isDragging")
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d(TAG, "ACTION_DOWN: Bắt đầu chạm, khởi động long press handler")
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        isLongPress = false
                        longPressHandler?.removeCallbacksAndMessages(null)
                        longPressHandler?.postDelayed({
                            if (!isLongPress) {
                                Log.d(TAG, "Long press triggered, isScreenCapturePermissionGranted=$isScreenCapturePermissionGranted")
                                isLongPress = true
                                showDimOverlay()
                                bubbleComposeView.setContent {
                                    MaterialTheme {
                                        BubbleIcon(
                                            isChatOpen = chatWindow?.visibility == View.VISIBLE,
                                            isLongPress = isLongPress
                                        )
                                    }
                                }
                                Toast.makeText(this@FloatingWindowService, "Giữ lâu: Hiển thị lớp mờ và hình chữ nhật", Toast.LENGTH_SHORT).show()
                            }
                        }, LONG_PRESS_DELAY)
                        lastClickTime = System.currentTimeMillis()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        Log.d(TAG, "ACTION_MOVE: deltaX=$deltaX, deltaY=$deltaY, isDragging=$isDragging")

                        if (!isDragging && (kotlin.math.abs(deltaX) > 20 || kotlin.math.abs(deltaY) > 20)) {
                            isDragging = true
                            Log.d(TAG, "Started dragging")
                        }

                        if (isDragging) {
                            params.x = (initialX - deltaX).toInt()
                            params.y = (initialY + deltaY).toInt()
                            windowManager.updateViewLayout(view, params)
                            Log.d(TAG, "ACTION_MOVE: x=${params.x}, y=${params.y}")
                        }

                        if (isDragging && kotlin.math.abs(deltaX) < 5 && kotlin.math.abs(deltaY) < 5) {
                            if (!isLongPress) {
                                longPressHandler?.removeCallbacksAndMessages(null)
                                longPressHandler?.postDelayed({
                                    if (!isLongPress) {
                                        Log.d(TAG, "Long press triggered after drag pause, isScreenCapturePermissionGranted=$isScreenCapturePermissionGranted")
                                        isLongPress = true
                                        showDimOverlay()
                                        bubbleComposeView.setContent {
                                            MaterialTheme {
                                                BubbleIcon(
                                                    isChatOpen = chatWindow?.visibility == View.VISIBLE,
                                                    isLongPress = isLongPress
                                                )
                                            }
                                        }
                                        Toast.makeText(this@FloatingWindowService, "Giữ lâu: Hiển thị lớp mờ và hình chữ nhật", Toast.LENGTH_SHORT).show()
                                    }
                                }, LONG_PRESS_DELAY)
                            }
                        }

                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        longPressHandler?.removeCallbacksAndMessages(null)
                        if (isDragging) {
                            val screenWidth = getFullScreenMetrics().first
                            params.x = if (params.x > screenWidth / 2 - size / 2) screenWidth - size - 16 else 16
                            windowManager.updateViewLayout(view, params)
                            Log.d(TAG, "ACTION_UP: Dragged to x=${params.x}")
                        } else if (System.currentTimeMillis() - lastClickTime < CLICK_DELAY && !isLongPress) {
                            Log.d(TAG, "Click detected, toggling chat window")
                            toggleChatWindow()
                            bubbleComposeView.setContent {
                                MaterialTheme {
                                    BubbleIcon(
                                        isChatOpen = chatWindow?.visibility == View.VISIBLE,
                                        isLongPress = isLongPress
                                    )
                                }
                            }
                        }
                        isDragging = false
                        isLongPress = false
                        bubbleComposeView.setContent {
                            MaterialTheme {
                                BubbleIcon(
                                    isChatOpen = chatWindow?.visibility == View.VISIBLE,
                                    isLongPress = isLongPress
                                )
                            }
                        }
                        Log.d(TAG, "ACTION_UP: isLongPress reset to $isLongPress, isDragging reset to $isDragging")
                        true
                    }
                    else -> {
                        Log.d(TAG, "Other touch event: ${event.action}")
                        false
                    }
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
    fun BubbleIcon(isChatOpen: Boolean, isLongPress: Boolean) {
        Box(
            modifier = Modifier
                .size(50.dp)
//                .shadow(4.dp, CircleShape)
//                .background(
//                    if (isChatOpen) Color.White else Color.Gray.copy(alpha = 0.2f),
//                    CircleShape
//                )
                .alpha(if (!isChatOpen && !isLongPress) 0.5f else 1.0f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.jj_icon), // Use the custom jj icon
                contentDescription = "Chat Bubble",
                modifier = Modifier.size(50.dp),
                colorFilter = ColorFilter.tint(
                    when {
                        isLongPress -> Color.Red
                        isChatOpen -> Color(0xFF00FFFF) // Cyan when chat is open
                        else -> Color.Gray
                    }
                )
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

            val (screenWidth, screenHeight) = getFullScreenMetrics()
            val chatWindowWidth = (screenWidth * 0.95).toInt()
            val chatWindowHeight = (screenHeight * 0.7).toInt()

            chatWindowParams = WindowManager.LayoutParams(
                chatWindowWidth,
                chatWindowHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
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
                        params.y = statusBarHeight + 80 - keyboardHeight
                    } else {
                        params.height = chatWindowHeight
                        params.y = statusBarHeight + 80
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
                val bubbleParams = bubbleButton.layoutParams as WindowManager.LayoutParams
                val (screenWidth, _) = getFullScreenMetrics()
                bubbleParams.x = (screenWidth - bubbleButton.width) / 2
                bubbleParams.y = statusBarHeight + 16
                windowManager.updateViewLayout(bubbleButton, bubbleParams)

                chatWindowParams?.let { params ->
                    params.y = statusBarHeight + 80
                    params.height = if (window.height > 0) window.height else params.height
                    windowManager.updateViewLayout(window, params)
                }

                chatWindowParams?.flags = chatWindowParams?.flags?.and(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv())
                windowManager.updateViewLayout(window, chatWindowParams)
                window.requestFocus()
            } else {
                chatWindowParams?.flags = chatWindowParams?.flags?.or(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                windowManager.updateViewLayout(window, chatWindowParams)
            }

            (bubbleButton as ComposeView).setContent {
                MaterialTheme {
                    BubbleIcon(
                        isChatOpen = window.visibility == View.VISIBLE,
                        isLongPress = isLongPress
                    )
                }
            }
        } ?: Log.w(TAG, "Chat window is null in toggleChatWindow")
    }

    private fun handleCommand(command: String) {
        if (command.isBlank()) return
        serviceScope.launch {
            try {
                Log.d(TAG, "Handling command: $command")
                messages.add(Message("", "", command, true))
                Log.d(TAG, "User message added. Current messages size: ${messages.size}")
                if (isBound && commandService != null) {
                    commandService?.processCommand(command)
                } else {
                    val commandIntent = Intent(this@FloatingWindowService, CommandService::class.java)
                    commandIntent.action = "COMMAND_DIRECT"
                    commandIntent.putExtra("command", command)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(commandIntent)
                    } else {
                        startService(commandIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling command", e)
                messages.add(Message("Lỗi xử lý lệnh: ${e.message}", "", "", false))
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
                unregisterReceiver(screenCaptureReceiver)
                isReceiverRegistered = false
                Log.d(TAG, "Unregistered receivers")
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
            dimOverlay?.let {
                windowManager.removeView(it)
                dimOverlay = null
            }

            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
            virtualDisplay = null
            imageReader = null
            mediaProjection = null
            isCapturing = false
            overlayBitmap?.recycle()
            clearedArea?.recycle()

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