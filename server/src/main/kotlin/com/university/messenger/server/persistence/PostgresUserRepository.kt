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
                insertTeacher(sql, id, email, passwordHash, displayName, department, now)
            }
        }
        return TeacherAccount(id, email, displayName, department)
    }

    override fun saveTeacherInvite(invite: TeacherInvite) {
        DatabaseFactory.transaction { sql ->
            sql.update(
                """
                INSERT INTO teacher_invites (email, department, code_hash, expires_at, attempts_left)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (email) DO UPDATE SET department = EXCLUDED.department,
                    code_hash = EXCLUDED.code_hash, expires_at = EXCLUDED.expires_at,
                    attempts_left = EXCLUDED.attempts_left
                """.trimIndent(),
                invite.email, invite.department, invite.codeHash, invite.expiresAt, invite.attemptsLeft,
            )
        }
    }

    override fun redeemTeacherInvite(
        id: UUID,
        email: String,
        hashPassword: () -> String,
        displayName: String,
        verify: (String) -> Boolean,
    ): Account? = write {
        DatabaseFactory.transaction { sql ->
            val invite = sql.one(
                """
                SELECT department, code_hash, expires_at, attempts_left
                FROM teacher_invites WHERE email = ? FOR UPDATE
                """.trimIndent(),
                email,
            ) { row ->
                TeacherInvite(
                    email, row.getString("department"), row.getString("code_hash"),
                    checkNotNull(row.instant("expires_at")), row.getInt("attempts_left"),
                )
            } ?: return@transaction null
            val now = Instant.now()
            if (invite.attemptsLeft <= 0 || !invite.expiresAt.isAfter(now)) return@transaction null
            if (!verify(invite.codeHash)) {
                sql.update("UPDATE teacher_invites SET attempts_left = attempts_left - 1 WHERE email = ?", email)
                return@transaction null
            }
            insertTeacher(sql, id, email, hashPassword(), displayName, invite.department, now)
            sql.update("DELETE FROM teacher_invites WHERE email = ?", email)
            TeacherAccount(id, email, displayName, invite.department)
        }
    }

    private fun insertTeacher(
        sql: Sql,
        id: UUID,
        email: String,
        passwordHash: String,
        displayName: String,
        department: String,
        now: Instant,
    ) {
        insertUser(sql, id, email, passwordHash, displayName, "TEACHER", now)
        sql.update("INSERT INTO teachers (user_id, department) VALUES (?, ?)", id, department)
    }

    override fun updateDisplayName(id: UUID, name: String): Account? {
        return DatabaseFactory.transaction { sql ->
            sql.one(
                """
                UPDATE users SET display_name = ?, updated_at = ?
                WHERE id = ? AND deleted_at IS NULL
                RETURNING id, email, display_name, role, deleted_at,
                    (SELECT department FROM teachers WHERE user_id = users.id) AS department
                """.trimIndent(),
                name, Instant.now(), id,
            ) { it.toAccount() }
        }
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

    private fun <T> write(block: () -> T): T {
        try {
            return block()
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
