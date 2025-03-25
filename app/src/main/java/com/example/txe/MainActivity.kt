package com.example.txe

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Đã cấp quyền hiển thị trên màn hình", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Cần quyền hiển thị trên màn hình để hoạt động", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Button for accessibility permission
        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Vui lòng bật Text Expander trong Cài đặt > Trợ năng", Toast.LENGTH_LONG).show()
                openAccessibilitySettings()
            } else {
                Toast.makeText(this, "Đã cấp quyền trợ năng", Toast.LENGTH_SHORT).show()
            }
        }

        // Button for overlay permission
        findViewById<Button>(R.id.overlayButton).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Đã cấp quyền hiển thị trên màn hình", Toast.LENGTH_SHORT).show()
            } else {
                requestOverlayPermission()
            }
        }

        // Button to start service
        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Vui lòng cấp quyền trợ năng trước", Toast.LENGTH_LONG).show()
            } else if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Vui lòng cấp quyền hiển thị trên màn hình trước", Toast.LENGTH_LONG).show()
            } else {
                try {
                    val serviceIntent = Intent(this, FloatingWindowService::class.java)
                    startService(serviceIntent)
                    Toast.makeText(this, "Đã bật Text Expander", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Lỗi khởi động service: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED
        )
        if (accessibilityEnabled == 1) {
            val serviceString = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            serviceString?.let {
                return it.contains("${packageName}/${MyAccessibilityService::class.java.name}")
            }
        }
        return false
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }
}