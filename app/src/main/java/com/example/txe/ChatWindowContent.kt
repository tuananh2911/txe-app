package com.example.txe

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWindowContent(
    messages: MutableList<Message>,
    onMinimize: () -> Unit,
    onSendCommand: (String) -> Unit,
    isChatVisible: Boolean = false
) {
    val context = LocalContext.current
    val textToSpeech = remember {
        TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Toast.makeText(context, "Không thể khởi tạo TextToSpeech", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isChatVisible) {
        if (isChatVisible) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(messages.size, keyboardHeight) {
        coroutineScope.launch {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
            if (keyboardHeight > 0) {
                listState.animateScrollBy(keyboardHeight.toFloat())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .padding(top = 16.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)
            .imePadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                MessageItem(
                    message = message,
                    textToSpeech = textToSpeech,
                    vibrator = vibrator
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .focusRequester(focusRequester),
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

@Composable
fun MessageItem(
    message: Message,
    textToSpeech: TextToSpeech,
    vibrator: Vibrator
) {
    val context = LocalContext.current
    val hasCopyableContent = message.userMessage.isNotEmpty() || message.text.isNotEmpty() || message.imagePath.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .wrapContentWidth(if (message.isUserMessage) Alignment.End else Alignment.Start)
        ) {
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .background(
                        if (message.isUserMessage) Color(0xFF0084FF) else Color(0xFFE4E6EB),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .wrapContentWidth()
                ) {
                    if (message.imagePath.isNotEmpty()) {
                        val file = File(message.imagePath)
                        if (file.exists()) {
                            Image(
                                painter = rememberAsyncImagePainter(file),
                                contentDescription = "Khu vực được chụp",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(bottom = 4.dp)
                            )
                        } else {
                            Text(
                                text = "Không thể tải ảnh: Tệp không tồn tại",
                                color = if (message.isUserMessage) Color.White else Color.Black,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    if (message.userMessage.isNotEmpty()) {
                        Text(
                            text = message.userMessage.replace("\n", " ").trim(),
                            color = Color.White,
                            fontSize = 16.sp,
                            softWrap = true,
                            modifier = Modifier
                                .wrapContentWidth()
                                .padding(bottom = if (message.text.isNotEmpty()) 4.dp else 0.dp)
                        )
                    }
                    if (message.text.isNotEmpty()) {
                        Text(
                            text = message.text.replace("\n", " ").trim(),
                            color = if (message.isUserMessage) Color.White else Color.Black,
                            fontSize = 16.sp,
                            softWrap = true,
                            modifier = Modifier
                                .wrapContentWidth()
                                .padding(end = if (message.speakerInfo != null) 8.dp else 0.dp)
                        )
                    }
                    if (message.speakerInfo != null && !message.isUserMessage) {
                        IconButton(onClick = {
                            val (word, language) = message.speakerInfo
                            textToSpeech.language = Locale(language)
                            textToSpeech.speak(word, TextToSpeech.QUEUE_FLUSH, null, null)
                        }) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play pronunciation",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            if (hasCopyableContent) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy message",
                    tint = Color.Gray,
                    modifier = Modifier
                        .size(20.dp)
                        .alpha(0.7f)
                        .padding(top = 4.dp)
                        .align(if (message.isUserMessage) Alignment.End else Alignment.Start)
                        .clickable {
                            val textToCopy = when {
                                message.userMessage.isNotEmpty() -> message.userMessage
                                message.text.isNotEmpty() -> message.text
                                message.imagePath.isNotEmpty() -> message.imagePath
                                else -> ""
                            }
                            if (textToCopy.isNotEmpty()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Message", textToCopy)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Đã sao chép", Toast.LENGTH_SHORT).show()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    vibrator.vibrate(50)
                                }
                            }
                        }
                )
            }
        }
    }
}