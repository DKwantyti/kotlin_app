package com.university.messenger.server.persistence

import com.university.messenger.domain.model.Account
import com.university.messenger.domain.model.ChannelKind
import com.university.messenger.domain.model.ChannelSummary
import com.university.messenger.domain.model.DirectChannelKey
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class PostgresChannelRepository : ChannelRepository {
    override fun createGroup(name: String, description: String?, owner: Account): ChannelSummary {
        val id = UUID.randomUUID()
        val now = Instant.now()
        DatabaseFactory.transaction { sql ->
            sql.update(
                """
                INSERT INTO channels (id, name, description, kind, created_by, created_at)
                VALUES (?, ?, ?, 'GROUP', ?, ?)
                """.trimIndent(),
                id,
                name,
                description,
                owner.id,
                now,
            )
            sql.update(
                "INSERT INTO channel_members (channel_id, user_id, joined_at) VALUES (?, ?, ?)",
                id,
                owner.id,
                now,
            )
        }
        return ChannelSummary(id, name, description, ChannelKind.GROUP, owner.id, null)
    }

    override fun findDirect(first: UUID, second: UUID): ChannelSummary? {
        return DatabaseFactory.transaction { sql ->
            sql.one(
                """
                SELECT id, name, description, kind, created_by, deleted_at
                FROM channels
                WHERE direct_key = ? AND deleted_at IS NULL
                """.trimIndent(),
                DirectChannelKey.of(first, second),
            ) { it.toChannel(it.getString("name")) }
        }
    }

    override fun createDirect(owner: Account, other: Account): ChannelSummary {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val key = DirectChannelKey.of(owner.id, other.id)
        try {
            DatabaseFactory.transaction { sql ->
                sql.update(
                    """
                    INSERT INTO channels (id, name, description, kind, direct_key, created_by, created_at)
                    VALUES (?, ?, NULL, 'DIRECT', ?, ?, ?)
                    """.trimIndent(),
                    id,
                    other.displayName,
                    key,
                    owner.id,
                    now,
                )
                sql.update(
                    "INSERT INTO channel_members (channel_id, user_id, joined_at) VALUES (?, ?, ?)",
                    id,
                    owner.id,
                    now,
                )
                sql.update(
                    "INSERT INTO channel_members (channel_id, user_id, joined_at) VALUES (?, ?, ?)",
                    id,
                    other.id,
                    now,
                )
            }
        } catch (error: SQLException) {
            if (error.isUniqueViolation()) {
                return findDirect(owner.id, other.id) ?: throw error
            }
            throw error
        }
        return ChannelSummary(id, other.displayName, null, ChannelKind.DIRECT, owner.id, null)
    }

    override fun listFor(userId: UUID): List<ChannelSummary> {
        return DatabaseFactory.transaction { sql ->
            sql.query(
                """
                SELECT c.id, c.description, c.kind, c.created_by, c.deleted_at,
                       CASE WHEN c.kind = 'DIRECT' THEN (
                           SELECT u.display_name
                           FROM channel_members cm
                           JOIN users u ON u.id = cm.user_id
                           WHERE cm.channel_id = c.id AND cm.user_id <> ?
                           LIMIT 1
                       ) ELSE c.name END AS title
                FROM channels c
                JOIN channel_members me ON me.channel_id = c.id AND me.user_id = ?
                WHERE c.deleted_at IS NULL
                ORDER BY c.created_at DESC
                """.trimIndent(),
                userId,
                userId,
            ) { result ->
                result.toChannel(result.getString("title"))
            }
        }
    }

    override fun findChannel(id: UUID): ChannelSummary? {
        return DatabaseFactory.transaction { sql ->
            sql.one(
                """
                SELECT id, name AS title, description, kind, created_by, deleted_at
                FROM channels
                WHERE id = ?
                """.trimIndent(),
                id,
            ) { it.toChannel(it.getString("title")) }
        }
    }

    override fun isMember(channelId: UUID, userId: UUID): Boolean {
        return DatabaseFactory.transaction { sql ->
            sql.one(
                "SELECT 1 AS member FROM channel_members WHERE channel_id = ? AND user_id = ?",
                channelId,
                userId,
            ) { true } ?: false
        }
    }
}

private fun ResultSet.toChannel(title: String): ChannelSummary {
    return ChannelSummary(
        id = uuid("id"),
        title = title,
        description = getString("description"),
        kind = ChannelKind.valueOf(getString("kind")),
        createdBy = uuid("created_by"),
        deletedAt = instant("deleted_at"),
    )
}
