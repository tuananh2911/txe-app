package com.example.txe

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.txe.ui.screens.SelectSheetScreen
import com.example.txe.ui.theme.TxETheme

class SelectSheetActivity : ComponentActivity() {
    private lateinit var sheetsManager: GoogleSheetsManager
    private var sheets by mutableStateOf<List<Sheet>>(emptyList())
    private var isLoading by mutableStateOf(true)
    private var error by mutableStateOf<String?>(null)
    private var hasLoaded = false
    companion object {
        private const val TAG = "SelectSheetActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        setContent {
            TxETheme {
                SelectSheetScreen(
                    sheets = sheets,
                    isLoading = isLoading,
                    error = error,
                    onSheetSelected = { sheet ->
                        Log.d(TAG, "Selected sheet: ${sheet.name} (${sheet.id})")
                        val intent = Intent().apply {
                            putExtra("sheet_id", sheet.id)
                            putExtra("sheet_name", sheet.name)
                        }
                        setResult(RESULT_OK, intent)
                        finish()
                    },
                    onDismiss = {
                        Log.d(TAG, "Dismiss selected")
                        finish()  // Hoặc xử lý theo cách bạn muốn khi người dùng nhấn Cancel
                    }
                )
            }
        }

        Log.d(TAG, "Content set, starting to load sheets")
        if (!hasLoaded) {
            loadSheets()
            hasLoaded = true
        }
    }

    private fun loadSheets() {
        Log.d(TAG, "Loading available sheets...")
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            Log.d(TAG, "Found signed in account: ${account.email}")
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    this,
                    listOf(
                        DriveScopes.DRIVE_READONLY,
                        DriveScopes.DRIVE_FILE,
                        DriveScopes.DRIVE_METADATA_READONLY,
                        SheetsScopes.SPREADSHEETS_READONLY
                    )
                ).apply {
                    selectedAccountName = account.email
                }
                Log.d(TAG, "Credential created successfully")

                sheetsManager = GoogleSheetsManager.getInstance(this)
                sheetsManager.initialize(credential)
                Log.d(TAG, "SheetsManager initialized")

                lifecycleScope.launch {
                    try {
                        Log.d(TAG, "Starting to fetch sheets...")
                        val fetchedSheets = sheetsManager.getAvailableSheets()
                        Log.d(TAG, "Found ${fetchedSheets.size} sheets: ${fetchedSheets.map { it.name }}")
                        sheets = fetchedSheets
                        isLoading = false
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading sheets", e)
                        error = "Lỗi khi tải danh sách sheet: ${e.message}"
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up Google credentials", e)
                error = "Lỗi khi thiết lập Google: ${e.message}"
                isLoading = false
            }
        } else {
            Log.e(TAG, "No Google account signed in")
            error = "Vui lòng đăng nhập Google trước"
            isLoading = false
            finish()
        }
    }
} 