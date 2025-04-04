package com.example.txe

import android.app.Application
import android.util.Log

class TxEApplication : Application() {
    companion object {
        private const val TAG = "TxEApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate")
    }
} 