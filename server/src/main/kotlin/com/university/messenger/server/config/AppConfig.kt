package com.university.messenger.server.config

import java.nio.file.Path

data class DatabaseConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String,
)

data class AppConfig(
    val port: Int,
    val database: DatabaseConfig,
    val jwtSecret: String,
    val uploadDir: Path,
) {
    companion object {
        fun fromEnv(): AppConfig = AppConfig(
            port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
            database = DatabaseConfig(
                jdbcUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/unimessenger",
                user = System.getenv("DB_USER") ?: "postgres",
                password = System.getenv("DB_PASSWORD") ?: "postgres",
            ),
            jwtSecret = System.getenv("JWT_SECRET") ?: "dev-secret-change-me",
            uploadDir = Path.of(System.getenv("UPLOAD_DIR") ?: "uploads"),
        )
    }
}
