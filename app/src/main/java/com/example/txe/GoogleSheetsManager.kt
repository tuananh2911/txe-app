package com.example.txe

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleSheetsManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "GoogleSheetsManager"
        private const val PREFS_NAME = "TxEPrefs"
        private const val KEY_SELECTED_SHEET_ID = "selected_sheet_id"

        @Volatile
        private var instance: GoogleSheetsManager? = null

        fun getInstance(context: Context): GoogleSheetsManager {
            return instance ?: synchronized(this) {
                instance ?: GoogleSheetsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var sheetsService: Sheets? = null
    private var driveService: Drive? = null
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dictionaryManager: DictionaryManager = DictionaryManager(context)
    private lateinit var googleSignInClient: GoogleSignInClient
    private var selectedSheetId: String? = null

    init {
        setupGoogleSignIn()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SheetsScopes.SPREADSHEETS_READONLY))
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    fun initialize(credential: GoogleAccountCredential) {
        Log.d(TAG, "Initializing GoogleSheetsManager with credential")
        try {
            val httpTransport = NetHttpTransport()
            val jsonFactory = GsonFactory()
            
            Log.d(TAG, "Creating Drive service...")
            driveService = Drive.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("TxE")
                .build()
            Log.d(TAG, "Drive service created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Drive service", e)
            throw e
        }
    }

    fun setSelectedSheetId(sheetId: String) {
        selectedSheetId = sheetId
        prefs.edit().putString(KEY_SELECTED_SHEET_ID, sheetId).apply()
        Log.d(TAG, "Selected sheet ID saved: $sheetId")
    }

    fun getSelectedSheetId(): String? {
        return prefs.getString(KEY_SELECTED_SHEET_ID, null)
    }

    suspend fun getAvailableSheets(): List<Sheet> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to search for Google Sheets...")
            val drive = driveService ?: throw IllegalStateException("Drive service not initialized")
            
            val query = "mimeType='application/vnd.google-apps.spreadsheet'"
            Log.d(TAG, "Using query: $query")
            
            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val sheets = result.files?.map { file ->
                Log.d(TAG, "Found sheet: ${file.name} (${file.id})")
                Sheet(file.id, file.name)
            } ?: emptyList()

            Log.d(TAG, "Total sheets found: ${sheets.size}")
            sheets
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available sheets", e)
            throw e
        }
    }

    fun setupSheetsService(account: GoogleSignInAccount) {
        try {
            Log.d(TAG, "Setting up Sheets service for account: ${account.email}")
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(SheetsScopes.SPREADSHEETS_READONLY)
            ).apply {
                selectedAccount = account.account
            }

            sheetsService = Sheets.Builder(
                NetHttpTransport(),
                GsonFactory(),
                credential
            )
                .setApplicationName("TxE Keyboard")
                .build()

            Log.d(TAG, "Sheets service initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up Sheets service", e)
            throw e
        }
    }

    suspend fun loadSheets(): List<SheetInfo> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: throw IllegalStateException("Drive service not initialized")
            
            val query = "mimeType='application/vnd.google-apps.spreadsheet'"
            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val sheets = result.files?.map { file ->
                SheetInfo(
                    id = file.id,
                    name = file.name
                )
            } ?: emptyList()
            
            Log.d(TAG, "Loaded ${sheets.size} sheets: ${sheets.map { it.name }}")
            sheets
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sheets", e)
            emptyList()
        }
    }

    suspend fun syncFromGoogleSheets(): List<Expander> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Syncing data from Google Sheets with sheetId: $selectedSheetId")
            
            if (selectedSheetId == null) {
                Log.e(TAG, "No sheet selected")
                throw IllegalStateException("No sheet selected")
            }
            
            if (sheetsService == null) {
                Log.e(TAG, "Sheets service not initialized")
                throw IllegalStateException("Sheets service not initialized")
            }
            
            val range = "A:B"
            val response = sheetsService?.spreadsheets()?.values()
                ?.get(selectedSheetId, range)
                ?.execute()
                
            Log.d(TAG, "Response from Google Sheets: ${response?.getValues()?.size ?: 0} rows")
            
            val values = response?.getValues() ?: emptyList()
            Log.d(TAG, "Raw values from sheet: $values")
            
            val expanders = values.mapNotNull { row ->
                if (row.size >= 2) {
                    // Column A is shortcut, Column B is value
                    val shortcut = row[0].toString().trim()
                    val value = row[1].toString().trim()
                    Log.d(TAG, "Processing row: shortcut=$shortcut, value=$value")
                    if (shortcut.isNotEmpty() && value.isNotEmpty()) {
                        Expander(shortcut, value)
                    } else {
                        Log.d(TAG, "Skipping empty shortcut or value")
                        null
                    }
                } else {
                    Log.d(TAG, "Skipping row with insufficient columns: $row")
                    null
                }
            }

            Log.d(TAG, "Created ${expanders.size} expanders from sheet data")
            
            // Sync to dictionary
            dictionaryManager.syncFromGoogleSheets(expanders)
            Log.d(TAG, "Synced expanders to dictionary")

            Log.d(TAG, "Successfully synced ${expanders.size} expanders from sheet")
            expanders
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing from Google Sheets", e)
            throw e
        }
    }

    suspend fun syncToGoogleSheets(expanders: List<Expander>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null || selectedSheetId == null) {
                Log.e(TAG, "Error syncing to Google Sheets: Sheets service not initialized or no sheet selected")
                return@withContext
            }

            // Prepare data for Google Sheets
            val values = listOf(listOf("shortcut", "value")) + // Header row
                expanders.map { listOf(it.shortcut, it.value) }

            val body = ValueRange().setValues(values)
            sheetsService?.spreadsheets()?.values()
                ?.update(selectedSheetId, "A1", body)
                ?.setValueInputOption("RAW")
                ?.execute()

            Log.d(TAG, "Synced ${expanders.size} expanders to Google Sheets")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing to Google Sheets", e)
        }
    }
} 