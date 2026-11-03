package com.university.messenger.server.service

import com.university.messenger.domain.api.messageReactionEmoji
import com.university.messenger.domain.model.Account
import com.university.messenger.domain.model.DeletionMode
import com.university.messenger.domain.model.Message
import com.university.messenger.domain.model.StudentAccount
import com.university.messenger.domain.policy.FileUploadPolicy
import com.university.messenger.domain.policy.UploadDecision
import com.university.messenger.server.AppException
import com.university.messenger.server.persistence.ChannelRepository
import com.university.messenger.server.persistence.MessageRepository
import com.university.messenger.server.persistence.NewAttachment
import com.university.messenger.server.storage.FileStorage
import java.io.InputStream
import java.util.UUID

data class OpenedFile(
    val fileName: String,
    val contentType: String,
    val stream: InputStream,
)

class MessageService(
    private val messages: MessageRepository,
    private val channels: ChannelRepository,
    private val files: FileStorage,
    private val deletion: DeletionCoordinator,
    private val policy: FileUploadPolicy = FileUploadPolicy(),
) {
    fun history(actor: Account, channelId: UUID, limit: Int, query: String = ""): List<Message> {
        actor.ensureActive()
        requireMember(actor, channelId)
        val term = query.trim()
        if (term.length > 120) throw AppException.BadRequest("Search can be at most 120 characters")
        return messages.history(channelId, limit.coerceIn(1, 200), term)
    }

    fun post(actor: Account, channelId: UUID, body: String, replyToId: String? = null): Message {
        actor.ensureActive()
        requireMember(actor, channelId)
        val trimmed = body.trim()
        if (trimmed.isEmpty()) throw AppException.BadRequest("Message cannot be empty")
        if (trimmed.length > MAX_BODY) throw AppException.BadRequest("Message can be at most 4000 characters")
        return messages.append(channelId, actor, trimmed, null, false, replyTarget(channelId, replyToId))
    }

    fun react(actor: Account, messageId: UUID, emoji: String?): Message {
        actor.ensureActive()
        val message = visibleMessage(messageId)
        requireMember(actor, message.channelId)
        if (emoji != null && emoji !in messageReactionEmoji) throw AppException.BadRequest("Invalid reaction")
        return messages.setReaction(messageId, actor.id, emoji)
            ?: throw AppException.NotFound("Message not found")
    }

    private fun replyTarget(channelId: UUID, rawId: String?): UUID? {
        if (rawId == null) return null
        val id = runCatching { UUID.fromString(rawId) }.getOrNull()
            ?: throw AppException.BadRequest("Invalid reply target")
        val target = visibleMessage(id)
        if (target.channelId != channelId) throw AppException.NotFound("Message not found")
        return id
    }

    fun edit(actor: Account, messageId: UUID, body: String): Message {
        actor.ensureActive()
        val message = visibleMessage(messageId)
        if (message.authorId != actor.id) throw AppException.Forbidden("You can only edit your own messages")
        requireMember(actor, message.channelId)
        val trimmed = body.trim()
        if (trimmed.isEmpty() && message.attachments.isEmpty()) {
            throw AppException.BadRequest("Message cannot be empty")
        }
        if (trimmed.length > MAX_BODY) throw AppException.BadRequest("Message can be at most 4000 characters")
        return messages.edit(messageId, trimmed)
    }

    fun delete(actor: Account, messageId: UUID, mode: DeletionMode): UUID? {
        actor.ensureActive()
        val message = messages.findMessage(messageId) ?: throw AppException.NotFound("Message not found")
        if (message.authorId != actor.id) throw AppException.Forbidden("Not allowed")
        if (message.isDeleted() && mode == DeletionMode.SOFT) return null
        deletion.deleteMessage(mode, messageId)
        return message.channelId
    }

    fun ensureVisible(actor: Account, channelId: UUID) {
        actor.ensureActive()
        requireMember(actor, channelId)
    }

    fun upload(
        actor: Account,
        channelId: UUID,
        fileName: String,
        sizeHint: Long?,
        body: String,
        source: InputStream,
        replyToId: String? = null,
    ): Message {
        actor.ensureActive()
        requireMember(actor, channelId)
        val replyId = replyTarget(channelId, replyToId)
        val knownSize = sizeHint?.takeIf { it > 0L }
        rejectIfNeeded(policy.evaluate(fileName, knownSize))
        val trimmed = body.trim()
        if (trimmed.length > MAX_BODY) throw AppException.BadRequest("Message can be at most 4000 characters")
        val blob = files.save(source, policyMax())
        try {
            rejectIfNeeded(policy.evaluate(fileName, blob.sizeBytes))
            val attachment = NewAttachment(
                id = UUID.randomUUID(),
                originalName = fileName.trim(),
                storedName = blob.storedName,
                contentType = contentTypeFor(fileName),
                sizeBytes = blob.sizeBytes,
            )
            return messages.append(
                channelId = channelId,
                author = actor,
                body = trimmed,
                attachment = attachment,
                studentItem = actor is StudentAccount,
                replyToId = replyId,
            )
        } catch (error: Exception) {
            files.delete(blob.storedName)
            throw error
        }
    }

    fun open(actor: Account, attachmentId: UUID): OpenedFile {
        actor.ensureActive()
        val stored = messages.attachment(attachmentId) ?: throw AppException.NotFound("File not found")
        requireMember(actor, stored.channelId)
        return OpenedFile(
            fileName = stored.attachment.originalName,
            contentType = stored.attachment.contentType,
            stream = files.open(stored.storedName),
        )
    }

    private fun visibleMessage(messageId: UUID): Message {
        val message = messages.findMessage(messageId) ?: throw AppException.NotFound("Message not found")
        if (message.isDeleted()) throw AppException.NotFound("Message not found")
        return message
    }

    private fun requireMember(actor: Account, channelId: UUID) {
        val channel = channels.findChannel(channelId) ?: throw AppException.NotFound("Channel not found")
        if (channel.isDeleted()) throw AppException.NotFound("Channel not found")
        if (!channels.isMember(channelId, actor.id)) throw AppException.Forbidden("Not allowed")
    }

    private fun rejectIfNeeded(decision: UploadDecision) {
        if (decision is UploadDecision.Rejected) throw AppException.BadRequest(decision.reason)
    }

    private fun policyMax(): Long = FileUploadPolicy.MAX_BYTES

    private fun contentTypeFor(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private companion object {
        const val MAX_BODY = 4000
    }
}
