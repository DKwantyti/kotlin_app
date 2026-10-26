package com.university.messenger.domain.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegistrationRulesTest {
    @Test
    fun acceptsStudentRegistration() {
        assertTrue(RegistrationRules.studentErrors("anna@uni.local", "Haslo123", "Anna Nowak").isEmpty())
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
