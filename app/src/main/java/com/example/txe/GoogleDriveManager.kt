package com.example.txe

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class GoogleDriveManager private constructor(private val context: Context) {
    private val TAG = "GoogleDriveManager"
    private val SCOPES = listOf(
        DriveScopes.DRIVE_READONLY,
        SheetsScopes.SPREADSHEETS_READONLY
    )
    private var credential: GoogleAccountCredential? = null

    companion object {
        @Volatile
        private var INSTANCE: GoogleDriveManager? = null

        fun getInstance(context: Context): GoogleDriveManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GoogleDriveManager(context).also { INSTANCE = it }
            }
        }

        const val REQUEST_CODE_PERMISSIONS = 1001
    }

    fun initialize(credential: GoogleAccountCredential) {
        this.credential = credential
        Log.d(TAG, "GoogleDriveManager initialized with credential")
    }

    private fun getDriveService(): Drive {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("No Google account signed in")

        val credential = this.credential ?: GoogleAccountCredential.usingOAuth2(context, SCOPES).apply {
            selectedAccount = account.account
        }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("TxE Keyboard")
            .build()
    }

    private fun getSheetsService(): Sheets {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("No Google account signed in")

        val credential = this.credential ?: GoogleAccountCredential.usingOAuth2(context, SCOPES).apply {
            selectedAccount = account.account
        }

        return Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("TxE Keyboard")
            .build()
    }

    suspend fun listGoogleSheets(activity: Activity? = null): List<SheetInfo> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to list Google Sheets")
            val driveService = getDriveService()

            val result = driveService.files().list()
                .setQ("mimeType='application/vnd.google-apps.spreadsheet'")
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            Log.d(TAG, "Found ${result.files.size} sheets")

            result.files.map { file ->
                Log.d(TAG, "Processing sheet: ${file.name}")
                SheetInfo(
                    id = file.id,
                    name = file.name
                )
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.e(TAG, "User recoverable auth error: Need remote consent", e)
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: throw IllegalStateException("No Google account signed in")

            val scopesArray = SCOPES.map { Scope(it) }.toTypedArray()
            if (activity != null) {
                GoogleSignIn.requestPermissions(
                    activity,
                    REQUEST_CODE_PERMISSIONS,
                    account,
                    *scopesArray
                )
                Log.d(TAG, "Requested new permissions from user")
            } else {
                Log.e(TAG, "No Activity provided, cannot request permissions")
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list sheets", e)
            throw Exception("Failed to list sheets: ${e.message ?: "Unknown error"}")
        }
    }

    suspend fun readSheetData(sheetId: String): List<List<String>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting to read sheet data for sheet: $sheetId")
            val sheetsService = getSheetsService()

            val range = "A:B"
            val response = sheetsService.spreadsheets().values()
                .get(sheetId, range)
                .execute()

            Log.d(TAG, "Successfully read sheet data")
            response.getValues()?.map { row -> row.map { it.toString() } } ?: emptyList()
        } catch (e: UserRecoverableAuthIOException) {
            Log.e(TAG, "User recoverable auth error while reading sheet data", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read sheet data", e)
            throw Exception("Failed to read sheet data: ${e.message ?: "Unknown error"}")
        }
    }
}