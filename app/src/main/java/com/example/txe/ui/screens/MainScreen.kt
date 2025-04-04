package com.example.txe.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.txe.MainActivity
import com.example.txe.MainViewModel
import com.example.txe.MainUiState
import com.example.txe.Expander
import com.example.txe.Sheet
import com.example.txe.SheetInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSignInClick: () -> Unit,
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
    LaunchedEffect(Unit) {
        viewModel.loadExpanders()
    }

    LaunchedEffect(uiState) {
        if (uiState is MainUiState.PermissionRequired) {
            val intent = (uiState as MainUiState.PermissionRequired).intent
            (context as? MainActivity)?.launchPermissionRequest(intent)
        }
    }

    when (uiState) {
        is MainUiState.Success -> {
            val state = uiState as MainUiState.Success
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("TxE Keyboard") },
                            actions = {
                                IconButton(onClick = onSignInClick) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Sign In")
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, "Settings") },
                                label = { Text("Settings") },
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.List, "Shortcuts") },
                                label = { Text("Shortcuts") },
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 }
                            )
                        }
                    }
                ) { padding ->
                    when (selectedTab) {
                        0 -> SettingsTab(
                            shortcutInput = shortcutInput,
                            valueInput = valueInput,
                            onShortcutInputChange = viewModel::onShortcutInputChange,
                            onValueInputChange = viewModel::onValueInputChange,
                            onAddExpander = viewModel::addExpander,
                            onOpenOverlaySettings = viewModel::openOverlaySettings,
                            onSetKeyboardEnabled = viewModel::setKeyboardEnabled,
                            onSetOverlayEnabled = viewModel::setOverlayEnabled,
                            onToggleFloatingWindow = viewModel::toggleFloatingWindow,
                            onSyncClick = {
                                onSyncClick()
                                showSheetDialog = true
                            },
                            isKeyboardEnabled = state.isKeyboardEnabled,
                            isOverlayEnabled = state.isOverlayEnabled,
                            isFloatingWindowEnabled = state.isFloatingWindowEnabled,
                            modifier = Modifier.padding(padding)
                        )
                        1 -> ShortcutsTab(
                            expanders = filteredExpanders,
                            searchQuery = searchQuery,
                            onSearchQueryChange = viewModel::onSearchQueryChange,
                            onDeleteExpander = viewModel::deleteExpander,
                            modifier = Modifier.padding(padding)
                        )
                    }
                }
            }
        }
        is MainUiState.Error -> {
            val error = (uiState as MainUiState.Error).message
            ErrorContent(error = error, onRetry = viewModel::clearError)
        }
        is MainUiState.Loading -> {
            if (!showSheetDialog) { // Thêm điều kiện này
                LoadingContent()
            }
        }
        is MainUiState.SignInRequired -> {
            SignInContent(onSignInClick = onSignInClick)
        }
        is MainUiState.PermissionRequired -> {
            PermissionContent(
                onGrantPermission = {
                    val intent = (uiState as MainUiState.PermissionRequired).intent
                    (context as? MainActivity)?.launchPermissionRequest(intent)
                }
            )
        }
        is MainUiState.OpenChatWindow -> {
            val intent = (uiState as MainUiState.OpenChatWindow).intent
            LaunchedEffect(intent) { context.startActivity(intent) }
        }
        is MainUiState.NeedsKeyboardEnabled -> {
            val intent = (uiState as MainUiState.NeedsKeyboardEnabled).intent
            LaunchedEffect(intent) { context.startActivity(intent) }
        }
        is MainUiState.SelectSheetRequired -> {
            showSheetDialog = true
        }
        is MainUiState.Initial -> {
            LoadingContent()
        }
        is MainUiState.SheetsLoaded -> {
            showSheetDialog = true
        }
    }

    if (showSheetDialog) {
        when (uiState) {
            is MainUiState.Loading -> {

                SelectSheetScreen(
                    sheets = emptyList(),
                    isLoading = true,
                    error = null,
                    onSheetSelected = { sheet ->
                        onSheetSelected(sheet.id, sheet.name)
                        showSheetDialog = false
                    },
                    onDismiss = { showSheetDialog = false } // Đóng khi hủy
                )
            }
            is MainUiState.SheetsLoaded -> {
                val sheets = (uiState as MainUiState.SheetsLoaded).sheets.map {
                    Sheet(id = it.id, name = it.name) // Ánh xạ SheetInfo sang Sheet
                }
                SelectSheetScreen(
                    sheets = sheets,
                    isLoading = false,
                    error = null,
                    onSheetSelected = { sheet ->
                        onSheetSelected(sheet.id, sheet.name)
                        showSheetDialog = false
                    },
                    onDismiss = { showSheetDialog = false } // Đóng khi hủy
                )
            }
            is MainUiState.Error -> {
                val error = (uiState as MainUiState.Error).message
                SelectSheetScreen(
                    sheets = emptyList(),
                    isLoading = false,
                    error = error,
                    onSheetSelected = { /* Không gọi được */ },
                    onDismiss = { showSheetDialog = false } // Đóng khi hủy
                )
            }
            else -> {
                showSheetDialog = false
            }
        }
    }

    (uiState as? MainUiState.Success)?.message?.let { msg ->
        Box(modifier = Modifier.fillMaxSize()) {
            Snackbar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = msg)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearMessage) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    shortcutInput: String,
    valueInput: String,
    onShortcutInputChange: (String) -> Unit,
    onValueInputChange: (String) -> Unit,
    onAddExpander: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onSetKeyboardEnabled: (Boolean) -> Unit,
    onSetOverlayEnabled: (Boolean) -> Unit,
    onToggleFloatingWindow: () -> Unit,
    onSyncClick: () -> Unit,
    isKeyboardEnabled: Boolean,
    isOverlayEnabled: Boolean,
    isFloatingWindowEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Add Shortcut",
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedTextField(
                    value = shortcutInput,
                    onValueChange = onShortcutInputChange,
                    label = { Text("Shortcut") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = valueInput,
                    onValueChange = onValueInputChange,
                    label = { Text("Value") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = onAddExpander,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Keyboard")
                    Switch(
                        checked = isKeyboardEnabled,
                        onCheckedChange = onSetKeyboardEnabled
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Overlay")
                    Switch(
                        checked = isOverlayEnabled,
                        onCheckedChange = onSetOverlayEnabled
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show Floating Chat")
                    Switch(
                        checked = isFloatingWindowEnabled,
                        onCheckedChange = { onToggleFloatingWindow() }
                    )
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = onSyncClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync with Google Sheets")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsTab(
    expanders: List<Expander>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDeleteExpander: (Expander) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Your Shortcuts",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            placeholder = { Text("Search shortcuts...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            singleLine = true
        )

        if (expanders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) "No shortcuts added yet" else "No shortcuts found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = expander.shortcut,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = expander.value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun SignInContent(
    onSignInClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Please sign in to continue",
                style = MaterialTheme.typography.titleLarge
            )
            Button(onClick = onSignInClick) {
                Text("Sign in with Google")
            }
        }
    }
}

@Composable
fun PermissionContent(
    onGrantPermission: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Permission Required",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Please grant permission to access Google Sheets",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = onGrantPermission) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun SheetSelectionDialog(
    onDismiss: () -> Unit,
    onSheetSelected: (String, String) -> Unit,
    sheets: List<SheetInfo>,
    isLoading: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isLoading) "Loading Sheets" else "Select Google Sheet") },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn {
                    items(sheets) { sheet ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            onClick = { onSheetSelected(sheet.id, sheet.name) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = sheet.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = sheet.id,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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

@Composable
fun SignInDialog(
    onDismiss: () -> Unit,
    onSignInClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in Required") },
        text = { Text("Please sign in to access your Google Sheets") },
        confirmButton = {
            TextButton(
                onClick = {
                    onSignInClick()
                    onDismiss()
                }
            ) {
                Text("Sign in")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ErrorDialog(
    error: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Error") },
        text = { Text(error) },
        confirmButton = {
            TextButton(
                onClick = {
                    onRetry()
                    onDismiss()
                }
            ) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}