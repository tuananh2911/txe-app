package com.example.txe

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExpanderManager(private val context: Context) {
    private val TAG = "ExpanderManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("TxEPrefs", Context.MODE_PRIVATE)
    private val dictionaryManager: DictionaryManager = DictionaryManager(context)

    suspend fun addExpander(shortcut: String, value: String) = withContext(Dispatchers.IO) {
        try {
            dictionaryManager.addWord(shortcut, value)
            Log.d(TAG, "Added expander: $shortcut -> $value")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error adding expander", e)
            throw e
        }
    }

    suspend fun deleteExpander(shortcut: String) = withContext(Dispatchers.IO) {
        try {
            dictionaryManager.removeWord(shortcut)
            Log.d(TAG, "Deleted expander: $shortcut")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting expander", e)
            throw e
        }
    }

    suspend fun setKeyboardEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        try {
            val keyboardComponent = "${context.packageName}/.TxEKeyboardService"
            if (enabled) {
                // Enable keyboard
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } else {
                // Disable keyboard
                val enabledKeyboards = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_INPUT_METHODS
                )
                if (enabledKeyboards?.contains(keyboardComponent) == true) {
                    val newEnabledKeyboards = enabledKeyboards.replace(keyboardComponent, "")
                    Settings.Secure.putString(
                        context.contentResolver,
                        Settings.Secure.ENABLED_INPUT_METHODS,
                        newEnabledKeyboards
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting keyboard state", e)
            throw e
        }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        try {
            if (enabled) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting overlay state", e)
            throw e
        }
    }

    suspend fun isKeyboardEnabled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val enabledKeyboards = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS
            )
            val keyboardComponent = "${context.packageName}/.TxEKeyboardService"
            enabledKeyboards?.contains(keyboardComponent) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking keyboard state", e)
            false
        }
    }

    suspend fun isOverlayEnabled(): Boolean = withContext(Dispatchers.IO) {
        try {
            Settings.canDrawOverlays(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking overlay state", e)
            false
        }
    }

    suspend fun getAllExpanders(): List<Expander> = withContext(Dispatchers.IO) {
        try {
            dictionaryManager.getAllWords().map { (shortcut, value) ->
                Expander(shortcut, value)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all expanders: ${e.message}")
            throw e
        }
    }
} 