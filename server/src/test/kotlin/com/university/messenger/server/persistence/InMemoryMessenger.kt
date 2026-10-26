package com.university.messenger.server.persistence

import com.university.messenger.domain.model.Account
import com.university.messenger.domain.model.AccountFactory
import com.university.messenger.domain.model.Attachment
import com.university.messenger.domain.model.ChannelKind
import com.university.messenger.domain.model.ChannelSummary
import com.university.messenger.domain.model.DirectChannelKey
import com.university.messenger.domain.model.Message
import com.university.messenger.domain.model.Role
import com.university.messenger.domain.model.StudentItem
import com.university.messenger.server.AppException
import java.time.Instant
import java.util.UUID

/** Test double that keeps the relational rules in memory. */
class InMemoryMessenger : UserRepository, ChannelRepository, MessageRepository {
    private data class UserRecord(
        val id: UUID,
        val email: String,
        var passwordHash: String,
        var displayName: String,
        val role: Role,
        val department: String?,
        var deletedAt: Instant?,
    ) {
        fun account(): Account = AccountFactory.restore(role, id, email, displayName, department, deletedAt)
    }

    private data class ChannelRecord(
        val id: UUID,
        val name: String,
        val description: String?,
        val kind: ChannelKind,
        val createdBy: UUID,
        val directKey: String?,
        var deletedAt: Instant?,
    )

    private val users = linkedMapOf<UUID, UserRecord>()
    private val channels = linkedMapOf<UUID, ChannelRecord>()
    private val members = mutableMapOf<UUID, MutableSet<UUID>>()
    private val messages = mutableListOf<Message>()
    private val storedNames = mutableMapOf<UUID, String>()
    private val items = mutableListOf<StudentItem>()

    override fun insertStudent(id: UUID, email: String, passwordHash: String, displayName: String): Account {
        return insert(id, email, passwordHash, displayName, Role.STUDENT, null)
    }

    override fun insertTeacher(
        id: UUID,
        email: String,
        passwordHash: String,
        displayName: String,
        department: String,
    ): Account = insert(id, email, passwordHash, displayName, Role.TEACHER, department)

    override fun findByEmail(email: String): StoredUser? {
        val record = users.values.find { it.email == email.lowercase() } ?: return null
        return StoredUser(record.account(), record.passwordHash)
    }

    override fun findById(id: UUID): StoredUser? {
        val record = users[id] ?: return null
        return StoredUser(record.account(), record.passwordHash)
    }

    override fun listByRole(role: Role, includeDeleted: Boolean): List<Account> {
        return users.values
            .filter { it.role == role && (includeDeleted || it.deletedAt == null) }
            .map { it.account() }
    }

    override fun createGroup(name: String, description: String?, owner: Account): ChannelSummary {
        val id = UUID.randomUUID()
        channels[id] = ChannelRecord(id, name, description, ChannelKind.GROUP, owner.id, null, null)
        members[id] = mutableSetOf(owner.id)
        return summary(channels.getValue(id), name)
    }

    override fun findDirect(first: UUID, second: UUID): ChannelSummary? {
        val key = DirectChannelKey.of(first, second)
        val record = channels.values.find { it.directKey == key && it.deletedAt == null } ?: return null
        return summary(record, record.name)
    }

    override fun createDirect(owner: Account, other: Account): ChannelSummary {
        val id = UUID.randomUUID()
        val record = ChannelRecord(
            id = id,
            name = other.displayName,
            description = null,
            kind = ChannelKind.DIRECT,
            createdBy = owner.id,
            directKey = DirectChannelKey.of(owner.id, other.id),
            deletedAt = null,
        )
        channels[id] = record
        members[id] = mutableSetOf(owner.id, other.id)
        return summary(record, other.displayName)
    }

    override fun listFor(userId: UUID): List<ChannelSummary> {
        return channels.values
            .filter { it.deletedAt == null && members[it.id]?.contains(userId) == true }
            .map { record ->
                val title = if (record.kind == ChannelKind.DIRECT) peerName(record.id, userId) else record.name
                summary(record, title)
            }
    }

    override fun findChannel(id: UUID): ChannelSummary? {
        val record = channels[id] ?: return null
        return summary(record, record.name)
    }

