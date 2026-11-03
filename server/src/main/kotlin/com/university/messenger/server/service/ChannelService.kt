package com.university.messenger.server.service

import com.university.messenger.domain.model.Account
import com.university.messenger.domain.model.ChannelKind
import com.university.messenger.domain.policy.RegistrationRules
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

    fun addMember(actor: Account, channelId: UUID, email: String) {
        actor.ensureActive()
        val channel = channels.findChannel(channelId)?.takeUnless { it.isDeleted() }
            ?: throw AppException.NotFound("Channel not found")
        if (!channels.isMember(channelId, actor.id)) throw AppException.Forbidden("Not allowed")
        if (channel.kind != ChannelKind.GROUP || channel.createdBy != actor.id) {
            throw AppException.Forbidden("Only the group owner can add members")
        }
        if (!RegistrationRules.isValidEmail(email)) throw AppException.BadRequest("Invalid email")
        val member = users.findByEmail(email.trim().lowercase())?.account?.takeUnless { it.isDeleted() }
            ?: throw AppException.NotFound("User not found")
        channels.addMember(channelId, member.id)
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
