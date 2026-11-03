package com.university.messenger.server.service

import com.university.messenger.domain.model.DeletionMode
import com.university.messenger.domain.model.TeacherAccount
import com.university.messenger.server.AppException
import com.university.messenger.server.MemoryTokens
import com.university.messenger.server.PlainHasher
import com.university.messenger.server.buildContext
import com.university.messenger.server.persistence.InMemoryMessenger
import com.university.messenger.server.storage.FileStorage
import com.university.messenger.server.storage.StoredBlob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID

class MessengerServiceTest {
    private val store = InMemoryMessenger()
    private val files = MemoryFileStorage()
    private val context = buildContext(store, store, store, files, PlainHasher(), MemoryTokens())

    @Test
    fun studentRegistersAndLogsIn() {
        val registered = context.auth.registerStudent("Anna@Uni.local", "Haslo123", "Anna")
        val logged = context.auth.login("anna@uni.local", "Haslo123")
        assertEquals(registered.account.id, logged.account.id)
    }

    @Test
    fun teacherCodeIsBoundToEmailAndConsumedOnRegistration() {
        val email = "teacher@example.edu"
        val code = context.auth.issueTeacherCode(email, "Mathematics")
        assertTrue(code.matches(Regex("[0-9]{6}")))
        assertThrows<AppException.BadRequest> {
            context.auth.registerTeacher("other@example.edu", "Haslo123", "Anna", code)
        }

        val account = context.auth.registerTeacher(email.uppercase(), "Haslo123", "Anna", code).account
        assertTrue(account is TeacherAccount)
        assertEquals("Mathematics", (account as TeacherAccount).department)
        assertThrows<AppException.BadRequest> {
            context.auth.registerTeacher(email, "Haslo123", "Anna", code)
        }
    }

    @Test
    fun fiveWrongCodesLockInviteUntilReissued() {
        val email = "teacher@example.edu"
        val code = context.auth.issueTeacherCode(email, "Mathematics")
        val wrongCode = if (code == "000000") "000001" else "000000"
        repeat(5) {
            assertThrows<AppException.BadRequest> {
                context.auth.registerTeacher(email, "Haslo123", "Anna", wrongCode)
            }
        }
        assertThrows<AppException.BadRequest> {
            context.auth.registerTeacher(email, "Haslo123", "Anna", code)
        }
        val newCode = context.auth.issueTeacherCode(email, "Mathematics")
        assertTrue(context.auth.registerTeacher(email, "Haslo123", "Anna", newCode).account is TeacherAccount)
    }

    @Test
    fun studentCanMessageTeacherButNotAnotherStudent() {
        val teacher = store.insertTeacher(UUID.randomUUID(), "jan@uni.local", "plain:Haslo123", "Jan", "Computer science")
        val student = context.auth.registerStudent("ola@uni.local", "Haslo123", "Ola").account
        val channel = context.channels.openDirect(student, teacher.id)
        assertEquals("Jan", channel.title)
        val other = context.auth.registerStudent("ewa@uni.local", "Haslo123", "Ewa").account
        assertThrows<AppException.Forbidden> {
            context.channels.openDirect(student, other.id)
        }
    }

    @Test
    fun softDeleteHidesMessageAndHardDeleteRemovesFile() {
        val student = context.auth.registerStudent("ola@uni.local", "Haslo123", "Ola").account
        val channel = context.channels.createGroup(student, "Project", null)
        val text = context.messages.post(student, channel.id, "Hello")
        context.messages.delete(student, text.id, DeletionMode.SOFT)
        assertTrue(context.messages.history(student, channel.id, 50).isEmpty())

        context.messages.upload(
            actor = student,
            channelId = channel.id,
            fileName = "notes.pdf",
            sizeHint = 4,
            body = "material",
            source = ByteArrayInputStream("abcd".toByteArray()),
        )
        assertEquals(1, store.studentItems(student.id).size)
        val storedName = files.saved.keys.single()
        val uploaded = context.messages.history(student, channel.id, 50).single()
        context.messages.delete(student, uploaded.id, DeletionMode.HARD)
        assertEquals(listOf(storedName), files.deleted)
        assertTrue(store.studentItems(student.id).isEmpty())
    }

    @Test
    fun blockedExtensionIsRejectedBeforeSaving() {
        val student = context.auth.registerStudent("ola@uni.local", "Haslo123", "Ola").account
        val channel = context.channels.createGroup(student, "Project", null)
        val error = assertThrows<AppException.BadRequest> {
            context.messages.upload(
                actor = student,
                channelId = channel.id,
                fileName = "setup.exe",
                sizeHint = 3,
                body = "",
                source = ByteArrayInputStream("abc".toByteArray()),
            )
        }
        assertEquals("Extension .exe is blocked", error.message)
        assertTrue(files.saved.isEmpty())
    }
}

class DeletionCoordinatorTest {
    @Test
    fun picksStrategyByMode() {
        val soft = RecordingStrategy(DeletionMode.SOFT)
        val hard = RecordingStrategy(DeletionMode.HARD)
        val coordinator = DeletionCoordinator(listOf(hard, soft))
        val id = UUID.randomUUID()
        coordinator.deleteMessage(DeletionMode.SOFT, id)
        coordinator.deleteMessage(DeletionMode.HARD, id)
        assertEquals(listOf(id), soft.messages)
        assertEquals(listOf(id), hard.messages)
    }
}

private class RecordingStrategy(override val mode: DeletionMode) : DeletionStrategy {
    val messages = mutableListOf<UUID>()

    override fun deleteMessage(id: UUID) {
        messages += id
    }
}

class MemoryFileStorage : FileStorage {
    val saved = linkedMapOf<String, ByteArray>()
    val deleted = mutableListOf<String>()

    override fun save(source: InputStream, maxBytes: Long): StoredBlob {
        val bytes = source.readBytes()
        if (bytes.isEmpty()) throw AppException.BadRequest("File is empty")
        if (bytes.size.toLong() > maxBytes) throw AppException.BadRequest("File exceeds 1 GB")
        val name = UUID.randomUUID().toString()
        saved[name] = bytes
        return StoredBlob(name, bytes.size.toLong())
    }

    override fun delete(storedName: String) {
        deleted += storedName
        saved.remove(storedName)
    }

    override fun open(storedName: String): InputStream {
        val bytes = saved[storedName] ?: throw AppException.NotFound("File not found")
        return ByteArrayInputStream(bytes)
    }
}
