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
import androidx.compose.ui.text.toLowerCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class CommandService : Service() {
    private val TAG = "CommandService"
    private var apiService: ApiService? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var isGameActive = false
    private var lastWord: String? = null
    private var gameJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): CommandService = this@CommandService
    }

    private val binder = LocalBinder()

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
            val filter = IntentFilter("COMMAND_DIRECT")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(commandReceiver, filter)
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

    fun processCommand(command: String) {
        Log.d(TAG, "Processing command: $command")
        val lowerCommand = command.lowercase() // Chuyển về lowercase
        serviceScope.launch {
            try {
                val result = when {
                    lowerCommand.startsWith("/thoitiet") -> {
                        Log.d(TAG, "Processing weather command")
                        apiService?.getWeatherInfo() ?: "Không thể lấy thông tin thời tiết"
                    }
                    lowerCommand.startsWith("/tygia") -> {
                        Log.d(TAG, "Processing exchange rate command")
                        val currency = lowerCommand.substringAfter("/tygia").trim()
                        Log.d(TAG, "Getting exchange rate for currency: $currency")
                        apiService?.getExchangeRate(currency) ?: "Không thể lấy tỷ giá"
                    }
                    lowerCommand == "/amlich" -> apiService?.getLunarDate() ?: "Lỗi khởi tạo service"
                    lowerCommand.startsWith("/phatnguoi") -> {
                        val parts = lowerCommand.split(" ")
                        if (parts.size > 1) {
                            val plateNumber = parts[1].trim()
                            apiService?.getTrafficViolations(plateNumber) ?: "Lỗi khởi tạo service"
                        } else {
                            "Vui lòng nhập biển số xe. Ví dụ: /phatnguoi 30a-12345"
                        }
                    }
                    lowerCommand.startsWith("/noitu") -> {
                        if (!isGameActive) {
                            handleWordChainGame(lowerCommand)
                        } else {
                            handleWordChainResponse(lowerCommand)
                        }
                    }
                    isGameActive -> {
                        "Đang trong phiên chơi nối từ! Dùng /noitu để tiếp tục, ví dụ: /noitu $lastWord <từ mới>"
                    }
                    else -> "Lệnh không hợp lệ"
                }
                Log.d(TAG, "Command result: $result")
                sendBroadcast(Intent("COMMAND_RESULT").apply {
                    putExtra("command_result", result)
                    setPackage("com.example.txe")
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                sendBroadcast(Intent("COMMAND_RESULT").apply {
                    putExtra("command_result", "Lỗi xử lý lệnh: ${e.message}")
                })
            }
        }
    }

    private suspend fun handleWordChainGame(command: String): String {
        val input = command.removePrefix("/noitu").trim()

        if (input.isEmpty()) {
            return "Vui lòng nhập từ đầu tiên, ví dụ: /noitu con chó"
        }

        val words = input.split(" ").filter { it.isNotBlank() }
        if (words.size != 2) {
            return "Vui lòng nhập đúng 2 từ!"
        }

        isGameActive = true
        lastWord = words.last()
        println("words $words")
        val response = startGameTurn("${words[0]} ${words[1]}")
        return "Game nối từ:\nTừ của bạn: $input. \nHệ thống: $response"

    }

    private suspend fun handleWordChainResponse(command: String): String {
        val input = command.removePrefix("/noitu").trim()
        val words = input.split(" ").filter { it.isNotBlank() }
        if (words.size != 2) {
            return "Vui lòng nhập đúng 2 từ với /noitu!"
        }

//        if (words.first().toLowerCase() != lastWord?.toLowerCase()) {
//            return "Từ đầu phải là '$lastWord'! Thử lại với /noitu $lastWord <từ mới>"
//        }

        gameJob?.cancel()
        lastWord = words.last()
        val response = startGameTurn("${words[0]} ${words[1]}")
        return "Từ của bạn: $input. Hệ thống: $response"
    }

    private suspend fun startGameTurn(userWord: String): String {
        println("userword ${userWord}")
        val response = apiService?.chatGeminiApi(userWord)
        println("respone $response")
        if (response != null) {
            lastWord = response.split(" ").filter { it.isNotBlank() }[1]
            startGameTimeout()
            return "$response"
        } else {
            endGame()
            return "Tôi hết từ rồi! Bạn thắng!"
        }
    }

    private fun startGameTimeout() {
        gameJob?.cancel()
        gameJob = serviceScope.launch {
            delay(60_000) // 1 phút
            if (isGameActive) {
                sendBroadcast(Intent("COMMAND_RESULT").apply {
                    putExtra("command_result", "Hết 1 phút không trả lời, game kết thúc!")
                    setPackage("com.example.txe")
                })
                endGame()
            }
        }
    }

    private fun endGame() {
        isGameActive = false
        lastWord = null
        gameJob?.cancel()
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
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        serviceScope.coroutineContext.cancelChildren()
        endGame()
        Log.d(TAG, "CommandService destroyed")
    }
}