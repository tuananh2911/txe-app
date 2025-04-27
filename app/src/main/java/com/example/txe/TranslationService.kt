package com.example.txe

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TranslationService(private val context: Context) {
    private val TAG = "TranslationService"

    suspend fun translateImage(imageFile: File): String? = withContext(Dispatchers.IO) {
        try {
            // Khởi tạo mô hình Gemini
            val generativeModel = GenerativeModel(
                modelName = "gemini-2.0-flash", // Mô hình từ code Python
                apiKey = BuildConfig.API_KEY
            )

            // Đọc dữ liệu hình ảnh từ file và chuyển thành Bitmap
            val imageBytes = imageFile.readBytes()
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: throw IllegalStateException("Không thể decode hình ảnh thành Bitmap")

            // Tạo nội dung với hình ảnh và prompt
            val content = content {
                image(bitmap) // Chỉ truyền Bitmap, không cần MIME type
                text("""
                    Trích xuất văn bản từ hình ảnh này và dịch sang tiếng Việt.
                    Chỉ trả về văn bản đã dịch, không thêm giải thích.
                    Nếu không tìm thấy văn bản, trả về: "Không tìm thấy văn bản trong ảnh".
                """.trimIndent())
            }

            Log.d(TAG, "Gửi yêu cầu dịch hình ảnh tới Gemini: ${imageFile.absolutePath}")

            // Gửi yêu cầu tới Gemini
            val response = generativeModel.generateContent(content)
            val translatedText = response.text

            if (translatedText.isNullOrBlank()) {
                Log.e(TAG, "Không nhận được kết quả dịch từ Gemini")
                return@withContext "Lỗi dịch văn bản: Không nhận được kết quả"
            }

            Log.d(TAG, "Văn bản đã dịch: $translatedText")
            translatedText
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi dịch hình ảnh", e)
            "Lỗi dịch ảnh: ${e.message}"
        }
    }
}