package com.university.messenger.domain.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FileUploadPolicyTest {
    private val policy = FileUploadPolicy()

    @Test
    fun acceptsPhotoAndDocumentWithinLimit() {
        assertTrue(policy.evaluate("lecture.png", 2_048) is UploadDecision.Accepted)
        assertTrue(policy.evaluate("notes.PDF", FileUploadPolicy.MAX_BYTES) is UploadDecision.Accepted)
    }

    @Test
    fun rejectsExecutableAndDoubleExtension() {
        assertEquals("Extension .exe is blocked", (policy.evaluate("setup.EXE", 10) as UploadDecision.Rejected).reason)
        assertEquals("Extension .dmg is blocked", (policy.evaluate("app.dmg", 10) as UploadDecision.Rejected).reason)
        assertEquals("Extension .exe is blocked", (policy.evaluate("photo.jpg.exe", 10) as UploadDecision.Rejected).reason)
    }

    @Test
    fun rejectsEmptyAndOversizedFile() {
        assertEquals("File is empty", (policy.evaluate("a.txt", 0) as UploadDecision.Rejected).reason)
        assertEquals(
            "File exceeds 1 GB",
            (policy.evaluate("a.txt", FileUploadPolicy.MAX_BYTES + 1) as UploadDecision.Rejected).reason,
        )
    }
}
