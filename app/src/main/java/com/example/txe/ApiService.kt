package com.example.txe

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.api.client.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import org.json.JSONArray
class ApiService(private val context: Context) {
    private val TAG = "ApiService"
    private val prefs: SharedPreferences = context.getSharedPreferences("TextExpander", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val queue: RequestQueue by lazy { Volley.newRequestQueue(context) }

    companion object {
        private const val OPENROUTER_API_KEY = "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" // Thay bằng API key của bạn
        private const val SITE_URL = "https://openrouter.ai/api/v1"                // Thay bằng URL của bạn
        private const val SITE_NAME = "<YOUR_SITE_NAME>"             // Thay bằng tên site của bạn
    }

    suspend fun getNextWordFromGemini(word: String): String? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val url = "https://openrouter.ai/api/v1/chat/completions"
                val requestBody = JSONObject().apply {
                    put("model", "google/gemini-pro")
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Bạn hãy chơi nối từ với tôi, từ của tôi là  '$word'. Hãy đưa ra từ tiếp theo để nối nó phải có nghĩa theo từ điển nhé, chỉ trả về từ cần nối, không cần nhắc lại từ của tôi, không giải thích, không có ký tự đặc biệt")
                    }))
                }

                Log.d(TAG, "Requesting next word from Gemini: $url with body $requestBody")

                val request = object : JsonObjectRequest(
                    Method.POST, url, requestBody,
                    { response ->
                        val result = try {
                            val choices = response.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val message = choices.getJSONObject(0).getJSONObject("message")
                                message.getString("content").trim()
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing Gemini response", e)
                            null
                        }
                        continuation.resume(result)
                    },
                    { error ->
                        Log.e(TAG, "Volley error getting Gemini response", error)
                        continuation.resume(null)
                    }
                ) {
                    override fun getHeaders(): MutableMap<String, String> {
                        return mutableMapOf(
                            "Authorization" to "Bearer $OPENROUTER_API_KEY",
                            "HTTP-Referer" to SITE_URL,
                            "X-Title" to SITE_NAME,
                            "Content-Type" to "application/json"
                        )
                    }
                }
                queue.add(request)
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating Gemini request", e)
                continuation.resume(null)
            }
        }
    }

    suspend fun chatGeminiApi(prompt: String): String? {
        val generativeModel = GenerativeModel(
            // The Gemini 1.5 models are versatile and work with both text-only and multimodal prompts
            modelName = "gemini-2.0-flash",
            // Access your API key as a Build Configuration variable (see "Set up your API key" above)
            apiKey = BuildConfig.API_KEY
        )
        val response = generativeModel.generateContent(prompt)
        return response.text
    }

    suspend fun chatGeminiJson(prompt: String): String? {
        val generationConfig = GenerationConfig.builder()
        generationConfig.responseMimeType = "application/json"
        val generativeModel = GenerativeModel(
            generationConfig = generationConfig.build(),
            // The Gemini 1.5 models are versatile and work with both text-only and multimodal prompts
            modelName = "gemini-2.0-flash",
            // Access your API key as a Build Configuration variable (see "Set up your API key" above)
            apiKey = BuildConfig.API_KEY
        )
        val response = generativeModel.generateContent(prompt)
        return response.text
    }
    private suspend fun getUserLocationFromIP(): Pair<String, String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val url = "http://ip-api.com/json/?fields=status,country,city,lat,lon&lang=vi"
                Log.d(TAG, "Requesting location from IP: $url")

                val request = JsonObjectRequest(Request.Method.GET, url, null,
                    { response ->
                        val result = try {
                            if (response.getString("status") == "success") {
                                val city = response.getString("city")
                                val country = response.getString("country")
                                Pair(city, country)
                            } else {
                                Pair("Hanoi", "Vietnam")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing IP location response", e)
                            Pair("Hanoi", "Vietnam")
                        }
                        continuation.resume(result)
                    },
                    { error ->
                        Log.e(TAG, "Volley error getting IP location", error)
                        continuation.resume(Pair("Hanoi", "Vietnam"))
                    }
                )
                queue.add(request)
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating IP location request", e)
                continuation.resume(Pair("Hanoi", "Vietnam"))
            }
        }
    }

    // Lấy thông tin thời tiết từ OpenWeatherMap
    suspend fun getWeatherInfo(): String = withContext(Dispatchers.IO) {
        try {
            val (city, country) = getUserLocationFromIP() // Chờ kết quả trước
            suspendCancellableCoroutine { continuation ->
                try {
                    val apiKey = "84e7fd705395cc97125488f5e56b1122"
                    val url = "https://api.openweathermap.org/data/2.5/weather?q=${encodeUrl(city)}&units=metric&lang=vi&appid=$apiKey"
                    Log.d(TAG, "Requesting weather data: $url")

                    val request = JsonObjectRequest(Request.Method.GET, url, null,
                        { response ->
                            val result = try {
                                val main = response.getJSONObject("main")
                                val weather = response.getJSONArray("weather").getJSONObject(0)
                                val wind = response.getJSONObject("wind")
                                val sys = response.getJSONObject("sys")

                                val temp = main.getDouble("temp")
                                val tempFeelsLike = main.getDouble("feels_like")
                                val tempMin = main.getDouble("temp_min")
                                val tempMax = main.getDouble("temp_max")
                                val humidity = main.getInt("humidity")
                                val pressure = main.getInt("pressure")
                                val weatherDesc = weather.getString("description")
                                val windSpeed = wind.getDouble("speed")
                                val sunrise = formatTime(sys.getLong("sunrise"))
                                val sunset = formatTime(sys.getLong("sunset"))

                                """
                            Thông tin thời tiết tại $city, $country
                            
                            Nhiệt độ hiện tại: ${"%.1f".format(temp)}°C
                            Cảm giác như: ${"%.1f".format(tempFeelsLike)}°C
                            Nhiệt độ thấp nhất: ${"%.1f".format(tempMin)}°C
                            Nhiệt độ cao nhất: ${"%.1f".format(tempMax)}°C
                            Thời tiết: $weatherDesc
                            Độ ẩm: $humidity%
                            Áp suất: $pressure hPa
                            Tốc độ gió: ${"%.1f".format(windSpeed)} m/s
                            Mặt trời mọc: $sunrise
                            Mặt trời lặn: $sunset
                            """.trimIndent()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing weather response", e)
                                "Lỗi phân tích dữ liệu thời tiết: ${e.message}"
                            }
                            continuation.resume(result)
                        },
                        { error ->
                            Log.e(TAG, "Volley error getting weather", error)
                            continuation.resume("Lỗi khi lấy thông tin thời tiết: ${error.message}")
                        }
                    )
                    queue.add(request)
                } catch (e: Exception) {
                    Log.e(TAG, "Error initiating weather request", e)
                    continuation.resume("Lỗi khi lấy thông tin thời tiết: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getWeatherInfo", e)
            "Lỗi khi lấy thông tin thời tiết: ${e.message}"
        }
    }

    // Lấy tỷ giá từ Vietcombank
    suspend fun getExchangeRate(currencyCode: String): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val url = "https://www.vietcombank.com.vn/api/exchangerates?date=$currentDate"
                Log.d(TAG, "Requesting exchange rate: $url")

                val request = JsonObjectRequest(Request.Method.GET, url, null,
                    { response ->
                        Log.d(TAG, "Exchange rate JSON: $response")
                        val result = try {
                            val data = response.getJSONArray("Data")
                            var result = "Tỷ giá Vietcombank ngày $currentDate\n\n"

                            if (currencyCode.isNotEmpty()) {
                                val upperCode = currencyCode.uppercase()
                                var found = false
                                for (i in 0 until data.length()) {
                                    val currency = data.getJSONObject(i)
                                    if (currency.getString("currencyCode") == upperCode) {
                                        found = true
                                        result += """
                                        Loại tiền tệ: ${currency.getString("currencyName")} - ${currency.getString("currencyCode")}
                                        Mua: ${currency.getString("transfer")}
                                        Mua chuyển khoản: ${currency.getString("transfer")}
                                        Bán: ${currency.getString("sell")}
                                        """.trimIndent()
                                        break
                                    }
                                }
                                if (!found) result += "Không tìm thấy thông tin cho mã tiền tệ: $upperCode"
                            } else {
                                for (i in 0 until data.length()) {
                                    val currency = data.getJSONObject(i)
                                    result += """
                                    Mã tiền tệ: ${currency.getString("currencyCode")}
                                    Mua: ${currency.getString("transfer")}
                                    Mua chuyển khoản: ${currency.getString("transfer")}
                                    Bán: ${currency.getString("sell")}
                                    ------------------------
                                    """.trimIndent()
                                }
                            }
                            result
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing exchange rate response", e)
                            "Lỗi phân tích dữ liệu tỷ giá: ${e.message}"
                        }
                        Log.d(TAG, "Exchange rate result: $result")
                        continuation.resume(result)
                    },
                    { error ->
                        Log.e(TAG, "Volley error getting exchange rate", error)
                        continuation.resume("Lỗi khi lấy tỷ giá: ${error.message}")
                    }
                )
                queue.add(request)
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating exchange rate request", e)
                continuation.resume("Lỗi khi lấy tỷ giá: ${e.message}")
            }
        }
    }

    // Lấy ngày âm lịch từ API
    suspend fun getLunarDate(): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val currentTime = Calendar.getInstance()
                val day = currentTime.get(Calendar.DAY_OF_MONTH)
                val month = currentTime.get(Calendar.MONTH) + 1
                val year = currentTime.get(Calendar.YEAR)

                val requestBody = JSONObject().apply {
                    put("day", day)
                    put("month", month)
                    put("year", year)
                }
                val url = "https://open.oapi.vn/date/convert-to-lunar"
                Log.d(TAG, "Requesting lunar date: $url with body $requestBody")

                val request = JsonObjectRequest(Request.Method.POST, url, requestBody,
                    { response ->
                        val result = try {
                            if (response.getString("code") == "success") {
                                val data = response.getJSONObject("data")
                                val lunarDay = data.getInt("day")
                                val lunarMonth = data.getInt("month")
                                val lunarYear = data.getInt("year")
                                val heavenlyStem = data.getString("heavenlyStem")
                                val earthlyBranch = data.getString("earthlyBranch")
                                "Ngày $lunarDay tháng $lunarMonth năm $lunarYear ($heavenlyStem $earthlyBranch)"
                            } else {
                                "Không thể lấy thông tin âm lịch: ${response.getString("code")}"
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing lunar date response", e)
                            "Lỗi phân tích dữ liệu âm lịch: ${e.message}"
                        }
                        continuation.resume(result)
                    },
                    { error ->
                        Log.e(TAG, "Volley error getting lunar date", error)
                        continuation.resume("Lỗi khi lấy ngày âm lịch: ${error.message}")
                    }
                )
                queue.add(request)
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating lunar date request", e)
                continuation.resume("Lỗi khi lấy ngày âm lịch: ${e.message}")
            }
        }
    }

    // Lấy thông tin vi phạm giao thông
    suspend fun getTrafficViolations(plateNumber: String): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val requestBody = JSONObject().apply {
                    put("bienso", plateNumber)
                }
                val url = "https://api.checkphatnguoi.vn/phatnguoi"
                Log.d(TAG, "Requesting traffic violations: $url with body $requestBody")

                val request = JsonObjectRequest(Request.Method.POST, url, requestBody,
                    { response ->
                        val result = try {
                            val status = response.getInt("status")
                            var result = "Kết quả tra cứu biển số: $plateNumber\n\n"
                            when (status) {
                                1 -> {
                                    val data = response.getJSONArray("data")
                                    val dataInfo = response.getJSONObject("data_info")
                                    result += "Tổng số vi phạm: ${dataInfo.getInt("total")}\n"
                                    result += "Chưa xử phạt: ${dataInfo.getInt("chuaxuphat")}\n"
                                    result += "Đã xử phạt: ${dataInfo.getInt("daxuphat")}\n"
                                    result += "Cập nhật lần cuối: ${dataInfo.getString("latest")}\n\n"

                                    for (i in 0 until data.length()) {
                                        val v = data.getJSONObject(i)
                                        result += "Vi phạm ${i + 1}:\n--------------------------------\n"
                                        result += "Thời gian: ${v.optString("Thời gian vi phạm", "N/A")}\n"
                                        result += "Địa điểm: ${v.optString("Địa điểm vi phạm", "N/A")}\n"
                                        result += "Lỗi vi phạm: ${v.optString("Hành vi vi phạm", "N/A")}\n"
                                        result += "Trạng thái: ${v.optString("Trạng thái", "N/A")}\n"
                                        result += "Đơn vị phát hiện: ${v.optString("Đơn vị phát hiện vi phạm", "N/A")}\n"
                                        val resolvePlaces = v.optJSONArray("Nơi giải quyết vụ việc")
                                        if (resolvePlaces != null) {
                                            result += "Nơi giải quyết:\n"
                                            for (j in 0 until resolvePlaces.length()) {
                                                result += "  ${resolvePlaces.getString(j)}\n"
                                            }
                                        }
                                        result += "\n"
                                    }
                                }
                                2 -> result += "Uy tín. Phương tiện không có lỗi vi phạm"
                                else -> result += "Uy tín. Phương tiện không có lỗi vi phạm"
                            }
                            result
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing traffic violations response", e)
                            "Lỗi phân tích dữ liệu vi phạm: ${e.message}"
                        }
                        continuation.resume(result)
                    },
                    { error ->
                        Log.e(TAG, "Volley error getting traffic violations", error)
                        continuation.resume("Lỗi khi lấy thông tin vi phạm: ${error.message}")
                    }
                )
                queue.add(request)
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating traffic violations request", e)
                continuation.resume("Lỗi khi lấy thông tin vi phạm: ${e.message}")
            }
        }
    }

    // Hàm hỗ trợ lấy vị trí từ IP


    // Hàm hỗ trợ mã hóa URL
    private fun encodeUrl(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8")
    }

    // Hàm chuyển timestamp sang giờ
    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp * 1000))
    }
}