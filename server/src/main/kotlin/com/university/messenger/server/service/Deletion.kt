package com.university.messenger.server.service

import com.university.messenger.domain.model.DeletionMode
import com.university.messenger.server.persistence.MessageRepository
import com.university.messenger.server.storage.FileStorage
import java.util.UUID

interface DeletionStrategy {
    val mode: DeletionMode
    fun deleteMessage(id: UUID)
}

class SoftDeletionStrategy(
    private val messages: MessageRepository,
) : DeletionStrategy {
    override val mode: DeletionMode = DeletionMode.SOFT
    override fun deleteMessage(id: UUID) = messages.softDelete(id)
}

class HardDeletionStrategy(
    private val messages: MessageRepository,
    private val files: FileStorage,
) : DeletionStrategy {
    override val mode: DeletionMode = DeletionMode.HARD

    override fun deleteMessage(id: UUID) {
        messages.hardDelete(id).forEach(files::delete)
    }
}

class DeletionCoordinator(strategies: List<DeletionStrategy>) {
    private val byMode = strategies.associateBy { it.mode }

    fun deleteMessage(mode: DeletionMode, id: UUID) = select(mode).deleteMessage(id)

    private fun select(mode: DeletionMode): DeletionStrategy {
        return byMode[mode] ?: error("No deletion strategy for $mode")
    }
}
