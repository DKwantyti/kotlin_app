package com.university.messenger.server.persistence

import com.university.messenger.domain.model.Account
import com.university.messenger.domain.model.Attachment
import com.university.messenger.domain.model.Message
import com.university.messenger.domain.model.StudentItem
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

class PostgresMessageRepository : MessageRepository {
    override fun append(
        channelId: UUID,
        author: Account,
        body: String,
        attachment: NewAttachment?,
        studentItem: Boolean,
    ): Message {
        val messageId = UUID.randomUUID()
        val now = Instant.now()
        DatabaseFactory.transaction { sql ->
            sql.update(
                """
                INSERT INTO messages (id, channel_id, author_id, body, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                messageId,
                channelId,
                author.id,
                body,
                now,
                now,
            )
            if (attachment != null) {
                sql.update(
                    """
                    INSERT INTO attachments (
                        id, message_id, uploader_id, original_name, stored_name, content_type, size_bytes, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    attachment.id,
                    messageId,
                    author.id,
                    attachment.originalName,
                    attachment.storedName,
                    attachment.contentType,
                    attachment.sizeBytes,
                    now,
                )
                if (studentItem) {
                    sql.update(
                        """
                        INSERT INTO student_items (id, student_id, attachment_id, title, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        """.trimIndent(),
                        UUID.randomUUID(),
                        author.id,
                        attachment.id,
                        attachment.originalName,
                        now,
                    )
                }
            }
        }
        return findMessage(messageId) ?: error("Saved message $messageId was not readable")
    }

    override fun history(channelId: UUID, limit: Int): List<Message> {
        return DatabaseFactory.transaction { sql ->
            val headers = sql.query(
                """
                SELECT m.id, m.channel_id, m.author_id, u.display_name, m.body,
                       m.created_at, m.updated_at, m.deleted_at
                FROM messages m
                JOIN users u ON u.id = m.author_id
                WHERE m.channel_id = ? AND m.deleted_at IS NULL
                ORDER BY m.created_at DESC
                LIMIT ?
                """.trimIndent(),
                channelId,
                limit,
            ) { it.toHeader() }.asReversed()
            val attachments = loadAttachments(sql, headers.map { it.id })
            headers.map { it.toMessage(attachments[it.id].orEmpty()) }
        }
    }

    override fun findMessage(id: UUID): Message? {
        return DatabaseFactory.transaction { sql ->
            val header = sql.one(
                """
                SELECT m.id, m.channel_id, m.author_id, u.display_name, m.body,
                       m.created_at, m.updated_at, m.deleted_at
                FROM messages m
                JOIN users u ON u.id = m.author_id
                WHERE m.id = ?
                """.trimIndent(),
                id,
            ) { it.toHeader() } ?: return@transaction null
            header.toMessage(loadAttachments(sql, listOf(id))[id].orEmpty())
        }
    }

    override fun edit(id: UUID, body: String): Message {
        DatabaseFactory.transaction { sql ->
            sql.update(
                "UPDATE messages SET body = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL",
                body,
                Instant.now(),
                id,
            )
        }
        return findMessage(id) ?: error("Edited message $id was not readable")
    }

    override fun softDelete(id: UUID) {
        val now = Instant.now()
        DatabaseFactory.transaction { sql ->
            sql.update(
                "UPDATE messages SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL",
                now,
                now,
                id,
            )
            sql.update(
                "UPDATE attachments SET deleted_at = ? WHERE message_id = ? AND deleted_at IS NULL",
                now,
                id,
            )
            sql.update(
                """
                UPDATE student_items
                SET deleted_at = ?
                WHERE deleted_at IS NULL
                  AND attachment_id IN (SELECT id FROM attachments WHERE message_id = ?)
                """.trimIndent(),
                now,
                id,
            )
        }
    }

    override fun hardDelete(id: UUID): List<String> {
        return DatabaseFactory.transaction { sql ->
            val names = sql.query(
                "SELECT stored_name FROM attachments WHERE message_id = ?",
                id,
            ) { it.getString("stored_name") }
            sql.update("DELETE FROM messages WHERE id = ?", id)
            names
        }
    }

    override fun attachment(id: UUID): StoredAttachment? {
        return DatabaseFactory.transaction { sql ->
            sql.one(
                """
                SELECT a.id, a.message_id, a.original_name, a.content_type, a.size_bytes,
                       a.deleted_at, a.stored_name, m.channel_id
                FROM attachments a
                JOIN messages m ON m.id = a.message_id
                WHERE a.id = ? AND a.deleted_at IS NULL AND m.deleted_at IS NULL
                """.trimIndent(),
                id,
            ) { result ->
                StoredAttachment(
                    attachment = result.toAttachment(),
                    storedName = result.getString("stored_name"),
                    channelId = result.uuid("channel_id"),
                )
            }
        }
    }

    override fun studentItems(studentId: UUID): List<StudentItem> {
        return DatabaseFactory.transaction { sql ->
            sql.query(
                """
                SELECT id, student_id, attachment_id, title, created_at, deleted_at
                FROM student_items
                WHERE student_id = ? AND deleted_at IS NULL
                ORDER BY created_at DESC
                """.trimIndent(),
                studentId,
            ) { result ->
                StudentItem(
                    id = result.uuid("id"),
                    studentId = result.uuid("student_id"),
                    attachmentId = result.uuid("attachment_id"),
                    title = result.getString("title"),
                    createdAt = result.instant("created_at") ?: Instant.EPOCH,
                    deletedAt = result.instant("deleted_at"),
                )
            }
        }
    }

    private fun loadAttachments(sql: Sql, messageIds: List<UUID>): Map<UUID, List<Attachment>> {
        if (messageIds.isEmpty()) return emptyMap()
        val placeholders = messageIds.joinToString(",") { "?" }
        return sql.query(
            """
            SELECT id, message_id, original_name, content_type, size_bytes, deleted_at
            FROM attachments
            WHERE deleted_at IS NULL AND message_id IN ($placeholders)
            """.trimIndent(),
            *messageIds.toTypedArray(),
        ) { it.toAttachment() }.groupBy { it.messageId }
    }
}

private data class MessageHeader(
    val id: UUID,
    val channelId: UUID,
    val authorId: UUID,
    val authorName: String,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
) {
    fun toMessage(attachments: List<Attachment>): Message {
        return Message(id, channelId, authorId, authorName, body, createdAt, updatedAt, attachments, deletedAt)
    }
}

private fun ResultSet.toHeader(): MessageHeader {
    return MessageHeader(
        id = uuid("id"),
        channelId = uuid("channel_id"),
        authorId = uuid("author_id"),
        authorName = getString("display_name"),
        body = getString("body"),
        createdAt = instant("created_at") ?: Instant.EPOCH,
        updatedAt = instant("updated_at") ?: Instant.EPOCH,
        deletedAt = instant("deleted_at"),
    )
}

private fun ResultSet.toAttachment(): Attachment {
    return Attachment(
        id = uuid("id"),
        messageId = uuid("message_id"),
        originalName = getString("original_name"),
        contentType = getString("content_type"),
        sizeBytes = getLong("size_bytes"),
        deletedAt = instant("deleted_at"),
    )
}