    override fun isMember(channelId: UUID, userId: UUID): Boolean {
        return members[channelId]?.contains(userId) == true
    }

    override fun append(
        channelId: UUID,
        author: Account,
        body: String,
        attachment: NewAttachment?,
        studentItem: Boolean,
    ): Message {
        val now = Instant.now()
        val messageId = UUID.randomUUID()
        val attachments = if (attachment == null) {
            emptyList()
        } else {
            storedNames[attachment.id] = attachment.storedName
            listOf(
                Attachment(
                    id = attachment.id,
                    messageId = messageId,
                    originalName = attachment.originalName,
                    contentType = attachment.contentType,
                    sizeBytes = attachment.sizeBytes,
                    deletedAt = null,
                ),
            )
        }
        if (attachment != null && studentItem) {
            items += StudentItem(UUID.randomUUID(), author.id, attachment.id, attachment.originalName, now, null)
        }
        val message = Message(
            id = messageId,
            channelId = channelId,
            authorId = author.id,
            authorName = author.displayName,
            body = body,
            createdAt = now,
            updatedAt = now,
            attachments = attachments,
            deletedAt = null,
        )
        messages += message
        return named(message)
    }

    override fun history(channelId: UUID, limit: Int): List<Message> {
        return messages
            .filter { it.channelId == channelId && !it.isDeleted() }
            .sortedBy { it.createdAt }
            .takeLast(limit)
            .map { named(it) }
    }

    override fun findMessage(id: UUID): Message? = messages.find { it.id == id }?.let(::named)

    override fun edit(id: UUID, body: String): Message {
        val index = messages.indexOfFirst { it.id == id && !it.isDeleted() }
        if (index < 0) error("Edited message $id was not readable")
        val updated = messages[index].copy(body = body, updatedAt = Instant.now())
        messages[index] = updated
        return named(updated)
    }

    override fun softDelete(id: UUID) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) return
        val now = Instant.now()
        val current = messages[index]
        messages[index] = current.copy(
            deletedAt = now,
            updatedAt = now,
            attachments = current.attachments.map { it.copy(deletedAt = now) },
        )
        for (indexItem in items.indices) {
            val item = items[indexItem]
            if (current.attachments.any { it.id == item.attachmentId }) {
                items[indexItem] = item.copy(deletedAt = now)
            }
        }
    }

    override fun hardDelete(id: UUID): List<String> {
        val message = messages.find { it.id == id } ?: return emptyList()
        val names = message.attachments.mapNotNull { storedNames.remove(it.id) }
        messages.removeAll { it.id == id }
        items.removeAll { item -> message.attachments.any { it.id == item.attachmentId } }
        return names
    }

    override fun attachment(id: UUID): StoredAttachment? {
        val message = messages.find { message -> message.attachments.any { it.id == id } } ?: return null
        if (message.isDeleted()) return null
        val attachment = message.attachments.find { it.id == id } ?: return null
        if (attachment.isDeleted()) return null
        val storedName = storedNames[id] ?: return null
        return StoredAttachment(attachment, storedName, message.channelId)
    }

    override fun studentItems(studentId: UUID): List<StudentItem> {
        return items.filter { it.studentId == studentId && !it.isDeleted() }.sortedByDescending { it.createdAt }
    }

    private fun insert(
        id: UUID,
        email: String,
        passwordHash: String,
        displayName: String,
        role: Role,
        department: String?,
    ): Account {
        if (users.values.any { it.email == email.lowercase() }) {
            throw AppException.Conflict("Email is already taken")
        }
        val record = UserRecord(id, email.lowercase(), passwordHash, displayName, role, department, null)
        users[id] = record
        return record.account()
    }

    private fun summary(record: ChannelRecord, title: String): ChannelSummary {
        return ChannelSummary(record.id, title, record.description, record.kind, record.createdBy, record.deletedAt)
    }

    private fun peerName(channelId: UUID, userId: UUID): String {
        val peerId = members[channelId]?.firstOrNull { it != userId } ?: return "Czat"
        return users[peerId]?.displayName ?: "Czat"
    }

    private fun named(message: Message): Message {
        val name = users[message.authorId]?.displayName ?: message.authorName
        return message.copy(
            authorName = name,
            attachments = message.attachments.filterNot { it.isDeleted() },
        )
    }
}
