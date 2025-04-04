package com.example.txe

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat

class TextMonitorService : NotificationListenerService() {
    private val TAG = "TextMonitorService"
    private lateinit var expanderManager: TextExpanderManager
    private var currentText = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        expanderManager = TextExpanderManager(this)
        createNotificationChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val text = sbn.notification.extras.getString("android.text") ?: return
            
            // Append new text to current buffer
            currentText.append(text)
            
            // Check if current text matches any shortcut
            val expanders = expanderManager.getExpanders()
            for (expander in expanders) {
                if (currentText.toString().endsWith(expander.key)) {
                    // Found a match, copy the value to clipboard
                    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("TextExpander", expander.value)
                    clipboardManager.setPrimaryClip(clipData)
                    
                    // Show notification
                    showNotification(expander.key, expander.value)
                    
                    // Clear the buffer
                    currentText.clear()
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Text Expander",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for text expansion"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(key: String, value: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Text Expander")
            .setContentText("Đã copy: $value")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "text_expander_channel"
    }
} 