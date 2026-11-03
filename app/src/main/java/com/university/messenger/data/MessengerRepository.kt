package com.university.messenger.data

import com.university.messenger.domain.api.ChannelResponse
import com.university.messenger.domain.api.LiveEvent
import com.university.messenger.domain.api.MessageResponse
import com.university.messenger.domain.api.UserResponse
import java.io.File
import java.io.InputStream

class MessengerRepository(private val api: MessengerApi = MessengerApi()) {
    suspend fun login(email: String, password: String) {
        val response = api.login(email, password)
        SessionStore.saveLogin(response.token, response.user.role, response.user.displayName, response.user.id)
    }

    suspend fun register(email: String, password: String, displayName: String) {
        val response = api.register(email, password, displayName)
        SessionStore.saveLogin(response.token, response.user.role, response.user.displayName, response.user.id)
    }

    suspend fun registerTeacher(email: String, password: String, displayName: String, code: String) {
        val response = api.registerTeacher(email, password, displayName, code)
        SessionStore.saveLogin(response.token, response.user.role, response.user.displayName, response.user.id)
    }

    suspend fun profile(): UserResponse = api.profile()

    suspend fun updateProfile(displayName: String): UserResponse =
        api.updateProfile(displayName).also { SessionStore.displayName = it.displayName }

    suspend fun channels(): List<ChannelResponse> = api.channels()

    suspend fun teachers(): List<UserResponse> = api.teachers()

    suspend fun createGroup(name: String): ChannelResponse = api.createGroup(name)

    suspend fun addChannelMember(channelId: String, email: String) = api.addChannelMember(channelId, email)

    suspend fun openDirect(userId: String): ChannelResponse = api.openDirect(userId)

    suspend fun messages(channelId: String): List<MessageResponse> = api.messages(channelId)

    suspend fun sendMessage(channelId: String, body: String, replyToId: String?): MessageResponse =
        api.sendMessage(channelId, body, replyToId)

    suspend fun editMessage(messageId: String, body: String) = api.editMessage(messageId, body)

    suspend fun deleteMessage(messageId: String, mode: com.university.messenger.domain.model.DeletionMode) =
        api.deleteMessage(messageId, mode)

    suspend fun react(messageId: String, emoji: String?) = api.react(messageId, emoji)

    suspend fun upload(
        channelId: String,
        fileName: String,
        size: Long,
        body: String,
        source: InputStream,
        replyToId: String?,
    ): MessageResponse = api.upload(channelId, fileName, size, body, source, replyToId)

    suspend fun download(attachmentId: String, target: File) = api.download(attachmentId, target)

    suspend fun listen(channelId: String, onEvent: (LiveEvent) -> Unit) = api.listen(channelId, onEvent)
}

object MessengerGraph {
    val repository: MessengerRepository by lazy { MessengerRepository() }
}
