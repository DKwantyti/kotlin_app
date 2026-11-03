package com.university.messenger.server.persistence

import com.university.messenger.domain.model.Account
import com.university.messenger.domain.model.Attachment
import com.university.messenger.domain.model.ChannelSummary
import com.university.messenger.domain.model.Message
import com.university.messenger.domain.model.Role
import com.university.messenger.domain.model.StudentItem
import java.time.Instant
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

data class TeacherInvite(
    val email: String,
    val department: String,
    val codeHash: String,
    val expiresAt: Instant,
    val attemptsLeft: Int = 5,
)

interface UserRepository {
    fun saveTeacherInvite(invite: TeacherInvite)
    fun redeemTeacherInvite(
        id: UUID,
        email: String,
        hashPassword: () -> String,
        displayName: String,
        verify: (String) -> Boolean,
    ): Account?

    fun insertStudent(id: UUID, email: String, passwordHash: String, displayName: String): Account
    fun insertTeacher(id: UUID, email: String, passwordHash: String, displayName: String, department: String): Account
    fun updateDisplayName(id: UUID, name: String): Account?
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
    fun addMember(channelId: UUID, userId: UUID)
}

interface MessageRepository {
    fun append(
        channelId: UUID,
        author: Account,
        body: String,
        attachment: NewAttachment?,
        studentItem: Boolean,
        replyToId: UUID? = null,
    ): Message

    fun setReaction(id: UUID, userId: UUID, emoji: String?): Message?

    fun history(channelId: UUID, limit: Int, query: String = ""): List<Message>
    fun findMessage(id: UUID): Message?
    fun edit(id: UUID, body: String): Message
    fun softDelete(id: UUID)
    fun hardDelete(id: UUID): List<String>
    fun attachment(id: UUID): StoredAttachment?
    fun studentItems(studentId: UUID): List<StudentItem>
}
