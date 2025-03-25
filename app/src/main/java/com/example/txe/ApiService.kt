package com.example.txe

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

class ApiService(private val context: Context) {
    private val TAG = "ApiService"
    private val prefs: SharedPreferences = context.getSharedPreferences("TextExpander", Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun getWeatherInfo(): String {
        return try {
            val currentDate = dateFormat.format(Date())
            "Thời tiết Hà Nội ngày $currentDate:\n" +
            "Nhiệt độ: 25°C\n" +
            "Độ ẩm: 65%\n" +
            "Trời nắng nhẹ"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting weather info", e)
            "Không thể lấy thông tin thời tiết"
        }
    }

    fun getExchangeRate(currencyCode: String): String {
        return try {
            when (currencyCode.uppercase()) {
                "USD" -> "1 USD = 24,500 VND"
                "EUR" -> "1 EUR = 26,800 VND"
                "JPY" -> "100 JPY = 16,500 VND"
                else -> "Không hỗ trợ mã tiền tệ: $currencyCode"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting exchange rate", e)
            "Không thể lấy tỷ giá"
        }
    }

    fun getLunarDate(): String {
        return try {
            val currentDate = dateFormat.format(Date())
            "Ngày dương lịch: $currentDate\n" +
            "Ngày âm lịch: 15/02/2024"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting lunar date", e)
            "Không thể lấy ngày âm lịch"
        }
    }

    fun getTrafficViolations(plateNumber: String): String {
        return try {
            "Thông tin vi phạm giao thông cho xe $plateNumber:\n" +
            "Tổng số vi phạm: 2\n" +
            "1. Vượt đèn đỏ - 15/02/2024 - Đã nộp phạt\n" +
            "2. Đỗ xe sai quy định - 10/02/2024 - Chưa nộp phạt"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting traffic violations", e)
            "Không thể lấy thông tin vi phạm"
        }
    }
} 