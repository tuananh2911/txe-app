package com.example.txe

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast

class ClipboardService : NotificationListenerService() {
    private lateinit var databaseHelper: DatabaseHelper
    private lateinit var clipboardManager: ClipboardManager
    private val TAG = "ClipboardService"

    override fun onCreate() {
        super.onCreate()
        databaseHelper = DatabaseHelper(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val text = extras.getString("android.text")
        
        if (text != null) {
            val value = databaseHelper.getValue(text)
            if (value != null) {
                // Copy the value to clipboard
                val clip = ClipData.newPlainText("TxE", value)
                clipboardManager.setPrimaryClip(clip)
                
                // Show a toast notification
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("show_toast", "Đã copy: $value")
                }
                startActivity(intent)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not needed
    }
} 