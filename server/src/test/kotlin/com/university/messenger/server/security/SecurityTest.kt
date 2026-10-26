package com.university.messenger.server.security

import com.university.messenger.domain.model.StudentAccount
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SecurityTest {
    @Test
    fun bcryptHashIsNotTheRawPassword() {
        val hasher = BcryptPasswordHasher(cost = 4)
        val hash = hasher.hash("Haslo123")
        assertNotEquals("Haslo123", hash)
        assertTrue(hasher.verify("Haslo123", hash))
    }

    @Test
    fun jwtRoundTripReturnsTheSameUser() {
        val tokens = JwtTokenIssuer("test-secret")
        val student = StudentAccount(UUID.randomUUID(), "ola@uni.local", "Ola")
        val token = tokens.issue(student)
        assertEqualsId(student.id, tokens.userId(token))
    }

    private fun assertEqualsId(expected: UUID, actual: UUID) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual)
    }
}
