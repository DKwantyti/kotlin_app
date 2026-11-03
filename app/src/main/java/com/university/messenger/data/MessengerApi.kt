package com.university.messenger.data

import com.university.messenger.domain.api.AuthResponse
import com.university.messenger.domain.api.ChannelResponse
import com.university.messenger.domain.api.CreateGroupRequest
import com.university.messenger.domain.api.ErrorResponse
import com.university.messenger.domain.api.LiveEvent
import com.university.messenger.domain.api.LoginRequest
import com.university.messenger.domain.api.MessageResponse
import com.university.messenger.domain.api.PostMessageRequest
import com.university.messenger.domain.api.RegisterStudentRequest
import com.university.messenger.domain.api.RegisterTeacherRequest
import com.university.messenger.domain.api.UserResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.patch
import com.university.messenger.domain.api.ReactionRequest
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.jvm.javaio.copyTo
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.io.File
import java.io.InputStream
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.Json

class ApiException(message: String) : Exception(message)

class MessengerApi(private val client: io.ktor.client.HttpClient = ApiClient.http) {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun register(email: String, password: String, displayName: String): AuthResponse {
        return client.post(url("/api/auth/register")) {
            contentType(ContentType.Application.Json)
            setBody(RegisterStudentRequest(email, password, displayName))
        }.parse()
    }

    suspend fun registerTeacher(email: String, password: String, displayName: String, code: String): AuthResponse {
        return client.post(url("/api/auth/register/teacher")) {
            contentType(ContentType.Application.Json)
            setBody(RegisterTeacherRequest(email, password, displayName, code))
        }.parse()
    }

    suspend fun login(email: String, password: String): AuthResponse {
        return client.post(url("/api/auth/login")) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
        }.parse()
    }

    suspend fun mailPreferences(): com.university.messenger.domain.api.MailPreferences =
        client.get(url("/api/auth/me/mail")) { auth() }.parse()

    suspend fun updateMailPreferences(enabled: Boolean): com.university.messenger.domain.api.MailPreferences {
        return client.patch(url("/api/auth/me/mail")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(com.university.messenger.domain.api.UpdateMailPreferences(enabled))
        }.parse()
    }

    suspend fun resendVerification() {
        val response = client.post(url("/api/auth/me/mail/verification")) { auth() }
        if (response.status.value !in 200..299) throw ApiException(response.errorMessage())
    }

    suspend fun sendGroupMail(channelId: String, subject: String, body: String): com.university.messenger.domain.api.GroupMailResult {
        return client.post(url("/api/channels/$channelId/mail")) {
            timeout {
                requestTimeoutMillis = 450_000
                socketTimeoutMillis = 450_000
            }
            auth()
            contentType(ContentType.Application.Json)
            setBody(com.university.messenger.domain.api.GroupMailRequest(subject, body))
        }.parse()
    }

    suspend fun profile(): UserResponse = client.get(url("/api/auth/me")) { auth() }.parse()

    suspend fun updateProfile(displayName: String): UserResponse {
        return client.patch(url("/api/auth/me")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(com.university.messenger.domain.api.UpdateProfileRequest(displayName))
        }.parse()
    }

    suspend fun channels(): List<ChannelResponse> {
        return client.get(url("/api/channels")) { auth() }.parse()
    }

    suspend fun teachers(): List<UserResponse> {
        return client.get(url("/api/teachers")) { auth() }.parse()
    }

    suspend fun createGroup(name: String): ChannelResponse {
        return client.post(url("/api/channels")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name))
        }.parse()
    }

    suspend fun addChannelMember(channelId: String, email: String) {
        val response = client.post(url("/api/channels/$channelId/members")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(com.university.messenger.domain.api.AddChannelMemberRequest(email))
        }
        if (response.status.value !in 200..299) throw ApiException(response.errorMessage())
    }

    suspend fun openDirect(userId: String): ChannelResponse {
        return client.post(url("/api/direct/$userId")) { auth() }.parse()
    }

    suspend fun messages(channelId: String, query: String? = null): List<MessageResponse> {
        return client.get(url("/api/channels/$channelId/messages")) {
            auth()
            if (query != null) parameter("q", query)
        }.parse()
    }

    suspend fun sendMessage(channelId: String, body: String, replyToId: String?): MessageResponse {
        return client.post(url("/api/channels/$channelId/messages")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(PostMessageRequest(body, replyToId))
        }.parse()
    }

    suspend fun editMessage(messageId: String, body: String): MessageResponse {
        return client.patch(url("/api/messages/$messageId")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(com.university.messenger.domain.api.EditMessageRequest(body))
        }.parse()
    }

    suspend fun deleteMessage(messageId: String, mode: com.university.messenger.domain.model.DeletionMode) {
        val response = client.delete(url("/api/messages/$messageId?mode=${mode.name.lowercase()}")) { auth() }
        if (response.status.value !in 200..299) throw ApiException(response.errorMessage())
    }

    suspend fun react(messageId: String, emoji: String?): MessageResponse {
        return client.patch(url("/api/messages/$messageId/reaction")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(ReactionRequest(emoji))
        }.parse()
    }

    suspend fun upload(
        channelId: String,
        fileName: String,
        size: Long,
        body: String,
        source: InputStream,
        replyToId: String?,
    ): MessageResponse {
        val safeName = fileName.replace("\"", "")
        val response = client.submitFormWithBinaryData(
            url = url("/api/channels/$channelId/files"),
            formData = formData {
                append("body", body)
                if (replyToId != null) append("replyToId", replyToId)
                appendInput(
                    key = "file",
                    headers = Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"$safeName\"")
                    },
                    size = size.takeIf { it > 0L },
                ) { source.asSource().buffered() }
            },
        ) { auth() }
        return response.parse()
    }

    suspend fun listen(channelId: String, onEvent: (LiveEvent) -> Unit) {
        client.webSocket(urlString = liveUrl(channelId), request = { auth() }) {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val event = runCatching { json.decodeFromString<LiveEvent>(frame.readText()) }.getOrNull() ?: continue
                onEvent(event)
            }
        }
    }

    suspend fun download(attachmentId: String, target: File) {
        client.prepareGet(url("/api/attachments/$attachmentId")) {
            auth()
        }.execute { response ->
            if (response.status.value !in 200..299) {
                throw ApiException(response.errorMessage())
            }
            target.outputStream().use { output ->
                response.bodyAsChannel().copyTo(output)
            }
        }
    }

    private fun url(path: String): String = SessionStore.baseUrl.trimEnd('/') + path

    private fun liveUrl(channelId: String): String {
        val base = SessionStore.baseUrl.trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        return "$base/api/channels/$channelId/live"
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth() {
        val token = SessionStore.token ?: return
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private suspend inline fun <reified T> HttpResponse.parse(): T {
        if (status.value !in 200..299) throw ApiException(errorMessage())
        return body()
    }

    private suspend fun HttpResponse.errorMessage(): String {
        return runCatching { body<ErrorResponse>().error }.getOrDefault(status.description)
    }
}
