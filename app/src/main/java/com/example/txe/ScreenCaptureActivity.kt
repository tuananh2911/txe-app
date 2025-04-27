package com.example.txe

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class ScreenCaptureActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ScreenCaptureActivity"
        private const val REQUEST_CODE = 100

        fun start(context: Context) {
            val intent = Intent(context, ScreenCaptureActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private val projectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Log.d(TAG, "Screen capture permission granted")
            val intent = Intent("SCREEN_CAPTURE_PERMISSION")
            intent.putExtra("resultCode", result.resultCode)
            intent.putExtra("data", result.data)
            sendBroadcast(intent)
        } else {
            Log.e(TAG, "Screen capture permission denied")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Starting screen capture permission request")
        val captureIntent = projectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }
}