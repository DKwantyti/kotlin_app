package com.university.messenger.data

import com.university.messenger.domain.api.ChannelResponse
import com.university.messenger.domain.api.MessageResponse
import com.university.messenger.domain.api.UserResponse
import java.io.File
import java.io.InputStream

class MessengerRepository(private val api: MessengerApi = MessengerApi()) {
    suspend fun login(email: String, password: String) {
        val response = api.login(email, password)
        SessionStore.saveLogin(response.token, response.user.role, response.user.displayName)
    }

    suspend fun register(email: String, password: String, displayName: String) {
        val response = api.register(email, password, displayName)
        SessionStore.saveLogin(response.token, response.user.role, response.user.displayName)
    }

    suspend fun channels(): List<ChannelResponse> = api.channels()

    suspend fun teachers(): List<UserResponse> = api.teachers()

    suspend fun createGroup(name: String): ChannelResponse = api.createGroup(name)

    suspend fun openDirect(userId: String): ChannelResponse = api.openDirect(userId)

    suspend fun messages(channelId: String): List<MessageResponse> = api.messages(channelId)

    suspend fun sendMessage(channelId: String, body: String): MessageResponse = api.sendMessage(channelId, body)

    suspend fun upload(
        channelId: String,
        fileName: String,
        size: Long,
        body: String,
        source: InputStream,
    ): MessageResponse = api.upload(channelId, fileName, size, body, source)

    suspend fun download(attachmentId: String, target: File) = api.download(attachmentId, target)
}

object MessengerGraph {
    val repository: MessengerRepository by lazy { MessengerRepository() }
}
