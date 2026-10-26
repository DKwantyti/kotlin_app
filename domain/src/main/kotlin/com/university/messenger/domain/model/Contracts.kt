package com.university.messenger.domain.model

import java.time.Instant
import java.util.UUID

interface Identifiable {
    val id: UUID
}

interface SoftDeletable {
    val deletedAt: Instant?

    fun isDeleted(): Boolean = deletedAt != null
}

/** Account created by the person themselves (a student). */
interface SelfRegistered

/** Teacher account, created outside student self-registration. */
interface StaffMember {
    val department: String?
}
