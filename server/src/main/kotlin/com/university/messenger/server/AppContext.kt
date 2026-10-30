package com.university.messenger.server

import com.university.messenger.domain.model.Account
import com.university.messenger.domain.model.StaffMember
import com.university.messenger.server.persistence.ChannelRepository
import com.university.messenger.server.persistence.MessageRepository
import com.university.messenger.server.persistence.UserRepository
import com.university.messenger.server.security.BcryptPasswordHasher
import com.university.messenger.server.security.JwtTokenIssuer
import com.university.messenger.server.security.PasswordHasher
import com.university.messenger.server.security.TokenIssuer
import com.university.messenger.server.service.AuthService
import com.university.messenger.server.service.ChannelService
import com.university.messenger.server.service.DeletionCoordinator
import com.university.messenger.server.service.HardDeletionStrategy
import com.university.messenger.server.service.MessageService
import com.university.messenger.server.service.SoftDeletionStrategy
import com.university.messenger.server.realtime.ChannelLive
import com.university.messenger.server.config.AppConfig
import com.university.messenger.server.persistence.PostgresChannelRepository
import com.university.messenger.server.persistence.PostgresMessageRepository
import com.university.messenger.server.persistence.PostgresUserRepository
import com.university.messenger.server.persistence.SchemaMigrator
import com.university.messenger.server.persistence.DatabaseFactory
import com.university.messenger.server.storage.LocalFileStorage
import com.university.messenger.domain.api.AttachmentResponse
import com.university.messenger.domain.api.ChannelResponse
import com.university.messenger.domain.api.MessageResponse
import com.university.messenger.domain.api.UserResponse
import com.university.messenger.domain.model.ChannelSummary
import com.university.messenger.domain.model.Message
import java.util.UUID

class AppContext(
    val users: UserRepository,
    val tokens: TokenIssuer,
    val auth: AuthService,
    val channels: ChannelService,
    val messages: MessageService,
    val live: ChannelLive,
)

fun buildContext(
    users: UserRepository,
    channels: ChannelRepository,
    messages: MessageRepository,
    files: com.university.messenger.server.storage.FileStorage,
    hasher: PasswordHasher,
    tokens: TokenIssuer,
): AppContext {
    val deletion = DeletionCoordinator(
        listOf(
            SoftDeletionStrategy(messages),
            HardDeletionStrategy(messages, files),
        ),
    )
    return AppContext(
        users = users,
        tokens = tokens,
        auth = AuthService(users, hasher, tokens),
        channels = ChannelService(channels, users),
        messages = MessageService(messages, channels, files, deletion),
        live = ChannelLive(),
    )
}

fun productionContext(config: AppConfig): AppContext {
    DatabaseFactory.init(config.database)
    SchemaMigrator.run()
    val users = PostgresUserRepository()
    val hasher = BcryptPasswordHasher()
    SeedData.run(users, hasher)
    return buildContext(
        users = users,
        channels = PostgresChannelRepository(),
        messages = PostgresMessageRepository(),
        files = LocalFileStorage(config.uploadDir),
        hasher = hasher,
        tokens = JwtTokenIssuer(config.jwtSecret),
    )
}

object SeedData {
    val teacherId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

    fun run(users: UserRepository, hasher: PasswordHasher) {
        if (users.findByEmail("teacher@uni.local") == null) {
            users.insertTeacher(
                teacherId,
                "teacher@uni.local",
                hasher.hash("Teacher12345"),
                "Jan Kowalski",
                "Computer science",
            )
        }
    }
}

fun Account.toResponse(): UserResponse {
    return UserResponse(
        id = id.toString(),
        email = email,
        displayName = displayName,
        role = role.name,
        department = (this as? StaffMember)?.department,
        origin = origin(),
    )
}

fun ChannelSummary.toResponse(): ChannelResponse {
    return ChannelResponse(
        id = id.toString(),
        title = title,
        kind = kind.name,
        description = description,
    )
}

fun Message.toResponse(): MessageResponse {
    return MessageResponse(
        id = id.toString(),
        channelId = channelId.toString(),
        authorId = authorId.toString(),
        authorName = authorName,
        body = body,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
        attachments = attachments.filterNot { it.isDeleted() }.map { attachment ->
            AttachmentResponse(
                id = attachment.id.toString(),
                fileName = attachment.originalName,
                contentType = attachment.contentType,
                sizeBytes = attachment.sizeBytes,
            )
        },
    )
}
