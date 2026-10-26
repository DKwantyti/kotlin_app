package com.university.messenger.server.service

import com.university.messenger.domain.model.Account
import com.university.messenger.domain.policy.RegistrationRules
import com.university.messenger.server.AppException
import com.university.messenger.server.persistence.UserRepository
import com.university.messenger.server.security.PasswordHasher
import com.university.messenger.server.security.TokenIssuer
import java.util.UUID

data class AuthResult(
    val token: String,
    val account: Account,
)

class AuthService(
    private val users: UserRepository,
    private val hasher: PasswordHasher,
    private val tokens: TokenIssuer,
) {
    fun registerStudent(email: String, password: String, displayName: String): AuthResult {
        val normalizedEmail = email.trim().lowercase()
        val name = displayName.trim()
        val errors = RegistrationRules.studentErrors(normalizedEmail, password, name)
        if (errors.isNotEmpty()) throw AppException.BadRequest(errors.joinToString(". "))
        if (users.findByEmail(normalizedEmail) != null) throw AppException.Conflict("Email is already taken")
        val account = users.insertStudent(UUID.randomUUID(), normalizedEmail, hasher.hash(password), name)
        return AuthResult(tokens.issue(account), account)
    }

    fun login(email: String, password: String): AuthResult {
        val stored = users.findByEmail(email.trim().lowercase())
            ?: throw AppException.Unauthorized("Invalid email or password")
        if (stored.account.isDeleted()) throw AppException.Unauthorized("Account is disabled")
        if (!hasher.verify(password, stored.passwordHash)) {
            throw AppException.Unauthorized("Invalid email or password")
        }
        return AuthResult(tokens.issue(stored.account), stored.account)
    }
}
