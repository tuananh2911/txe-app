package com.example.txe

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWindowContent(
    messages: MutableList<Message>,
    onMinimize: () -> Unit,
    onSendCommand: (String) -> Unit
) {
    val context = LocalContext.current
    val textToSpeech = remember {
        TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                // Xử lý lỗi nếu cần
            }
        }
    }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current) // Lấy chiều cao bàn phím

    // Cuộn xuống tin nhắn mới nhất hoặc khi bàn phím xuất hiện
    LaunchedEffect(messages.size, keyboardHeight) {
        coroutineScope.launch {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
            if (keyboardHeight > 0) {
                // Đảm bảo ô nhập được đẩy lên trên bàn phím
                listState.animateScrollBy(keyboardHeight.toFloat())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F2F5), shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .padding(top = 16.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)
            .imePadding() // Thêm padding để xử lý bàn phím
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
            state = listState,
            verticalArrangement = Arrangement.Bottom,
            reverseLayout = false
        ) {
            items(messages) { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = if (message.isUserMessage) Arrangement.End else Arrangement.Start
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                if (message.isUserMessage) Color(0xFF0084FF) else Color(0xFFE4E6EB),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.text,
                            color = if (message.isUserMessage) Color.White else Color.Black,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(end = if (message.speakerInfo != null) 8.dp else 0.dp)
                        )
                        if (message.speakerInfo != null && !message.isUserMessage) {
                            IconButton(onClick = {
                                val (word, language) = message.speakerInfo
                                textToSpeech.language = Locale(language)
                                textToSpeech.speak(word, TextToSpeech.QUEUE_FLUSH, null, null)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play pronunciation",
                                    tint = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .padding(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            onSendCommand(inputText)
                            inputText = ""
                        }
                    }
                ),
                placeholder = { Text("Nhập lệnh...") },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFF0084FF),
                    unfocusedBorderColor = Color.Gray
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = {
                if (inputText.isNotBlank()) {
                    onSendCommand(inputText)
                    inputText = ""
                }
            }) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color(0xFF0084FF)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }
}