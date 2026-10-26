package com.university.messenger.server.route

import com.university.messenger.domain.api.AuthResponse
import com.university.messenger.domain.api.ChannelResponse
import com.university.messenger.domain.api.CreateGroupRequest
import com.university.messenger.domain.api.MessageResponse
import com.university.messenger.domain.api.PostMessageRequest
import com.university.messenger.domain.api.RegisterStudentRequest
import com.university.messenger.server.MemoryTokens
import com.university.messenger.server.PlainHasher
import com.university.messenger.server.buildContext
import com.university.messenger.server.module
import com.university.messenger.server.persistence.InMemoryMessenger
import com.university.messenger.server.service.MemoryFileStorage
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuthRouteTest {
    @Test
    fun registerCreateGroupAndReadHistory() = testApplication {
        val store = InMemoryMessenger()
        application {
            module(buildContext(store, store, store, MemoryFileStorage(), PlainHasher(), MemoryTokens()))
        }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        val created = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterStudentRequest("ola@uni.local", "Haslo123", "Ola"))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val auth = created.body<AuthResponse>()
        assertEquals("STUDENT", auth.user.role)

        val group = client.post("/api/channels") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest("Lab"))
        }
        assertEquals(HttpStatusCode.Created, group.status)
        val channelId = group.body<ChannelResponse>().id

        client.post("/api/channels/$channelId/messages") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
            contentType(ContentType.Application.Json)
            setBody(PostMessageRequest("Hello"))
        }
        val history = client.get("/api/channels/$channelId/messages") {
            header(HttpHeaders.Authorization, "Bearer ${auth.token}")
        }
        assertEquals("Hello", history.body<List<MessageResponse>>().single().body)
    }
}
