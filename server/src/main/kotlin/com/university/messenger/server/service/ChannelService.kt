package com.university.messenger.server.service

import com.university.messenger.domain.model.Account
import com.university.messenger.domain.model.ChannelSummary
import com.university.messenger.domain.model.Role
import com.university.messenger.server.AppException
import com.university.messenger.server.persistence.ChannelRepository
import com.university.messenger.server.persistence.UserRepository
import java.util.UUID

class ChannelService(
    private val channels: ChannelRepository,
    private val users: UserRepository,
) {
    fun list(actor: Account): List<ChannelSummary> {
        actor.ensureActive()
        return channels.listFor(actor.id)
    }

    fun listTeachers(actor: Account): List<Account> {
        actor.ensureActive()
        return users.listByRole(Role.TEACHER, includeDeleted = false)
    }

    fun createGroup(actor: Account, name: String, description: String?): ChannelSummary {
        actor.ensureActive()
        val trimmed = name.trim()
        if (trimmed.length !in 2..120) {
            throw AppException.BadRequest("Channel name must be 2 to 120 characters")
        }
        val about = description?.trim()?.ifEmpty { null }
        if (about != null && about.length > 500) {
            throw AppException.BadRequest("Description can be at most 500 characters")
        }
        return channels.createGroup(trimmed, about, actor)
    }

    fun openDirect(actor: Account, otherId: UUID): ChannelSummary {
        actor.ensureActive()
        if (otherId == actor.id) throw AppException.BadRequest("You cannot message yourself")
        val other = users.findById(otherId)?.account ?: throw AppException.NotFound("User not found")
        if (!actor.canOpenDirectWith(other)) {
            throw AppException.Forbidden("A student can only message a teacher directly")
        }
        val existing = channels.findDirect(actor.id, other.id)
        if (existing != null) return existing.copy(title = other.displayName)
        return channels.createDirect(actor, other)
    }
}

internal fun Account.ensureActive() {
    if (isDeleted()) throw AppException.Unauthorized("Account is disabled")
}
