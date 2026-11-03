package com.university.messenger.server.service

import com.university.messenger.domain.model.Account
import com.university.messenger.domain.policy.RegistrationRules
import com.university.messenger.server.AppException
import com.university.messenger.server.SeedData
import com.university.messenger.server.persistence.UserRepository
import com.university.messenger.server.persistence.TeacherInvite
import java.security.SecureRandom
import java.time.Instant
import java.time.Duration
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
    private val demoTeacherEnabled: Boolean = false,
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

    fun issueTeacherCode(email: String, department: String): String {
        val normalizedEmail = email.trim().lowercase()
        val name = department.trim()
        if (!RegistrationRules.isValidEmail(normalizedEmail)) throw AppException.BadRequest("Invalid email")
        if (name.length !in 2..120) throw AppException.BadRequest("Department must be 2 to 120 characters")
        if (users.findByEmail(normalizedEmail) != null) throw AppException.Conflict("Email is already taken")
        val code = secureRandom.nextInt(1_000_000).toString().padStart(6, '0')
        val expiresAt = Instant.now().plus(Duration.ofHours(24))
        users.saveTeacherInvite(TeacherInvite(normalizedEmail, name, hasher.hash(code), expiresAt))
        return code
    }

    fun registerTeacher(email: String, password: String, displayName: String, code: String): AuthResult {
        val normalizedEmail = email.trim().lowercase()
        val name = displayName.trim()
        val errors = RegistrationRules.studentErrors(normalizedEmail, password, name)
        if (errors.isNotEmpty()) throw AppException.BadRequest(errors.joinToString(". "))
        if (!code.matches(Regex("[0-9]{6}"))) throw AppException.BadRequest("Enter the six-digit dean's office code")
        val account = users.redeemTeacherInvite(UUID.randomUUID(), normalizedEmail, { hasher.hash(password) }, name) {
            hasher.verify(code, it)
        } ?: throw AppException.BadRequest("Code is invalid or expired. Ask the dean's office for a new code.")
        return AuthResult(tokens.issue(account), account)
    }

    fun login(email: String, password: String): AuthResult {
        val stored = users.findByEmail(email.trim().lowercase())
            ?: throw AppException.Unauthorized("Invalid email or password")
        ensureEnabled(stored.account)
        if (!hasher.verify(password, stored.passwordHash)) {
            throw AppException.Unauthorized("Invalid email or password")
        }
        return AuthResult(tokens.issue(stored.account), stored.account)
    }

    fun ensureEnabled(account: Account) {
        if (account.isDeleted() || (!demoTeacherEnabled && account.id == SeedData.teacherId)) {
            throw AppException.Unauthorized("Account is disabled")
        }
    }

    private companion object {
        val secureRandom = SecureRandom()
    }
}
