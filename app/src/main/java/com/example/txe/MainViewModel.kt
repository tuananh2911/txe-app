package com.example.txe

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class MainUiState {
    data class Success(
        val account: GoogleSignInAccount? = null,
        val message: String? = null,
        val expanders: List<Expander> = emptyList(),
        val isKeyboardEnabled: Boolean = false,
        val isOverlayEnabled: Boolean = false,
        val isFloatingWindowEnabled: Boolean = false
    ) : MainUiState()
    data class Error(val message: String) : MainUiState()
    object Loading : MainUiState()
    object SignInRequired : MainUiState()
    data class PermissionRequired(val intent: Intent) : MainUiState() // Thêm Intent để yêu cầu quyền
    data class OpenChatWindow(val intent: Intent) : MainUiState()
    data class NeedsKeyboardEnabled(val intent: Intent) : MainUiState()
    object SelectSheetRequired : MainUiState()
    object Initial : MainUiState()
    data class SheetsLoaded(val sheets: List<SheetInfo>) : MainUiState()
}

data class SheetInfo(val id: String, val name: String)
data class Expander(val shortcut: String, val value: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val dictionaryManager: DictionaryManager = DictionaryManager(application)
    private val sheetsManager: GoogleSheetsManager = GoogleSheetsManager.getInstance(application)

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Initial)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _expanders = MutableStateFlow<List<Expander>>(emptyList())
    val expanders: StateFlow<List<Expander>> = _expanders.asStateFlow()

    private val _shortcutInput = MutableStateFlow("")
    val shortcutInput: StateFlow<String> = _shortcutInput.asStateFlow()

    private val _valueInput = MutableStateFlow("")
    val valueInput: StateFlow<String> = _valueInput.asStateFlow()

    private val _isFloatingWindowEnabled = MutableStateFlow(false)
    val isFloatingWindowEnabled: StateFlow<Boolean> = _isFloatingWindowEnabled.asStateFlow()

    private val expanderManager = ExpanderManager(application)
    private var sheetsService: Sheets? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filteredExpanders = MutableStateFlow<List<Expander>>(emptyList())
    val filteredExpanders: StateFlow<List<Expander>> = _filteredExpanders.asStateFlow()

    init {
        loadExpanders()
        checkKeyboardEnabled()
    }

    fun onShortcutInputChange(value: String) {
        _shortcutInput.value = value
    }

    fun onValueInputChange(value: String) {
        _valueInput.value = value
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        filterExpanders()
    }

    private fun filterExpanders() {
        val query = _searchQuery.value.trim().lowercase()
        if (query.isEmpty()) {
            _filteredExpanders.value = _expanders.value
        } else {
            _filteredExpanders.value = _expanders.value.filter { expander ->
                expander.shortcut.lowercase().contains(query) || expander.value.lowercase().contains(query)
            }
        }
    }

    fun addExpander() {
        val shortcut = _shortcutInput.value.trim()
        val value = _valueInput.value.trim()

        if (shortcut.isNotEmpty() && value.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    dictionaryManager.addWord(shortcut, value)
                    _shortcutInput.value = ""
                    _valueInput.value = ""
                    val expanders = dictionaryManager.getAllWords()
                    _expanders.value = expanders
                    filterExpanders()
                    val currentState = _uiState.value as? MainUiState.Success
                    _uiState.value = MainUiState.Success(
                        account = currentState?.account,
                        message = "Đã thêm shortcut thành công",
                        expanders = expanders,
                        isKeyboardEnabled = currentState?.isKeyboardEnabled ?: false,
                        isOverlayEnabled = currentState?.isOverlayEnabled ?: false,
                        isFloatingWindowEnabled = currentState?.isFloatingWindowEnabled ?: false
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding expander", e)
                    _uiState.value = MainUiState.Error("Không thể thêm shortcut: ${e.message}")
                }
            }
        } else {
            _uiState.value = MainUiState.Error("Vui lòng nhập đầy đủ thông tin")
        }
    }

    fun deleteExpander(expander: Expander) {
        viewModelScope.launch {
            try {
                dictionaryManager.removeWord(expander.shortcut)
                val updatedExpanders = dictionaryManager.getAllWords()
                _expanders.value = updatedExpanders
                filterExpanders()
                val currentState = _uiState.value as? MainUiState.Success
                _uiState.value = MainUiState.Success(
                    account = currentState?.account,
                    message = "Đã xóa shortcut thành công",
                    expanders = updatedExpanders,
                    isKeyboardEnabled = currentState?.isKeyboardEnabled ?: false,
                    isOverlayEnabled = currentState?.isOverlayEnabled ?: false,
                    isFloatingWindowEnabled = currentState?.isFloatingWindowEnabled ?: false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting expander", e)
                _uiState.value = MainUiState.Error("Không thể xóa shortcut: ${e.message}")
            }
        }
    }

    fun loadExpanders() {
        viewModelScope.launch {
            try {
                _uiState.value = MainUiState.Loading
                val expanders = dictionaryManager.getAllWords()
                _expanders.value = expanders
                filterExpanders()
                _uiState.value = MainUiState.Success(
                    message = "Loaded ${expanders.size} expanders",
                    expanders = expanders,
                    isFloatingWindowEnabled = (_uiState.value as? MainUiState.Success)?.isFloatingWindowEnabled ?: false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading expanders", e)
                _uiState.value = MainUiState.Error("Failed to load expanders: ${e.message}")
            }
        }
    }

    fun syncFromGoogleSheets(activity: Activity) {
        viewModelScope.launch {
            try {
                val account = GoogleSignIn.getLastSignedInAccount(getApplication())
                if (account == null) {
                    _uiState.value = MainUiState.SignInRequired
                    return@launch
                }

                if (sheetsService == null) {
                    setupSheetsService(account)
                }

                _uiState.value = MainUiState.Loading
                val sheets = loadSheets(activity)
                if (sheets.isNotEmpty()) {
                    _uiState.value = MainUiState.SheetsLoaded(sheets)
                } else {
                    _uiState.value = MainUiState.SelectSheetRequired
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing from Google Sheets", e)
                _uiState.value = MainUiState.Error(e.message ?: "Failed to sync with Google Sheets")
            }
        }
    }

    fun getSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_READONLY), Scope(SheetsScopes.SPREADSHEETS_READONLY))
            .build()
        return GoogleSignIn.getClient(getApplication(), gso).signInIntent
    }

    fun signIn() {
        _uiState.value = MainUiState.SignInRequired
    }

    fun handleSignInResult(account: GoogleSignInAccount) {
        try {
            setupSheetsService(account)
            _uiState.value = MainUiState.Success(
                account = account,
                message = "Signed in as ${account.email}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error handling sign in result", e)
            _uiState.value = MainUiState.Error(e.message ?: "Failed to handle sign in result")
        }
    }

    private suspend fun loadSheets(activity: Activity): List<SheetInfo> {
        try {
            Log.d(TAG, "Starting to load sheets...")
            _uiState.value = MainUiState.Loading

            val driveManager = GoogleDriveManager.getInstance(getApplication())
            Log.d(TAG, "Got DriveManager instance")

            val sheets = driveManager.listGoogleSheets(activity)
            Log.d(TAG, "Loaded ${sheets.size} sheets: ${sheets.map { it.name }}")
            return sheets
        } catch (e: UserRecoverableAuthIOException) {
            Log.e(TAG, "Need remote consent to load sheets", e)
            val intent = e.intent // Lấy Intent từ exception để yêu cầu quyền
            _uiState.value = MainUiState.PermissionRequired(intent)
            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sheets", e)
            _uiState.value = MainUiState.Error("Failed to load sheets: ${e.message}")
            return emptyList()
        }
    }

    fun handleSignInError(error: String) {
        Log.e(TAG, "Sign in error: $error")
        _uiState.value = MainUiState.Error(error)
    }

    fun handleSheetSelected(sheetId: String, sheetName: String, activity: Activity) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Handling sheet selection: $sheetName (ID: $sheetId)")
                _uiState.value = MainUiState.Loading

                val account = GoogleSignIn.getLastSignedInAccount(getApplication())
                if (account == null) {
                    Log.e(TAG, "No account signed in")
                    _uiState.value = MainUiState.SignInRequired
                    return@launch
                }

                sheetsManager.setupSheetsService(account)
                sheetsManager.setSelectedSheetId(sheetId)

                val expanders = sheetsManager.syncFromGoogleSheets()
                _expanders.value = expanders
                _uiState.value = MainUiState.Success(
                    message = "Successfully synced ${expanders.size} expanders from $sheetName",
                    expanders = expanders,
                    isKeyboardEnabled = (_uiState.value as? MainUiState.Success)?.isKeyboardEnabled ?: false,
                    isOverlayEnabled = (_uiState.value as? MainUiState.Success)?.isOverlayEnabled ?: false,
                    isFloatingWindowEnabled = (_uiState.value as? MainUiState.Success)?.isFloatingWindowEnabled ?: false
                )
            } catch (e: UserRecoverableAuthIOException) {
                Log.e(TAG, "Need remote consent to sync sheet data", e)
                _uiState.value = MainUiState.PermissionRequired(e.intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling sheet selection", e)
                _uiState.value = MainUiState.Error("Failed to sync data: ${e.message}")
            }
        }
    }

    fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${getApplication<Application>().packageName}")
        )
        _uiState.value = MainUiState.PermissionRequired(intent)
    }

    fun openChatWindow() {
        if (Settings.canDrawOverlays(getApplication())) {
            val intent = Intent(getApplication(), FloatingWindowService::class.java)
            _uiState.value = MainUiState.OpenChatWindow(intent)
        } else {
            _uiState.value = MainUiState.Error("Vui lòng cấp quyền hiển thị trên các ứng dụng khác")
        }
    }

    fun checkKeyboardEnabled() {
        viewModelScope.launch {
            try {
                val isEnabled = expanderManager.isKeyboardEnabled()
                _uiState.value = MainUiState.Success(
                    expanders = (_uiState.value as? MainUiState.Success)?.expanders ?: emptyList(),
                    isKeyboardEnabled = isEnabled,
                    isOverlayEnabled = (_uiState.value as? MainUiState.Success)?.isOverlayEnabled ?: false,
                    isFloatingWindowEnabled = (_uiState.value as? MainUiState.Success)?.isFloatingWindowEnabled ?: false
                )
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error("Failed to check keyboard state: ${e.message}")
            }
        }
    }

    fun addExpander(shortcut: String, value: String) {
        viewModelScope.launch {
            try {
                expanderManager.addExpander(shortcut, value)
                loadExpanders()
            } catch (e: Exception) {
                Log.e(TAG, "Error adding expander", e)
                _uiState.value = MainUiState.Error("Failed to add expander: ${e.message}")
            }
        }
    }

    fun setKeyboardEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                expanderManager.setKeyboardEnabled(enabled)
                _uiState.value = MainUiState.Success(
                    expanders = (_uiState.value as? MainUiState.Success)?.expanders ?: emptyList(),
                    isKeyboardEnabled = enabled,
                    isOverlayEnabled = (_uiState.value as? MainUiState.Success)?.isOverlayEnabled ?: false,
                    isFloatingWindowEnabled = (_uiState.value as? MainUiState.Success)?.isFloatingWindowEnabled ?: false
                )
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error("Failed to set keyboard state: ${e.message}")
            }
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                expanderManager.setOverlayEnabled(enabled)
                _uiState.value = MainUiState.Success(
                    expanders = (_uiState.value as? MainUiState.Success)?.expanders ?: emptyList(),
                    isKeyboardEnabled = (_uiState.value as? MainUiState.Success)?.isKeyboardEnabled ?: false,
                    isOverlayEnabled = enabled,
                    isFloatingWindowEnabled = (_uiState.value as? MainUiState.Success)?.isFloatingWindowEnabled ?: false
                )
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error("Failed to set overlay state: ${e.message}")
            }
        }
    }

    fun clearMessage() {
        _uiState.value = MainUiState.Success(
            account = (_uiState.value as? MainUiState.Success)?.account,
            message = null,
            expanders = (_uiState.value as? MainUiState.Success)?.expanders ?: emptyList(),
            isKeyboardEnabled = (_uiState.value as? MainUiState.Success)?.isKeyboardEnabled ?: false,
            isOverlayEnabled = (_uiState.value as? MainUiState.Success)?.isOverlayEnabled ?: false,
            isFloatingWindowEnabled = (_uiState.value as? MainUiState.Success)?.isFloatingWindowEnabled ?: false
        )
    }

    fun clearError() {
        _uiState.value = MainUiState.Initial
    }

    private fun setupSheetsService(account: GoogleSignInAccount) {
        Log.d(TAG, "Setting up sheets service for account: ${account.email}")
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                getApplication(),
                listOf(DriveScopes.DRIVE_READONLY, SheetsScopes.SPREADSHEETS_READONLY)
            ).apply {
                selectedAccount = account.account
            }

            sheetsService = Sheets.Builder(
                NetHttpTransport(),
                GsonFactory(),
                credential
            )
                .setApplicationName("TxE")
                .build()

            GoogleDriveManager.getInstance(getApplication()).initialize(credential)
            Log.d(TAG, "Sheets service setup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup sheets service", e)
            throw e
        }
    }

    fun onSheetSelected(sheetId: String, sheetName: String, activity: Activity) {
        handleSheetSelected(sheetId, sheetName, activity)
    }

    fun handleSignInRequired() {
        _uiState.value = MainUiState.SignInRequired
    }

    fun toggleFloatingWindow() {
        _isFloatingWindowEnabled.value = !_isFloatingWindowEnabled.value
        if (_isFloatingWindowEnabled.value) {
            val intent = Intent(getApplication(), FloatingWindowService::class.java)
            getApplication<Application>().startService(intent)
        } else {
            val intent = Intent(getApplication(), FloatingWindowService::class.java)
            getApplication<Application>().stopService(intent)
        }

        val currentState = _uiState.value as? MainUiState.Success
        _uiState.value = MainUiState.Success(
            account = currentState?.account,
            message = currentState?.message,
            expanders = currentState?.expanders ?: emptyList(),
            isKeyboardEnabled = currentState?.isKeyboardEnabled ?: false,
            isOverlayEnabled = currentState?.isOverlayEnabled ?: false,
            isFloatingWindowEnabled = _isFloatingWindowEnabled.value
        )
    }
}