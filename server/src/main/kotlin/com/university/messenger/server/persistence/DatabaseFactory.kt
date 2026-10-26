package com.university.messenger.server.persistence

import com.university.messenger.server.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Singleton of the PostgreSQL connection pool.
 * One process uses one pool, created on the first [init] call.
 */
object DatabaseFactory {
    @Volatile
    private var dataSource: HikariDataSource? = null

    @Synchronized
    fun init(config: DatabaseConfig) {
        if (dataSource != null) return
        val hikari = HikariConfig()
        hikari.jdbcUrl = config.jdbcUrl
        hikari.username = config.user
        hikari.password = config.password
        hikari.driverClassName = "org.postgresql.Driver"
        hikari.maximumPoolSize = 10
        dataSource = HikariDataSource(hikari)
    }

    internal fun <T> transaction(block: (Sql) -> T): T {
        val source = dataSource ?: error("DatabaseFactory.init was not called")
        return source.connection.use { connection ->
            connection.autoCommit = false
            try {
                val result = block(Sql(connection))
                connection.commit()
                result
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }
    }
}

internal class Sql(private val connection: Connection) {
    fun update(sql: String, vararg args: Any?): Int {
        return connection.prepareStatement(sql).use { statement ->
            bind(statement, args)
            statement.executeUpdate()
        }
    }

    fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> {
        return connection.prepareStatement(sql).use { statement ->
            bind(statement, args)
            statement.executeQuery().use { result ->
                val rows = mutableListOf<T>()
                while (result.next()) {
                    rows += map(result)
                }
                rows
            }
        }
    }

    fun <T> one(sql: String, vararg args: Any?, map: (ResultSet) -> T): T? {
        return query(sql, *args, map = map).firstOrNull()
    }
}

private fun bind(statement: PreparedStatement, args: Array<out Any?>) {
    args.forEachIndexed { index, value ->
        val position = index + 1
        when (value) {
            null -> statement.setObject(position, null)
            is UUID -> statement.setObject(position, value)
            is Instant -> statement.setTimestamp(position, Timestamp.from(value))
            is Int -> statement.setInt(position, value)
            is Long -> statement.setLong(position, value)
            else -> statement.setObject(position, value)
        }
    }
}

internal fun ResultSet.uuid(column: String): UUID = getObject(column, UUID::class.java)

internal fun ResultSet.instant(column: String): Instant? = getTimestamp(column)?.toInstant()

internal fun SQLException.isUniqueViolation(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is SQLException && current.sqlState == "23505") return true
        current = current.cause
    }
    return false
}
