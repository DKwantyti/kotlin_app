package com.university.messenger.domain.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class RegistrationRulesTest {
    @Test
    fun acceptsStudentRegistration() {
        assertTrue(RegistrationRules.studentErrors("anna@uni.local", "Haslo123", "Anna Nowak").isEmpty())
    }

    @Test
    fun validatesNameBoundariesAndTrimsSpaces() {
        assertFalse(RegistrationRules.isValidDisplayName(" A "))
        assertTrue(RegistrationRules.isValidDisplayName(" Ab "))
        assertTrue(RegistrationRules.isValidDisplayName("a".repeat(120)))
        assertFalse(RegistrationRules.isValidDisplayName("a".repeat(121)))
    }

    @Test
    fun requiresBothPasswordCharacterTypesAndMinimumLength() {
        val passwordError = "Password must be at least 8 characters and include a letter and a digit"
        for (password in listOf("abcdef1", "abcdefgh", "12345678")) {
            assertEquals(listOf(passwordError), RegistrationRules.studentErrors("a@uni.local", password, "Anna"))
        }
        assertEquals(emptyList<String>(), RegistrationRules.studentErrors(" a@uni.local ", "abcdefg1", "Anna"))
    }

    @Test
    fun rejectsWeakPasswordAndBadEmail() {
        assertEquals(
            listOf(
                "Invalid email",
                "Password must be at least 8 characters and include a letter and a digit",
            ),
            RegistrationRules.studentErrors("anna", "haslo", "Anna"),
        )
    }
}
