package com.university.messenger.server.persistence

import com.university.messenger.domain.model.Account
import com.university.messenger.domain.model.Attachment
import com.university.messenger.domain.model.ChannelSummary
import com.university.messenger.domain.model.Message
import com.university.messenger.domain.model.Role
import com.university.messenger.domain.model.StudentItem
import java.util.UUID

data class StoredUser(
    val account: Account,
    val passwordHash: String,
)

data class NewAttachment(
    val id: UUID,
    val originalName: String,
    val storedName: String,
    val contentType: String,
    val sizeBytes: Long,
)

data class StoredAttachment(
    val attachment: Attachment,
    val storedName: String,
    val channelId: UUID,
)

interface UserRepository {
    fun insertStudent(id: UUID, email: String, passwordHash: String, displayName: String): Account
    fun insertTeacher(id: UUID, email: String, passwordHash: String, displayName: String, department: String): Account
    fun findByEmail(email: String): StoredUser?
    fun findById(id: UUID): StoredUser?
    fun listByRole(role: Role, includeDeleted: Boolean): List<Account>
}

interface ChannelRepository {
    fun createGroup(name: String, description: String?, owner: Account): ChannelSummary
    fun findDirect(first: UUID, second: UUID): ChannelSummary?
    fun createDirect(owner: Account, other: Account): ChannelSummary
    fun listFor(userId: UUID): List<ChannelSummary>
    fun findChannel(id: UUID): ChannelSummary?
    fun isMember(channelId: UUID, userId: UUID): Boolean
}

interface MessageRepository {
    fun append(
        channelId: UUID,
        author: Account,
        body: String,
        attachment: NewAttachment?,
        studentItem: Boolean,
    ): Message

    fun history(channelId: UUID, limit: Int): List<Message>
    fun findMessage(id: UUID): Message?
    fun edit(id: UUID, body: String): Message
    fun softDelete(id: UUID)
    fun hardDelete(id: UUID): List<String>
    fun attachment(id: UUID): StoredAttachment?
    fun studentItems(studentId: UUID): List<StudentItem>
}
