package com.university.messenger.server.persistence

import com.university.messenger.domain.model.Account
import com.university.messenger.domain.model.AccountFactory
import com.university.messenger.domain.model.Role
import com.university.messenger.domain.model.StudentAccount
import com.university.messenger.domain.model.TeacherAccount
import com.university.messenger.server.AppException
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

class PostgresUserRepository : UserRepository {
    override fun insertStudent(id: UUID, email: String, passwordHash: String, displayName: String): Account {
        val now = Instant.now()
        write {
            DatabaseFactory.transaction { sql ->
                insertUser(sql, id, email, passwordHash, displayName, "STUDENT", now)
                sql.update("INSERT INTO students (user_id) VALUES (?)", id)
            }
        }
        return StudentAccount(id, email, displayName)
    }

    override fun insertTeacher(
        id: UUID,
        email: String,
        passwordHash: String,
        displayName: String,
        department: String,
    ): Account {
        val now = Instant.now()
        write {
            DatabaseFactory.transaction { sql ->
                insertUser(sql, id, email, passwordHash, displayName, "TEACHER", now)
                sql.update("INSERT INTO teachers (user_id, department) VALUES (?, ?)", id, department)
            }
        }
        return TeacherAccount(id, email, displayName, department)
    }

    override fun findByEmail(email: String): StoredUser? = findOne("WHERE u.email = ?", email)

    override fun findById(id: UUID): StoredUser? = findOne("WHERE u.id = ?", id)

    override fun listByRole(role: Role, includeDeleted: Boolean): List<Account> {
        val deletedFilter = if (includeDeleted) "" else "AND u.deleted_at IS NULL"
        return accounts("WHERE u.role = ? $deletedFilter ORDER BY u.display_name", role.name)
    }

    private fun findOne(where: String, arg: Any): StoredUser? {
        return DatabaseFactory.transaction { sql ->
            sql.one(
                """
                SELECT u.id, u.email, u.password_hash, u.display_name, u.role, u.deleted_at, t.department
                FROM users u
                LEFT JOIN teachers t ON t.user_id = u.id
                $where
                """.trimIndent(),
                arg,
            ) { result ->
                StoredUser(result.toAccount(), result.getString("password_hash"))
            }
        }
    }

    private fun accounts(where: String, vararg args: Any?): List<Account> {
        return DatabaseFactory.transaction { sql ->
            sql.query(
                """
                SELECT u.id, u.email, u.display_name, u.role, u.deleted_at, t.department
                FROM users u
                LEFT JOIN teachers t ON t.user_id = u.id
                $where
                """.trimIndent(),
                *args,
            ) { it.toAccount() }
        }
    }

    private fun insertUser(
        sql: Sql,
        id: UUID,
        email: String,
        passwordHash: String,
        displayName: String,
        role: String,
        now: Instant,
    ) {
        sql.update(
            """
            INSERT INTO users (id, email, password_hash, display_name, role, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            email,
            passwordHash,
            displayName,
            role,
            now,
            now,
        )
    }

    private fun write(block: () -> Unit) {
        try {
            block()
        } catch (error: SQLException) {
            if (error.isUniqueViolation()) throw AppException.Conflict("Email is already taken")
            throw error
        }
    }
}

private fun ResultSet.toAccount(): Account {
    return AccountFactory.restore(
        role = Role.valueOf(getString("role")),
        id = uuid("id"),
        email = getString("email"),
        displayName = getString("display_name"),
        department = getString("department"),
        deletedAt = instant("deleted_at"),
    )
}
