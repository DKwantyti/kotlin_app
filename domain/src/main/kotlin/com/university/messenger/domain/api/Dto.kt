package com.university.messenger.domain.api

import kotlinx.serialization.Serializable

@Serializable
data class RegisterStudentRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
)

@Serializable
data class PostMessageRequest(
    val body: String,
)

@Serializable
data class EditMessageRequest(
    val body: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String,
    val department: String? = null,
    val origin: String,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserResponse,
)

@Serializable
data class ChannelResponse(
    val id: String,
    val title: String,
    val kind: String,
    val description: String? = null,
)

@Serializable
data class AttachmentResponse(
    val id: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
)

@Serializable
data class MessageResponse(
    val id: String,
    val channelId: String,
    val authorId: String,
    val authorName: String,
    val body: String,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<AttachmentResponse>,
)
