package com.example.txe.ui.screens

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.txe.MainActivity
import com.example.txe.MainViewModel
import com.example.txe.MainUiState
import com.example.txe.Expander
import com.example.txe.SheetInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSyncClick: () -> Unit,
    onSheetSelected: (String, String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val expanders by viewModel.expanders.collectAsState()
    val shortcutInput by viewModel.shortcutInput.collectAsState()
    val valueInput by viewModel.valueInput.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredExpanders by viewModel.filteredExpanders.collectAsState()
    var showSheetDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d("MainScreen", "Permission launcher result received")
        viewModel.onPermissionResult()
    }

    // Làm mới trạng thái khi chuyển sang tab Settings
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) { // Tab Settings
            Log.d("MainScreen", "Refreshing system states for Settings tab")
            viewModel.refreshSystemStates()
        }
    }

    LaunchedEffect(Unit) {
        Log.d("MainScreen", "Initial load expanders")
        viewModel.loadExpanders()
    }

    LaunchedEffect(uiState) {
        Log.d("MainScreen", "UI State: $uiState")
        when (uiState) {
            is MainUiState.SignInRequired -> {
                (context as? MainActivity)?.launchSignIn()
            }
            is MainUiState.PermissionRequired -> {
                val intent = (uiState as MainUiState.PermissionRequired).intent
                permissionLauncher.launch(intent)
            }
            is MainUiState.OpenChatWindow -> {
                val intent = (uiState as MainUiState.OpenChatWindow).intent
                context.startService(intent)
                viewModel.clearMessage()
            }
            is MainUiState.NeedsKeyboardEnabled -> {
                val intent = (uiState as MainUiState.NeedsKeyboardEnabled).intent
                permissionLauncher.launch(intent)
            }
            is MainUiState.SheetsLoaded -> {
                showSheetDialog = true
            }
            is MainUiState.SelectSheetRequired -> {
                showSheetDialog = true
            }
            is MainUiState.Error -> {
                val message = (uiState as MainUiState.Error).message
                Log.e("MainScreen", "Error: $message")
                viewModel.clearError()
            }
            else -> {
                if (uiState !is MainUiState.Loading || !showSheetDialog) {
                    showSheetDialog = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GiGi") },
                actions = {
                    IconButton(onClick = onSyncClick) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Sync with Google Sheets"
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        when (uiState) {
            is MainUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is MainUiState.Success -> {
                when (selectedTab) {
                    0 -> ShortcutTab(
                        expanders = filteredExpanders,
                        shortcutInput = shortcutInput,
                        valueInput = valueInput,
                        searchQuery = searchQuery,
                        onShortcutInputChange = { viewModel.onShortcutInputChange(it) },
                        onValueInputChange = { viewModel.onValueInputChange(it) },
                        onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                        onAddExpander = { viewModel.addExpander() },
                        onDeleteExpander = { viewModel.deleteExpander(it) },
                        modifier = Modifier.padding(innerPadding)
                    )
                    1 -> SettingsTab(
                        isKeyboardEnabled = (uiState as MainUiState.Success).isKeyboardEnabled,
                        isOverlayEnabled = (uiState as MainUiState.Success).isOverlayEnabled,
                        isFloatingWindowEnabled = (uiState as MainUiState.Success).isFloatingWindowEnabled,
                        onKeyboardToggle = { viewModel.setKeyboardEnabled(it) },
                        onOverlayToggle = { viewModel.setOverlayEnabled(it) },
                        onFloatingWindowToggle = { viewModel.toggleFloatingWindow() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
            is MainUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (uiState as MainUiState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is MainUiState.SignInRequired -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Initializing...")
                }
            }
        }

        if (showSheetDialog) {
            SheetSelectionDialog(
                sheets = (uiState as? MainUiState.SheetsLoaded)?.sheets ?: emptyList(),
                onSheetSelected = { sheetId, sheetName ->
                    onSheetSelected(sheetId, sheetName)
                    showSheetDialog = false
                },
                onDismiss = {
                    showSheetDialog = false
                    viewModel.clearMessage()
                }
            )
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Add, contentDescription = "Shortcuts") },
            label = { Text("Shortcuts") }
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}

@Composable
fun ShortcutTab(
    expanders: List<Expander>,
    shortcutInput: String,
    valueInput: String,
    searchQuery: String,
    onShortcutInputChange: (String) -> Unit,
    onValueInputChange: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAddExpander: () -> Unit,
    onDeleteExpander: (Expander) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { newQuery ->
                Log.d("ShortcutTab", "Search input: $newQuery")
                onSearchQueryChange(newQuery)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            label = { Text("Search Shortcuts") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        Log.d("ShortcutTab", "Clearing search query")
                        onSearchQueryChange("")
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                    }
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = shortcutInput,
                onValueChange = onShortcutInputChange,
                modifier = Modifier.weight(1f),
                label = { Text("Shortcut") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            OutlinedTextField(
                value = valueInput,
                onValueChange = onValueInputChange,
                modifier = Modifier.weight(2f),
                label = { Text("Expanded Text") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
        }

        Button(
            onClick = onAddExpander,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text("Add Shortcut")
        }

        if (expanders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No shortcuts found")
            }
        } else {
            LazyColumn {
                items(expanders) { expander ->
                    ExpanderItem(
                        expander = expander,
                        onDelete = { onDeleteExpander(expander) }
                    )
                }
            }
        }
    }
}

@Composable
fun ExpanderItem(
    expander: Expander,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = expander.shortcut,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = expander.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Expander")
            }
        }
    }
}

@Composable
fun SettingsTab(
    isKeyboardEnabled: Boolean,
    isOverlayEnabled: Boolean,
    isFloatingWindowEnabled: Boolean,
    onKeyboardToggle: (Boolean) -> Unit,
    onOverlayToggle: (Boolean) -> Unit,
    onFloatingWindowToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable Keyboard",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isKeyboardEnabled,
                onCheckedChange = { onKeyboardToggle(it) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Show Overlay",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isOverlayEnabled,
                onCheckedChange = { onOverlayToggle(it) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Show Floating Chat",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isFloatingWindowEnabled,
                onCheckedChange = { onFloatingWindowToggle() }
            )
        }
    }
}

@Composable
fun SheetSelectionDialog(
    sheets: List<SheetInfo>,
    onSheetSelected: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Google Sheet") },
        text = {
            if (sheets.isEmpty()) {
                Text("No sheets found. Please create a sheet or check permissions.")
            } else {
                LazyColumn {
                    items(sheets) { sheet ->
                        Text(
                            text = sheet.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSheetSelected(sheet.id, sheet.name)
                                }
                                .padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}