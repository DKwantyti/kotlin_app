package com.university.messenger.server.route

import com.university.messenger.domain.model.Account
import com.university.messenger.domain.model.DeletionMode
import com.university.messenger.server.AppContext
import com.university.messenger.server.AppException
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import java.util.UUID

fun ApplicationCall.account(context: AppContext): Account {
    val header = request.header(HttpHeaders.Authorization)
        ?: throw AppException.Unauthorized("Missing token")
    if (!header.startsWith("Bearer ")) throw AppException.Unauthorized("Missing token")
    val userId = context.tokens.userId(header.removePrefix("Bearer ").trim())
    val stored = context.users.findById(userId) ?: throw AppException.Unauthorized("Session expired")
    if (stored.account.isDeleted()) throw AppException.Unauthorized("Account is disabled")
    return stored.account
}

fun ApplicationCall.pathId(name: String): UUID {
    val raw = parameters[name] ?: throw AppException.BadRequest("Missing id")
    return try {
        UUID.fromString(raw)
    } catch (error: IllegalArgumentException) {
        throw AppException.BadRequest("Invalid id")
    }
}

fun ApplicationCall.deletionMode(): DeletionMode {
    return when (request.queryParameters["mode"]?.lowercase() ?: "soft") {
        "soft" -> DeletionMode.SOFT
        "hard" -> DeletionMode.HARD
        else -> throw AppException.BadRequest("mode must be soft or hard")
    }
}
