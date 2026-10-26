package com.university.messenger.data

import com.university.messenger.domain.api.AuthResponse
import com.university.messenger.domain.api.ChannelResponse
import com.university.messenger.domain.api.CreateGroupRequest
import com.university.messenger.domain.api.ErrorResponse
import com.university.messenger.domain.api.LoginRequest
import com.university.messenger.domain.api.MessageResponse
import com.university.messenger.domain.api.PostMessageRequest
import com.university.messenger.domain.api.RegisterStudentRequest
import com.university.messenger.domain.api.UserResponse
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.File
import java.io.InputStream
import kotlinx.io.asSource
import kotlinx.io.buffered

class ApiException(message: String) : Exception(message)

class MessengerApi(private val client: io.ktor.client.HttpClient = ApiClient.http) {
    suspend fun register(email: String, password: String, displayName: String): AuthResponse {
        return client.post(url("/api/auth/register")) {
            contentType(ContentType.Application.Json)
            setBody(RegisterStudentRequest(email, password, displayName))
        }.parse()
    }

    suspend fun login(email: String, password: String): AuthResponse {
        return client.post(url("/api/auth/login")) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email, password))
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

    suspend fun openDirect(userId: String): ChannelResponse {
        return client.post(url("/api/direct/$userId")) { auth() }.parse()
    }

    suspend fun messages(channelId: String): List<MessageResponse> {
        return client.get(url("/api/channels/$channelId/messages")) { auth() }.parse()
    }

    suspend fun sendMessage(channelId: String, body: String): MessageResponse {
        return client.post(url("/api/channels/$channelId/messages")) {
            auth()
            contentType(ContentType.Application.Json)
            setBody(PostMessageRequest(body))
        }.parse()
    }

    suspend fun upload(
        channelId: String,
        fileName: String,
        size: Long,
        body: String,
        source: InputStream,
    ): MessageResponse {
        val safeName = fileName.replace("\"", "")
        val response = client.submitFormWithBinaryData(
            url = url("/api/channels/$channelId/files"),
            formData = formData {
                append("body", body)
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
