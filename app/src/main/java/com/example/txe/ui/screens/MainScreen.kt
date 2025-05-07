package com.example.txe.ui.screens

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.txe.MainActivity
import com.example.txe.MainViewModel
import com.example.txe.MainUiState
import com.example.txe.Expander
import com.example.txe.R
import com.example.txe.SheetInfo
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
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
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo_in_app),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(100.dp) // Kích thước hợp lý hơn
                                .align(Alignment.CenterStart) // Căn chỉnh về phía bên trái
                                .padding(4.dp)
                        )
                    }
                },
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
                        onRefresh = { viewModel.loadExpanders() }, // Thêm hàm làm mới
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRefreshing by remember { mutableStateOf(false) }
    var hasAttemptedRefresh by remember { mutableStateOf(false) }

    // Hiển thị một full-screen loader khi đang làm mới
    if (isRefreshing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(50.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Đang đồng bộ shortcuts...",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    } else {
        // Sử dụng SwipeRefresh với cấu hình tùy chỉnh
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing = false), // Luôn tắt trạng thái isRefreshing mặc định
            onRefresh = {
                isRefreshing = true
                hasAttemptedRefresh = true
                onSearchQueryChange("") // Xóa search query
                onRefresh() // Gọi hàm làm mới

                // Giả lập thời gian tải để người dùng có thể thấy trạng thái loading
                MainScope().launch {
                    delay(1500)
                    isRefreshing = false
                }
            },
            indicator = { state, refreshTrigger ->
                // Tùy chỉnh indicator để nó hiển thị rõ ràng hơn
                SwipeRefreshIndicator(
                    state = state,
                    refreshTriggerDistance = refreshTrigger,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    scale = true
                )
            }
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Hiển thị thông báo đồng bộ nếu đã làm mới
                if (hasAttemptedRefresh && expanders.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            Column {
                                Text(
                                    "Đã đồng bộ shortcuts",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    "Không tìm thấy shortcuts. Thêm shortcuts mới hoặc kéo xuống để làm mới.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

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

                // Sử dụng LazyColumn để tận dụng scroll mượt mà
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (expanders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize()
                                    .padding(top = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(60.dp)
                                            .padding(bottom = 16.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        "Không có shortcuts",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Row(
                                        modifier = Modifier.padding(top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            " Kéo xuống để làm mới",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    } else {
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