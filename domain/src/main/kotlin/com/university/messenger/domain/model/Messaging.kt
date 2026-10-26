package com.university.messenger.domain.model

import java.time.Instant
import java.util.UUID

object DirectChannelKey {
    fun of(first: UUID, second: UUID): String {
        val left = first.toString()
        val right = second.toString()
        return if (left < right) "$left:$right" else "$right:$left"
    }
}

data class ChannelSummary(
    override val id: UUID,
    val title: String,
    val description: String?,
    val kind: ChannelKind,
    val createdBy: UUID,
    override val deletedAt: Instant?,
) : Identifiable, SoftDeletable

data class Attachment(
    override val id: UUID,
    val messageId: UUID,
    val originalName: String,
    val contentType: String,
    val sizeBytes: Long,
    override val deletedAt: Instant?,
) : Identifiable, SoftDeletable

data class Message(
    override val id: UUID,
    val channelId: UUID,
    val authorId: UUID,
    val authorName: String,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val attachments: List<Attachment>,
    override val deletedAt: Instant?,
) : Identifiable, SoftDeletable

data class StudentItem(
    override val id: UUID,
    val studentId: UUID,
    val attachmentId: UUID,
    val title: String,
    val createdAt: Instant,
    override val deletedAt: Instant?,
) : Identifiable, SoftDeletable
