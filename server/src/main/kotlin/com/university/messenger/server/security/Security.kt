package com.university.messenger.server.security

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.university.messenger.domain.model.Account
import com.university.messenger.server.AppException
import java.time.Instant
import java.util.Date
import java.util.UUID

interface PasswordHasher {
    fun hash(raw: String): String
    fun verify(raw: String, hash: String): Boolean
}

class BcryptPasswordHasher(private val cost: Int = 10) : PasswordHasher {
    override fun hash(raw: String): String = BCrypt.withDefaults().hashToString(cost, raw.toCharArray())

    override fun verify(raw: String, hash: String): Boolean {
        return BCrypt.verifyer().verify(raw.toCharArray(), hash).verified
    }
}

interface TokenIssuer {
    fun issue(account: Account): String
    fun userId(token: String): UUID
}

class JwtTokenIssuer(
    secret: String,
    private val ttlSeconds: Long = 7L * 24L * 60L * 60L,
) : TokenIssuer {
    private val algorithm = Algorithm.HMAC256(secret)

    override fun issue(account: Account): String {
        val expiresAt = Date.from(Instant.now().plusSeconds(ttlSeconds))
        return JWT.create()
            .withSubject(account.id.toString())
            .withClaim("role", account.role.name)
            .withExpiresAt(expiresAt)
            .sign(algorithm)
    }

    override fun userId(token: String): UUID {
        try {
            val decoded = JWT.require(algorithm).build().verify(token)
            return UUID.fromString(decoded.subject)
        } catch (error: Exception) {
            throw AppException.Unauthorized("Invalid token")
        }
    }
}
