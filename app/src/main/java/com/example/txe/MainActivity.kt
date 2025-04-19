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
        Log.d(TAG, "Permission result received, resultCode: ${result.resultCode}")
        viewModel.onPermissionResult()
    }

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            Log.d(TAG, "Sign in successful: ${account.email}")
            viewModel.handleSignInResult(account, this)
        } catch (e: ApiException) {
            Log.e(TAG, "Sign in failed with status code: ${e.statusCode}", e)
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

        setContent {
            TxETheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        onSyncClick = { viewModel.onSyncClick(this) },
                        onSheetSelected = { sheetId, sheetName ->
                            viewModel.onSheetSelected(sheetId, sheetName, this)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity onResume")
        viewModel.onPermissionResult()
    }

    fun launchPermissionRequest(intent: Intent) {
        Log.d(TAG, "Launching permission request")
        permissionLauncher.launch(intent)
    }

    fun launchSignIn() {
        Log.d(TAG, "Launching sign in intent")
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }
}