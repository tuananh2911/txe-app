package com.example.txe

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.delay
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
    data class PermissionRequired(val intent: Intent) : MainUiState()
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
        private const val PREFS_NAME = "TxEPrefs"
        private const val KEY_KEYBOARD_ENABLED = "keyboard_enabled"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_FLOATING_WINDOW_ENABLED = "floating_window_enabled"
    }

    private val sharedPrefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dictionaryManager: DictionaryManager = DictionaryManager(application)
    private val sheetsManager: GoogleSheetsManager = GoogleSheetsManager.getInstance(application)
    private val expanderManager = ExpanderManager(application)

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

    private var sheetsService: Sheets? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filteredExpanders = MutableStateFlow<List<Expander>>(emptyList())
    val filteredExpanders: StateFlow<List<Expander>> = _filteredExpanders.asStateFlow()

    init {
        Log.d(TAG, "MainViewModel initialized")
        loadExpanders()
        restoreSystemStates()
    }

    private fun restoreSystemStates() {
        viewModelScope.launch {
            Log.d(TAG, "Restoring system states")
            updateSystemStates()
        }
    }

    private suspend fun updateSystemStates() {
        // Thử tối đa 3 lần nếu trạng thái chưa chính xác
        repeat(3) { attempt ->
            Log.d(TAG, "Checking system states, attempt ${attempt + 1}")
            val isKeyboardEnabled = expanderManager.isKeyboardEnabled()
            val isOverlayEnabled = Settings.canDrawOverlays(getApplication())
            val isFloatingWindowEnabled = checkFloatingWindowState()

            Log.d(
                TAG,
                "System states - Keyboard: $isKeyboardEnabled, Overlay: $isOverlayEnabled, Floating: $isFloatingWindowEnabled"
            )

            // Nếu tất cả trạng thái đều là false, thử lại sau 500ms
            if (!isKeyboardEnabled && !isOverlayEnabled && !isFloatingWindowEnabled && attempt < 2) {
                Log.d(TAG, "All states false, retrying after delay")
                delay(500)
                return@repeat
            }

            saveSystemStates(isKeyboardEnabled, isOverlayEnabled, isFloatingWindowEnabled)

            val currentState = _uiState.value as? MainUiState.Success
            _uiState.value = MainUiState.Success(
                account = currentState?.account,
                expanders = _expanders.value,
                isKeyboardEnabled = isKeyboardEnabled,
                isOverlayEnabled = isOverlayEnabled,
                isFloatingWindowEnabled = isFloatingWindowEnabled,
                message = currentState?.message ?: "System states restored"
            )
            return // Thoát vòng lặp nếu trạng thái hợp lệ
        }
    }

    private fun saveSystemStates(
        isKeyboardEnabled: Boolean,
        isOverlayEnabled: Boolean,
        isFloatingWindowEnabled: Boolean
    ) {
        Log.d(
            TAG,
            "Saving system states - Keyboard: $isKeyboardEnabled, Overlay: $isOverlayEnabled, Floating: $isFloatingWindowEnabled"
        )
        sharedPrefs.edit {
            putBoolean(KEY_KEYBOARD_ENABLED, isKeyboardEnabled)
            putBoolean(KEY_OVERLAY_ENABLED, isOverlayEnabled)
            putBoolean(KEY_FLOATING_WINDOW_ENABLED, isFloatingWindowEnabled)
        }
    }

    private fun checkFloatingWindowState(): Boolean {
        val activityManager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isRunning = activityManager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == FloatingWindowService::class.java.name }
        Log.d(TAG, "FloatingWindowService running: $isRunning")
        return isRunning
    }

    fun onSearchQueryChange(query: String) {
        Log.d(TAG, "Search query changed: $query")
        _searchQuery.value = query
        filterExpanders()
    }

    private fun filterExpanders() {
        val query = _searchQuery.value.trim().lowercase()
        Log.d(TAG, "Filtering expanders with query: '$query', total expanders: ${_expanders.value.size}")
        _filteredExpanders.value = if (query.isEmpty()) {
            _expanders.value
        } else {
            _expanders.value.filter { expander ->
                val matches = expander.shortcut.lowercase().contains(query) ||
                        expander.value.lowercase().contains(query)
                Log.d(TAG, "Expander ${expander.shortcut}: matches=$matches")
                matches
            }
        }
        Log.d(TAG, "Filtered expanders count: ${_filteredExpanders.value.size}")
    }

    fun loadExpanders() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Loading expanders")
                _uiState.value = MainUiState.Loading
                val expanders = dictionaryManager.getAllWords()
                Log.d(TAG, "Loaded ${expanders.size} expanders")
                _expanders.value = expanders
                filterExpanders()
                val currentState = _uiState.value as? MainUiState.Success
                _uiState.value = MainUiState.Success(
                    account = currentState?.account,
                    message = "Loaded ${expanders.size} expanders",
                    expanders = expanders,
                    isKeyboardEnabled = currentState?.isKeyboardEnabled ?: false,
                    isOverlayEnabled = currentState?.isOverlayEnabled ?: false,
                    isFloatingWindowEnabled = currentState?.isFloatingWindowEnabled ?: false
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading expanders", e)
                _uiState.value = MainUiState.Error("Failed to load expanders: ${e.message}")
            }
        }
    }

    fun setKeyboardEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Requesting keyboard enabled: $enabled")
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                _uiState.value = MainUiState.NeedsKeyboardEnabled(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request keyboard settings", e)
                _uiState.value = MainUiState.Error("Failed to open keyboard settings: ${e.message}")
            }
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Requesting overlay enabled: $enabled")
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${getApplication<Application>().packageName}")
                )
                _uiState.value = MainUiState.PermissionRequired(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request overlay settings", e)
                _uiState.value = MainUiState.Error("Failed to open overlay settings: ${e.message}")
            }
        }
    }

    fun toggleFloatingWindow() {
        viewModelScope.launch {
            try {
                val currentEnabled = _isFloatingWindowEnabled.value
                Log.d(TAG, "Toggling floating window: $currentEnabled -> ${!currentEnabled}")
                if (!currentEnabled && !Settings.canDrawOverlays(getApplication())) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${getApplication<Application>().packageName}")
                    )
                    _uiState.value = MainUiState.PermissionRequired(intent)
                    return@launch
                }

                _isFloatingWindowEnabled.value = !currentEnabled
                val intent = Intent(getApplication(), FloatingWindowService::class.java)
                if (_isFloatingWindowEnabled.value) {
                    getApplication<Application>().startService(intent)
                } else {
                    getApplication<Application>().stopService(intent)
                }

                saveSystemStates(
                    (_uiState.value as? MainUiState.Success)?.isKeyboardEnabled ?: false,
                    (_uiState.value as? MainUiState.Success)?.isOverlayEnabled ?: false,
                    _isFloatingWindowEnabled.value
                )

                val currentState = _uiState.value as? MainUiState.Success
                _uiState.value = MainUiState.Success(
                    account = currentState?.account,
                    expanders = _expanders.value,
                    isKeyboardEnabled = currentState?.isKeyboardEnabled ?: false,
                    isOverlayEnabled = currentState?.isOverlayEnabled ?: false,
                    isFloatingWindowEnabled = _isFloatingWindowEnabled.value,
                    message = "Floating window state updated"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling floating window", e)
                _uiState.value = MainUiState.Error("Failed to toggle floating window: ${e.message}")
            }
        }
    }

    fun onPermissionResult() {
        viewModelScope.launch {
            Log.d(TAG, "Handling permission result")
            updateSystemStates()
        }
    }

    fun refreshSystemStates() {
        viewModelScope.launch {
            Log.d(TAG, "Refreshing system states for Settings tab")
            updateSystemStates()
        }
    }

    fun clearMessage() {
        val currentState = _uiState.value as? MainUiState.Success
        _uiState.value = MainUiState.Success(
            account = currentState?.account,
            message = null,
            expanders = currentState?.expanders ?: emptyList(),
            isKeyboardEnabled = currentState?.isKeyboardEnabled ?: false,
            isOverlayEnabled = currentState?.isOverlayEnabled ?: false,
            isFloatingWindowEnabled = currentState?.isFloatingWindowEnabled ?: false
        )
    }

    fun clearError() {
        val currentState = _uiState.value as? MainUiState.Success
        _uiState.value = MainUiState.Success(
            account = currentState?.account,
            message = null,
            expanders = _expanders.value,
            isKeyboardEnabled = currentState?.isKeyboardEnabled ?: false,
            isOverlayEnabled = currentState?.isOverlayEnabled ?: false,
            isFloatingWindowEnabled = currentState?.isFloatingWindowEnabled ?: false
        )
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

    fun onSyncClick(activity: Activity) {
        viewModelScope.launch {
            val account = GoogleSignIn.getLastSignedInAccount(getApplication())
            if (account == null) {
                Log.d(TAG, "No account signed in, requesting sign in")
                _uiState.value = MainUiState.SignInRequired
            } else {
                Log.d(TAG, "Account signed in: ${account.email}, syncing sheets")
                syncFromGoogleSheets(activity)
            }
        }
    }

    fun onShortcutInputChange(value: String) {
        _shortcutInput.value = value
    }

    fun onValueInputChange(value: String) {
        _valueInput.value = value
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
                    loadExpanders()
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
                loadExpanders()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting expander", e)
                _uiState.value = MainUiState.Error("Không thể xóa shortcut: ${e.message}")
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

    fun handleSignInResult(account: GoogleSignInAccount, activity: Activity? = null) {
        try {
            setupSheetsService(account)
            val currentState = _uiState.value as? MainUiState.Success
            _uiState.value = MainUiState.Success(
                account = account,
                message = "Signed in as ${account.email}",
                expanders = _expanders.value,
                isKeyboardEnabled = currentState?.isKeyboardEnabled ?: false,
                isOverlayEnabled = currentState?.isOverlayEnabled ?: false,
                isFloatingWindowEnabled = currentState?.isFloatingWindowEnabled ?: false
            )
            if (activity != null) {
                syncFromGoogleSheets(activity)
            }
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
            val intent = e.intent
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
                filterExpanders()
                val currentState = _uiState.value as? MainUiState.Success
                _uiState.value = MainUiState.Success(
                    account = account,
                    message = "Successfully synced ${expanders.size} expanders from $sheetName",
                    expanders = expanders,
                    isKeyboardEnabled = currentState?.isKeyboardEnabled ?: false,
                    isOverlayEnabled = currentState?.isOverlayEnabled ?: false,
                    isFloatingWindowEnabled = currentState?.isFloatingWindowEnabled ?: false
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
                val currentState = _uiState.value as? MainUiState.Success
                _uiState.value = MainUiState.Success(
                    account = currentState?.account,
                    expanders = currentState?.expanders ?: emptyList(),
                    isKeyboardEnabled = isEnabled,
                    isOverlayEnabled = currentState?.isOverlayEnabled ?: false,
                    isFloatingWindowEnabled = currentState?.isFloatingWindowEnabled ?: false
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
}