package com.university.messenger.domain.model

import java.time.Instant
import java.util.UUID

/**
 * One abstract account and two roles.
 * Kotlin has no multiple class inheritance, so a student also implements
 * [SelfRegistered] and a teacher also implements [StaffMember].
 */
abstract class Account(
    override val id: UUID,
    val email: String,
    val displayName: String,
    override val deletedAt: Instant?,
) : Identifiable, SoftDeletable {
    abstract val role: Role

    abstract fun canOpenDirectWith(other: Account): Boolean

    fun origin(): String = if (this is SelfRegistered) "SELF" else "STAFF"
}

class StudentAccount(
    id: UUID,
    email: String,
    displayName: String,
    deletedAt: Instant? = null,
) : Account(id, email, displayName, deletedAt), SelfRegistered {
    override val role: Role = Role.STUDENT

    override fun canOpenDirectWith(other: Account): Boolean {
        return other is TeacherAccount && other.id != id && !other.isDeleted()
    }
}

class TeacherAccount(
    id: UUID,
    email: String,
    displayName: String,
    override val department: String,
    deletedAt: Instant? = null,
) : Account(id, email, displayName, deletedAt), StaffMember {
    override val role: Role = Role.TEACHER

    override fun canOpenDirectWith(other: Account): Boolean {
        return other.id != id && !other.isDeleted()
    }
}

object AccountFactory {
    fun restore(
        role: Role,
        id: UUID,
        email: String,
        displayName: String,
        department: String?,
        deletedAt: Instant?,
    ): Account = when (role) {
        Role.STUDENT -> StudentAccount(id, email, displayName, deletedAt)
        Role.TEACHER -> TeacherAccount(
            id = id,
            email = email,
            displayName = displayName,
            department = department ?: "—",
            deletedAt = deletedAt,
        )
    }
}
