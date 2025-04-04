package com.example.txe

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.txe.ui.screens.MainScreen
import com.example.txe.ui.theme.TxETheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.SheetsScopes

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private val viewModel: MainViewModel by viewModels()
    private lateinit var googleSignInClient: GoogleSignInClient

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Permissions granted, retrying sync")
            viewModel.syncFromGoogleSheets(this)
        } else {
            Log.w(TAG, "Permissions denied by user")
            viewModel.handleSignInError("User denied permission")
        }
    }

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            viewModel.handleSignInResult(account)
        } catch (e: ApiException) {
            Log.e(TAG, "Sign in failed", e)
            viewModel.handleSignInError("Sign in failed: ${e.statusCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate")

        // Setup Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_READONLY), Scope(SheetsScopes.SPREADSHEETS_READONLY))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Check if user is already signed in
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            Log.d(TAG, "User already signed in: ${account.email}")
            viewModel.handleSignInResult(account)
        }

        setContent {
            TxETheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        onSignInClick = { signIn() },
                        onSyncClick = { viewModel.syncFromGoogleSheets(this) },
                        onSheetSelected = { sheetId, sheetName ->
                            viewModel.onSheetSelected(sheetId, sheetName, this)
                        }
                    )
                }
            }
        }
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GoogleDriveManager.REQUEST_CODE_PERMISSIONS) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Permissions granted, retrying sync")
                viewModel.syncFromGoogleSheets(this)
            } else {
                Log.w(TAG, "Permissions denied by user")
                viewModel.handleSignInError("User denied permission")
            }
        }
    }

    fun launchPermissionRequest(intent: Intent) {
        permissionLauncher.launch(intent)
    }
}