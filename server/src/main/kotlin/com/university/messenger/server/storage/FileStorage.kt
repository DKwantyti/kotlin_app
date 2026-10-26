package com.university.messenger.server.storage

import com.university.messenger.server.AppException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

data class StoredBlob(
    val storedName: String,
    val sizeBytes: Long,
)

interface FileStorage {
    fun save(source: InputStream, maxBytes: Long): StoredBlob
    fun delete(storedName: String)
    fun open(storedName: String): InputStream
}

class LocalFileStorage(private val root: Path) : FileStorage {
    override fun save(source: InputStream, maxBytes: Long): StoredBlob {
        Files.createDirectories(root)
        val storedName = UUID.randomUUID().toString()
        val target = root.resolve(storedName)
        var total = 0L
        try {
            Files.newOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw AppException.BadRequest("File exceeds 1 GB")
                    output.write(buffer, 0, read)
                }
            }
            if (total == 0L) throw AppException.BadRequest("File is empty")
        } catch (error: Exception) {
            Files.deleteIfExists(target)
            throw error
        }
        return StoredBlob(storedName, total)
    }

    override fun delete(storedName: String) {
        val path = safePath(storedName) ?: return
        Files.deleteIfExists(path)
    }

    override fun open(storedName: String): InputStream {
        val path = safePath(storedName) ?: throw AppException.NotFound("File not found")
        if (!Files.exists(path)) throw AppException.NotFound("File not found")
        return Files.newInputStream(path)
    }

    private fun safePath(storedName: String): Path? {
        if (storedName.contains('/') || storedName.contains('\\') || storedName.contains("..")) return null
        return root.resolve(storedName)
    }
}
