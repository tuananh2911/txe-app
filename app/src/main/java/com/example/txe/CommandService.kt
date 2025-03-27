package com.example.txe

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommandService : Service() {
    private val TAG = "CommandService"
    private var apiService: ApiService? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CommandService created")
        try {
            apiService = ApiService(applicationContext)
            Log.d(TAG, "ApiService initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ApiService", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        when (intent?.action) {
            "HANDLE_COMMAND" -> {
                val command = intent.getStringExtra("command")
                Log.d(TAG, "Received command: $command")
                if (command != null) {
                    processCommand(command)
                } else {
                    Log.w(TAG, "Command is null")
                }
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }
        return START_NOT_STICKY
    }

    private fun processCommand(command: String) {
        Log.d(TAG, "Processing command: $command")
        serviceScope.launch {
            try {
                val result = when {
                    command == "/thoitiet" -> apiService?.getWeatherInfo() ?: "Lỗi khởi tạo service"
                    command.startsWith("/tygia") -> {
                        Log.d(TAG, "Processing exchange rate command")
                        val parts = command.split(" ")
                        Log.d(TAG, "Command parts: $parts")
                        val currencyCode = if (parts.size > 1) parts[1].trim() else ""
                        Log.d(TAG, "Extracted currencyCode: '$currencyCode'")
                        val exchangeRateInfo = apiService?.getExchangeRate(currencyCode)
                        Log.d(TAG, "Exchange rate result: $exchangeRateInfo")
                        exchangeRateInfo ?: "Lỗi khởi tạo service hoặc không lấy được tỷ giá"
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
                    else -> "Lệnh không được hỗ trợ: $command"
                }
                Log.d(TAG, "Command result: $result")
                val intent = Intent("COMMAND_RESULT").apply {
                    putExtra("command_result", result)
                }
                sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command: $command", e)
                val intent = Intent("COMMAND_RESULT").apply {
                    putExtra("command_result", "Lỗi xử lý lệnh: ${e.message}")
                }
                sendBroadcast(intent)
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
        Log.d(TAG, "CommandService destroyed")
    }
} 