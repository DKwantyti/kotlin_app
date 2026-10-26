package com.university.messenger.server.persistence

object SchemaMigrator {
    fun run() {
        val sql = SchemaMigrator::class.java.getResourceAsStream("/schema.sql")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("schema.sql is missing from server resources")
        val statements = sql.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        DatabaseFactory.transaction { connection ->
            statements.forEach { statement ->
                connection.update(statement)
            }
        }
    }
}
