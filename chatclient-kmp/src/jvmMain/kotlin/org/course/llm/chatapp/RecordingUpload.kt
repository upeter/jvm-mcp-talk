package org.course.llm.chatapp

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Utility to stop the current recording and upload it as multipart/form-data.
 * Handles temp file lifecycle and returns the raw HttpResponse so callers can decode as needed.
 */
suspend fun uploadRecording(
    audioRecorder: AudioRecorder,
    httpClient: HttpClient,
    url: String,
    conversationId: String
): HttpResponse {
    // Stop recording and obtain MP3 bytes
    val audioData = audioRecorder.stopRecording()

    // Create a temp file and write audio bytes off the main thread
    val tempFile = withContext(Dispatchers.IO) {
        val file = File.createTempFile("audio", ".mp3")
        file.writeBytes(audioData)
        file
    }

    try {
        // Submit multipart request
        return httpClient.submitFormWithBinaryData(
            url = url,
            formData = formData {
                append(
                    key = "audio",
                    value = tempFile.readBytes(),
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, "audio/mpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"${tempFile.name}\"")
                    }
                )
                // Add conversation id
                append("conversationId", conversationId)
            }
        ) {
            headers {
                append(HttpHeaders.Accept, ContentType.Application.OctetStream.toString())
            }
        }
    } finally {
        // Ensure temp file is removed
        withContext(Dispatchers.IO) {
            runCatching { tempFile.delete() }
        }
    }
}
