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
    val demoTeacherEnabled: Boolean = false,
) {
    fun mailService(): com.university.messenger.server.service.MailService? {
        val address = System.getenv("GMAIL_ADDRESS")
        val password = System.getenv("GMAIL_APP_PASSWORD")
        val publicUrl = System.getenv("MAIL_PUBLIC_URL")
        if (address == null && password == null && publicUrl == null) return null
        require(!address.isNullOrBlank() && !password.isNullOrBlank() && !publicUrl.isNullOrBlank()) {
            "Set GMAIL_ADDRESS, GMAIL_APP_PASSWORD and MAIL_PUBLIC_URL together"
        }
        return com.university.messenger.server.service.MailService(
            com.university.messenger.server.service.GmailSender(address, password.filterNot { it.isWhitespace() }),
            publicUrl,
            jwtSecret,
        )
    }

    companion object {
        fun fromEnv(): AppConfig = AppConfig(
            port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
            database = DatabaseConfig(
                jdbcUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/unimessenger",
                user = System.getenv("DB_USER") ?: "postgres",
                password = System.getenv("DB_PASSWORD") ?: "postgres",
            ),
            jwtSecret = System.getenv("JWT_SECRET") ?: "dev-secret-change-me",
            demoTeacherEnabled = System.getenv("ENABLE_DEMO_TEACHER") == "true",
            uploadDir = Path.of(System.getenv("UPLOAD_DIR") ?: "uploads"),
        )
    }
}
