package org.course.llm.chatapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.animation.core.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ChatMessage(
    val content: String,
    val isUserMessage: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val customStyle: ChatBubbleStyle? = null
)

data class ChatInput(
    val message: String,
    val conversationId: String
)

data class TranscribedMessageReply(
    val transcribedInputText: String,
    val outputText: String
)

sealed class ChatBubbleStyle {
    abstract val alignment: Alignment
    abstract val backgroundColor: Color
    abstract val textColor: Color

    object User : ChatBubbleStyle() {
        override val alignment = Alignment.CenterEnd
        override val backgroundColor = Color.LightGray
        override val textColor = Color.DarkGray
    }

    object Agent : ChatBubbleStyle() {
        override val alignment = Alignment.CenterStart
        override val backgroundColor = Color(0xFFFF100D)
        override val textColor = Color.White
    }

    // Gray bubble for transcribed user input
    object Transcribed : ChatBubbleStyle() {
        override val alignment = Alignment.CenterEnd
        override val backgroundColor = Color.LightGray
        override val textColor = Color.DarkGray
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val defaultStyle = if (message.isUserMessage) ChatBubbleStyle.User else ChatBubbleStyle.Agent
    val style = message.customStyle ?: defaultStyle
    ChatBubbleWithStyle(message.content, style)
}

@Composable
fun ChatBubbleWithStyle(content: String, style: ChatBubbleStyle) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
        contentAlignment = style.alignment
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (style is ChatBubbleStyle.Agent) Arrangement.Start else Arrangement.End
        ) {
            // Show agent icon only for agent messages
            if (style is ChatBubbleStyle.Agent) {
                Image(
                    painter = painterResource("AgentIcon.png"),
                    contentDescription = "Agent",
                    modifier = Modifier.size(60.dp).padding(end = 8.dp),
                    alignment = Alignment.TopStart
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = style.backgroundColor,
                modifier = Modifier.widthIn(max = 400.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = content,
                        modifier = Modifier.padding(12.dp),
                        color = style.textColor
                    )
                }
            }
        }
    }
}

@Composable
fun TextChatScreen(httpClient: HttpClient, conversationId: String) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Recorder + overlay states
    var showRecordOverlay by remember(conversationId) { mutableStateOf(false) }
    var isRecording by remember(conversationId) { mutableStateOf(false) }

    // Animation for record button in overlay
    val recordScale by animateFloatAsState(
        targetValue = if (isRecording) 1.2f else 1f,
        animationSpec = if (isRecording) {
            infiniteRepeatable(
                animation = tween(durationMillis = 500),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(durationMillis = 300)
        },
        label = "recordButtonScale"
    )

    // Reset all UI state when the conversationId changes so previous messages disappear
    var inputText by remember(conversationId) { mutableStateOf("") }
    var messages by remember(conversationId) { mutableStateOf(listOf<ChatMessage>()) }
    var isLoading by remember(conversationId) { mutableStateOf(false) }

    val audioRecorder = remember(conversationId) { AudioRecorder() }
    DisposableEffect(audioRecorder) {
        onDispose {
            audioRecorder.cleanup()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
        // Progress bar
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                color = Color(0xFFFF100D),
                trackColor = Color(0xFFFF100D).copy(alpha = 0.3f)
            )
        }

        // Chat messages area
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
        }

        // Input area
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Create focus requesters for input field and send button
            val inputFieldFocus = remember { FocusRequester() }
            val sendButtonFocus = remember { FocusRequester() }

            // Function to send message
            val sendMessage = {
                if (inputText.isNotBlank() && !isLoading) {
                    val userMessage = ChatMessage(inputText, true)
                    messages = messages + userMessage
                    isLoading = true

                    scope.launch {
                        try {
                            val response = httpClient.post("http://localhost:8082/chat") {
                                contentType(ContentType.Application.Json)
                                setBody(ChatInput(inputText, conversationId))
                            }

                            val responseText = response.body<String>()
                            messages = messages + ChatMessage(responseText, false)

                            // Scroll to the bottom
                            listState.animateScrollToItem(messages.size - 1)
                        } catch (e: Exception) {
                            messages = messages + ChatMessage("Error: ${e.message}", false)
                        } finally {
                            isLoading = false
                            inputText = ""
                            // Return focus to input field after sending
                            inputFieldFocus.requestFocus()
                        }
                    }
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, bottom = 8.dp)
                    .focusRequester(inputFieldFocus)
                    .focusProperties {
                        next = sendButtonFocus
                    }
                    .onKeyEvent { event ->
                        when (event.key) {
                            Key.Tab -> {
                                // Move focus to send button when Tab is pressed
                                sendButtonFocus.requestFocus()
                                true // Consume the event to prevent default behavior
                            }
                            Key.Enter -> {
                                // Send message when Enter is pressed
                                sendMessage()
                                true // Consume the event
                            }
                            else -> false // Don't consume other key events
                        }
                    },
                placeholder = { Text("Type a message...") },
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { sendMessage() },
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 8.dp)
                    .focusRequester(sendButtonFocus)
                    .focusProperties {
                        previous = inputFieldFocus
                    },
                enabled = !isLoading && inputText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF100D))
            ) {
                Text("Send")
            }

            Button(
                onClick = {
                    if (!isLoading) {
                        // Show overlay and start recording immediately
                        showRecordOverlay = true
                        if (!isRecording) {
                            isRecording = true
                            audioRecorder.startRecording()
                        }
                    }
                },
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 8.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF100D))
            ) {
                Text("Rec")
            }
        }

        }

        // Recording overlay
        if (showRecordOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Big circular record/stop button
                    Box(
                        modifier = Modifier
                            .size(120.dp * recordScale)
                            .clip(CircleShape)
                            .background(if (isRecording) Color(0xFFFF100D) else Color.LightGray)
                            .clickable {
                                if (!isRecording) {
                                    isRecording = true
                                    audioRecorder.startRecording()
                                } else {
                                    // Stop and send
                                    isRecording = false
                                    showRecordOverlay = false
                                    isLoading = true
                                    scope.launch {
                                        try {
                                            val response = uploadRecording(
                                                audioRecorder = audioRecorder,
                                                httpClient = httpClient,
                                                url = "http://localhost:8082/audio-in-text-out-chat",
                                                conversationId = conversationId
                                            )

                                            if (response.status.value != 200) {
                                                throw Exception("Server returned error: ${response.status.value} ${response.status.description}")
                                            }
                                            val reply = response.body<TranscribedMessageReply>()

                                            messages = messages + ChatMessage(
                                                reply.transcribedInputText,
                                                isUserMessage = true,
                                                customStyle = ChatBubbleStyle.Transcribed
                                            )
                                            messages = messages + ChatMessage(
                                                reply.outputText,
                                                isUserMessage = false
                                            )

                                            listState.animateScrollToItem(messages.size - 1)
                                        } catch (e: Exception) {
                                            messages = messages + ChatMessage("Error: ${e.message}", false)
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (isRecording) "Stop" else "Record", color = Color.White)
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = {
                                // Cancel overlay
                                if (isRecording) {
                                    isRecording = false
                                    audioRecorder.stopRecording()
                                }
                                showRecordOverlay = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.DarkGray,
                                contentColor = Color.White
                            )
                        ) { Text("Cancel") }
                    }
                }
            }
        }
    }
}