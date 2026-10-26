package com.university.messenger.server.storage

import com.university.messenger.server.AppException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.nio.file.Files

class LocalFileStorageTest {
    @Test
    fun storesSmallFileAndDeletesPartialWhenLimitIsExceeded() {
        val root = Files.createTempDirectory("unimessenger")
        val storage = LocalFileStorage(root)
        val saved = storage.save(ByteArrayInputStream("abcd".toByteArray()), maxBytes = 10)
        assertEquals(4, saved.sizeBytes)
        assertEquals("abcd", storage.open(saved.storedName).readBytes().decodeToString())

        assertThrows<AppException.BadRequest> {
            storage.save(ByteArrayInputStream("too-long".toByteArray()), maxBytes = 3)
        }
        assertEquals(listOf(saved.storedName), Files.list(root).map { it.fileName.toString() }.toList())
        storage.delete(saved.storedName)
    }
}
