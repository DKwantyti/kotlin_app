package com.university.messenger.server

import com.university.messenger.domain.model.Account
import com.university.messenger.server.security.PasswordHasher
import com.university.messenger.server.security.TokenIssuer
import com.university.messenger.server.AppException
import java.util.UUID

class PlainHasher : PasswordHasher {
    override fun hash(raw: String): String = "plain:$raw"

    override fun verify(raw: String, hash: String): Boolean = hash == "plain:$raw"
}

class MemoryTokens : TokenIssuer {
    override fun issue(account: Account): String = account.id.toString()

    override fun userId(token: String): UUID {
        return try {
            UUID.fromString(token)
        } catch (error: IllegalArgumentException) {
            throw AppException.Unauthorized("Invalid token")
        }
    }
}
