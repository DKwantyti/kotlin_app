package com.university.messenger.server.persistence

import com.university.messenger.domain.model.DeletionMode
import com.university.messenger.server.MemoryTokens
import com.university.messenger.server.PlainHasher
import com.university.messenger.server.buildContext
import com.university.messenger.server.config.DatabaseConfig
import com.university.messenger.server.service.MemoryFileStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.ByteArrayInputStream
import java.sql.DriverManager
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.*")
class PostgresMessengerTest {
    @Test
    fun groupProfileSearchAndDeletionUseRealPostgres() {
        val url = checkNotNull(System.getenv("TEST_DB_URL"))
        val user = System.getenv("TEST_DB_USER") ?: "postgres"
        val password = System.getenv("TEST_DB_PASSWORD").orEmpty()
        val schema = "test_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, user, password).use { admin ->
            admin.createStatement().use { it.execute("CREATE SCHEMA $schema") }
            try {
                val separator = if ('?' in url) "&" else "?"
                DatabaseFactory.init(DatabaseConfig("$url${separator}currentSchema=$schema", user, password))
                SchemaMigrator.run()
                val users = PostgresUserRepository()
                val messages = PostgresMessageRepository()
                val files = MemoryFileStorage()
                val context = buildContext(users, PostgresChannelRepository(), messages, files, PlainHasher(), MemoryTokens())
                val student = context.auth.registerStudent("student@uni.local", "Haslo123", "Student").account
                val code = context.auth.issueTeacherCode("teacher@uni.local", "Mathematics")
                val teacher = context.auth.registerTeacher("teacher@uni.local", "Haslo123", "Teacher", code).account
                val renamed = context.auth.updateProfile(teacher, "Professor")
                assertEquals("Professor", users.findById(renamed.id)?.account?.displayName)
                val group = context.channels.createGroup(renamed, "Calculus", null)
                context.channels.addMember(renamed, group.id, student.email)
                assertEquals(listOf(group.id), context.channels.list(student).map { it.id })

                val message = context.messages.upload(
                    student, group.id, "Integral.pdf", 4, "Draft", ByteArrayInputStream("data".toByteArray()),
                )
                assertEquals(1, messages.studentItems(student.id).size)
                context.messages.edit(student, message.id, "Solution")
                assertEquals("Solution", context.messages.history(teacher, group.id, 20, "INTEGRAL").single().body)

                context.messages.delete(student, message.id, DeletionMode.SOFT)
                assertEquals(emptyList<Any>(), context.messages.history(teacher, group.id, 20, "integral"))
                assertNotNull(messages.findMessage(message.id)?.deletedAt)
                context.messages.delete(student, message.id, DeletionMode.HARD)
                assertNull(messages.findMessage(message.id))
                assertEquals(emptyList<Any>(), messages.studentItems(student.id))
                assertEquals(1, files.deleted.size)
            } finally {
                admin.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") }
            }
        }
    }
}
