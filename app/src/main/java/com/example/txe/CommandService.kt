package com.example.txe

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class CommandService : Service() {
    private val TAG = "CommandService"
    private var apiService: ApiService? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var isGameActive = false
    private var lastWord: String? = null
    private var gameJob: Job? = null
    private var validWords: List<String> = emptyList()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "CommandServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

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
        startForegroundService()
        apiService = ApiService(applicationContext)
        Log.d(TAG, "ApiService initialized")
        loadWordList()
        try {
            val filter = IntentFilter("COMMAND_DIRECT")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(commandReceiver, filter)
            }
            Log.d(
                TAG,
                "Registered command receiver with filter: ${
                    filter.actionsIterator().asSequence().toList()
                }"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing command receiver", e)
        }
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Command Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Command Service")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Foreground service started")
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
        val lowerCommand = command.lowercase()
        serviceScope.launch {
            try {
                val result = when {
                    !isGameActive && lowerCommand.startsWith("jjthoitiet") -> {
                        Log.d(TAG, "Processing weather command")
                        apiService?.getWeatherInfo() ?: "Không thể lấy thông tin thời tiết"
                    }
                    !isGameActive && lowerCommand.startsWith("jjtygia") -> {
                        Log.d(TAG, "Processing exchange rate command")
                        val currency = lowerCommand.substringAfter("jjtygia").trim()
                        Log.d(TAG, "Getting exchange rate for currency: $currency")
                        apiService?.getExchangeRate(currency) ?: "Không thể lấy tỷ giá"
                    }
                    !isGameActive && lowerCommand == "jjamlich" -> {
                        apiService?.getLunarDate() ?: "Lỗi khởi tạo service"
                    }
                    !isGameActive && lowerCommand.startsWith("jjdich") -> {
                        val afterPrefix = lowerCommand.substringAfter("jjdich")
                        if (afterPrefix.isEmpty()) {
                            "Vui lòng nhập đúng cú pháp: jjdich{ngôn ngữ đích} <câu cần dịch> \nVí dụ: jjdichen tôi là ai"
                        } else {
                            val langToTrans = afterPrefix.take(2)
                            val text = afterPrefix.drop(2).trim()
                            if (text.isEmpty()) {
                                "Vui lòng nhập câu cần dịch sau ngôn ngữ đích, ví dụ: jjdich$langToTrans tôi là ai"
                            } else {
                                Log.d(TAG, "Text to translate: $text")
                                apiService?.chatGeminiApi("Hãy dịch đoạn text sau sang ngôn ngữ có mã hiệu $langToTrans: $text .Lưu ý câu trả lời là đoạn text đã được dịch, không cần giải thích gì thêm")
                                    ?: "Không thể dịch lúc này"
                            }
                        }
                    }
                    !isGameActive && lowerCommand.startsWith("jjnguphap") -> {
                        val afterPrefix = lowerCommand.substringAfter("jjnguphap")
                        if (afterPrefix.isEmpty()) {
                            "Vui lòng nhập đúng cú pháp: jjnguphap <câu cần check ngữ pháp> \nVí dụ: jjnguphap tôi là ai"
                        } else {
                            val text = afterPrefix.trim()
                            Log.d(TAG, "Grammarly: $text")
                            if (text.isEmpty()) {
                                "Vui lòng nhập câu cần check ngữ pháp, ví dụ: jjnguphap tôi là ai"
                            } else {
                                Log.d(TAG, "Text to grammarly: $text")
                                apiService?.chatGeminiApi("Hãy check ngữ pháp của đoạn text sau : $text .Lưu ý trả lời là giải thích ngữ pháp đúng, và đoạn ngữ pháp đã được sửa, ngắn gọn xúc tích và trả lời bằng tiếng việt ")
                                    ?: "Không thể kiểm tra ngữ pháp lúc này"
                            }
                        }
                    }
                    !isGameActive && lowerCommand.startsWith("jjtratu") -> {
                        val afterPrefix = lowerCommand.substringAfter("jjtratu").trim()
                        if (afterPrefix.isEmpty()) {
                            "Vui lòng nhập đúng cú pháp: jjtratu<ngôn ngữ> <từ cần tra>\nVí dụ: jjtratuen hello"
                        } else {
                            val langToSearch = afterPrefix.take(2)
                            val text = afterPrefix.drop(2).trim()
                            if (text.isEmpty()) {
                                "Vui lòng nhập từ cần tra, ví dụ: jjtratu$langToSearch hello"
                            } else {
                                Log.d(TAG, "Text to search: $text")
                                val prompt = """Translate the following word '$text' into the language with the code '$langToSearch', then look up the translated word.
                Use this JSON schema:
                WordEntry = {
                    'pronunciation': str,
                    'part_of_speech': str,
                    'translation': str,
                    'usage': str
                }
                Return: list[WordEntry]
                Return exactly one entry as a valid JSON string."""
                                val jsonResult = apiService?.chatGeminiJson(prompt)
                                if (jsonResult != null) {
                                    try {
                                        val jsonArray = JSONArray(jsonResult)
                                        val jsonObject = jsonArray.getJSONObject(0)
                                        val translation = jsonObject.getString("translation")
                                        val partOfSpeech = jsonObject.getString("part_of_speech")
                                        val pronunciation = jsonObject.getString("pronunciation")
                                        val usage = jsonObject.getString("usage")

                                        val translationMessage = "Translation: $translation [SPEAKER:$translation:$langToSearch]"
                                        sendBroadcast(Intent("COMMAND_RESULT").apply {
                                            putExtra("command_result", translationMessage)
                                            setPackage("com.example.txe")
                                        })

                                        val detailsMessage = """
                                        Part of Speech: $partOfSpeech
                                        Pronunciation: $pronunciation
                                        Usage: $usage
                                    """.trimIndent()
                                        sendBroadcast(Intent("COMMAND_RESULT").apply {
                                            putExtra("command_result", detailsMessage)
                                            setPackage("com.example.txe")
                                        })

                                        null
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error parsing JSON", e)
                                        "Lỗi khi phân tích kết quả: ${e.message}"
                                    }
                                } else {
                                    "Không thể tra từ lúc này"
                                }
                            }
                        }
                    }
                    !isGameActive && lowerCommand.startsWith("jjphatnguoi") -> {
                        val parts = lowerCommand.split(" ")
                        if (parts.size > 1) {
                            val plateNumber = parts[1].trim()
                            apiService?.getTrafficViolations(plateNumber) ?: "Lỗi khởi tạo service"
                        } else {
                            "Vui lòng nhập biển số xe. Ví dụ: jjphatnguoi 30a-12345"
                        }
                    }
                    lowerCommand.startsWith("jjnoitu") -> {
                        if (lowerCommand == "jjnoitu end") {
                            isGameActive = false
                            "Trò chơi nối từ đã kết thúc!"
                        } else {
                            handleWordChainGame(lowerCommand)
                        }
                    }
                    isGameActive -> {
                        handleWordChainResponse(lowerCommand)
                    }
                    else -> "Lệnh không hợp lệ"
                }
                Log.d(TAG, "Command result: $result")
                if (result != null) {
                    sendBroadcast(Intent("COMMAND_RESULT").apply {
                        putExtra("command_result", result)
                        setPackage("com.example.txe")
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                sendBroadcast(Intent("COMMAND_RESULT").apply {
                    putExtra("command_result", "Lỗi xử lý lệnh: ${e.message}")
                    setPackage("com.example.txe")
                })
            }
        }
    }

    private fun loadWordList() {
        try {
            val assetFiles = assets.list("")
            Log.d(TAG, "Files in assets: ${assetFiles?.joinToString() ?: "Empty"}")
            if (assetFiles?.contains("word.json") == true) {
                val jsonString = assets.open("word.json").bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                val wordList = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val text = item.getString("text")
                    wordList.add(text)
                }
                validWords = wordList
                Log.d(TAG, "Loaded ${validWords.size} words from word.json")
            } else {
                Log.e(TAG, "word.json not found in assets!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading word list", e)
        }
    }

    private suspend fun handleWordChainGame(command: String): String {
        val input = command.removePrefix("jjnoitu").trim()
        if (input.isEmpty()) {
            return "Vui lòng nhập từ đầu tiên, ví dụ: jjnoitu con chó"
        }
        val words = input.split(" ").filter { it.isNotBlank() }
        if (words.size != 2) {
            return "Vui lòng nhập đúng 2 từ!"
        }
        if (!validWords.any { it.toLowerCase() == input }) {
            Log.d(TAG, "Invalid word: $input not in validWords")
            endGame()
            return "Từ '$input' không hợp lệ, bạn thua!"
        }
        isGameActive = true
        lastWord = words.last()
        val response = startGameTurn("${words[0]} ${words[1]}")
        return "Game nối từ:\nTừ của bạn: $input. \nHệ thống: $response"
    }

    private suspend fun handleWordChainResponse(command: String): String {
        val input = command.trim()
        val words = input.split(" ").filter { it.isNotBlank() }
        Log.d(TAG, "Input: $input, LastWord: $lastWord")
        if (words.size != 2) {
            return "Vui lòng nhập đúng 2 từ"
        }
        if (!validWords.any { it.toLowerCase() == input }) {
            Log.d(TAG, "Invalid word: $input not in validWords")
            endGame()
            return "Từ '$input' không hợp lệ, bạn thua!"
        }
        if (words.first().toLowerCase() != lastWord?.toLowerCase()) {
            Log.d(TAG, "First word '${words[0]}' does not match lastWord '$lastWord'")
            endGame()
            return "Từ đầu '${words[0]}' không khớp với '$lastWord', bạn thua!"
        }
        Log.d(TAG, "Valid input: $input, proceeding with game")
        gameJob?.cancel()
        lastWord = words.last()
        val response = startGameTurn(input)
        return "Từ của bạn: $input. Hệ thống: $response"
    }

    private fun findWord(wordToMap: String): String? {
        val matchingWords = validWords.filter { word ->
            val firstWord = word.split(" ")[0].toLowerCase()
            firstWord == wordToMap.split(" ")[1].toLowerCase()
        }
        if (matchingWords.isEmpty()) {
            Log.d(TAG, "No matching words found for: $wordToMap")
            return null
        }
        val randomWord = matchingWords[Random.nextInt(matchingWords.size)]
        Log.d(TAG, "Matching words: $matchingWords, Selected: $randomWord")
        return randomWord
    }

    private suspend fun startGameTurn(userWord: String): String {
        val response = findWord(userWord)
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
            delay(60_000)
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