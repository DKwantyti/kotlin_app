package com.university.messenger.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class AccountPermissionsTest {
    private val student = StudentAccount(UUID.randomUUID(), "s@uni.local", "Student")
    private val otherStudent = StudentAccount(UUID.randomUUID(), "s2@uni.local", "Other")
    private val teacher = TeacherAccount(UUID.randomUUID(), "t@uni.local", "Teacher", "Computer science")

    @Test
    fun studentCanMessageTeacherButNotAnotherStudent() {
        assertTrue(student.canOpenDirectWith(teacher))
        assertFalse(student.canOpenDirectWith(otherStudent))
        assertTrue(teacher.canOpenDirectWith(student))
    }

    @Test
    fun originFollowsHowTheAccountWasCreated() {
        assertEquals("SELF", student.origin())
        assertEquals("STAFF", teacher.origin())
    }
}
