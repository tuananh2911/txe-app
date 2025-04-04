package com.example.txe

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommandService : Service() {
    private val TAG = "CommandService"
    private var apiService: ApiService? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    
    // Binder class để bind service
    inner class LocalBinder : Binder() {
        fun getService(): CommandService = this@CommandService
    }
    
    private val binder = LocalBinder()
    
    // BroadcastReceiver để nhận lệnh trực tiếp
    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received broadcast with action: ${intent?.action}")
            val command = intent?.getStringExtra("command")
            if (command != null) {
                Log.d(TAG, "Processing command from broadcast: $command")
                processCommand(command)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CommandService created")
        apiService = ApiService(applicationContext)
        Log.d(TAG, "ApiService initialized")
        try {
            // Đăng ký BroadcastReceiver với cờ RECEIVER_NOT_EXPORTED
            val filter = IntentFilter("COMMAND_DIRECT")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14
                registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(commandReceiver, filter) // Giữ nguyên cho phiên bản cũ
            }
            Log.d(TAG, "Registered command receiver with filter: ${filter.actionsIterator().asSequence().toList()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing command receiver", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with intent: $intent")
        val command = intent?.getStringExtra("command")
        Log.d(TAG, "Received command: $command")
        if (command != null) {
            processCommand(command)
        }
        return START_NOT_STICKY
    }

    // Expose processCommand method for direct calls
    fun processCommand(command: String) {
        Log.d(TAG, "Processing command: $command")
        serviceScope.launch {
            try {
                val result = when {
                    command.startsWith("/thoitiet") -> {
                        Log.d(TAG, "Processing weather command")
                        apiService?.getWeatherInfo() ?: "Không thể lấy thông tin thời tiết"
                    }
                    command.startsWith("/tygia") -> {
                        Log.d(TAG, "Processing exchange rate command")
                        val currency = command.substringAfter("/tygia").trim().uppercase()
                        Log.d(TAG, "Getting exchange rate for currency: $currency")
                        apiService?.getExchangeRate(currency) ?: "Không thể lấy tỷ giá"
                    }
                    command == "/amlich" -> apiService?.getLunarDate() ?: "Lỗi khởi tạo service"
                    command.startsWith("/phatnguoi") -> {
                        val parts = command.split(" ")
                        if (parts.size > 1) {
                            val plateNumber = parts[1].trim()
                            apiService?.getTrafficViolations(plateNumber) ?: "Lỗi khởi tạo service"
                        } else {
                            "Vui lòng nhập biển số xe. Ví dụ: /phatnguoi 30A-12345"
                        }
                    }
                    else -> "Lệnh không hợp lệ"
                }
                Log.d(TAG, "Command result: $result")
                sendBroadcast(Intent("COMMAND_RESULT").apply {
                    putExtra("command_result", result)
                    setPackage("com.example.txe") // Thêm dòng này
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                sendBroadcast(Intent("COMMAND_RESULT").apply {
                    putExtra("command_result", "Lỗi xử lý lệnh: ${e.message}")
                })
            }
        }
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            try {
                Log.d(TAG, "Showing toast: $message")
                Toast.makeText(this@CommandService, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error showing toast", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Unregister the receiver
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        serviceScope.coroutineContext.cancelChildren()
        Log.d(TAG, "CommandService destroyed")
    }
} 