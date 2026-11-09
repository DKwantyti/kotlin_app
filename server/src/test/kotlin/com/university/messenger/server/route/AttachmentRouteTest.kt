package com.university.messenger.server.route

import com.university.messenger.domain.api.AuthResponse
import com.university.messenger.domain.api.ChannelResponse
import com.university.messenger.domain.api.CreateGroupRequest
import com.university.messenger.domain.api.MessageResponse
import com.university.messenger.domain.api.RegisterStudentRequest
import com.university.messenger.server.MemoryTokens
import com.university.messenger.server.PlainHasher
import com.university.messenger.server.buildContext
import com.university.messenger.server.module
import com.university.messenger.server.persistence.InMemoryMessenger
import com.university.messenger.server.storage.LocalFileStorage
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.util.Base64
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class AttachmentRouteTest {
    @TempDir
    lateinit var storageRoot: Path

    @Test
    fun textUploadAndDownloadPreserveBytesAndTrailingCaption() = roundTrip(
        "notes.txt", "Campus notes\r\nUnicode: привет, café\n".repeat(4096).toByteArray(Charsets.UTF_8),
    )

    @Test
    fun pngUploadAndDownloadPreserveBinaryBytes() = roundTrip(
        "campus.png",
        Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a9l0AAAAASUVORK5CYII="),
    )

    private fun roundTrip(fileName: String, original: ByteArray) = testApplication {
        val store = InMemoryMessenger()
        application {
            module(buildContext(store, store, store, LocalFileStorage(storageRoot), PlainHasher(), MemoryTokens()))
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        val auth = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterStudentRequest("attachment@uni.local", "Student123", "Attachment Test"))
        }.body<AuthResponse>()
        val group = client.post("/api/channels") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest("Attachment test"))
        }.body<ChannelResponse>()
        val uploaded = client.post("/api/channels/${group.id}/files") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            setBody(MultiPartFormDataContent(formData {
                append("file", original, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
                // Multipart fields may follow the file: advancing must not discard its bytes.
                append("body", "Caption after file")
            }))
        }
        assertEquals(HttpStatusCode.Created, uploaded.status, uploaded.body<String>())
        val message = uploaded.body<MessageResponse>()
        assertEquals("Caption after file", message.body)
        val attachment = message.attachments.single()
        assertEquals(fileName, attachment.fileName)
        assertEquals(original.size.toLong(), attachment.sizeBytes)
        val downloaded = client.get("/api/attachments/${attachment.id}") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }
        assertEquals(HttpStatusCode.OK, downloaded.status)
        assertArrayEquals(original, downloaded.body<ByteArray>())
    }
}
