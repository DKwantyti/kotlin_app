package com.university.messenger.server.service

import com.university.messenger.domain.api.GroupMailResult
import com.university.messenger.domain.api.MailPreferences
import com.university.messenger.domain.model.Account
import com.university.messenger.domain.model.Role
import com.university.messenger.server.AppException
import com.university.messenger.server.persistence.DatabaseFactory
import com.university.messenger.server.persistence.uuid
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Properties
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun interface MailSender {
    fun send(to: String, subject: String, body: String)
}

class GmailSender(private val address: String, private val appPassword: String) : MailSender {
    private val session = Session.getInstance(Properties().apply {
        setProperty("mail.smtp.host", "smtp.gmail.com")
        setProperty("mail.smtp.port", "587")
        setProperty("mail.smtp.auth", "true")
        setProperty("mail.smtp.starttls.enable", "true")
        setProperty("mail.smtp.starttls.required", "true")
        setProperty("mail.smtp.ssl.checkserveridentity", "true")
        setProperty("mail.smtp.connectiontimeout", "10000")
        setProperty("mail.smtp.timeout", "10000")
        setProperty("mail.smtp.writetimeout", "10000")
    })

    override fun send(to: String, subject: String, body: String) {
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(address, true))
            setRecipient(Message.RecipientType.TO, InternetAddress(to, true))
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
        }
        Transport.send(message, address, appPassword)
    }
}

class MailService(
    private val sender: MailSender,
    private val publicUrl: String,
    private val secret: String,
) {
    private val deliveries = Semaphore(4)

    init {
        val uri = URI(publicUrl)
        require(uri.scheme == "https" && uri.host != null && uri.rawQuery == null &&
            uri.rawFragment == null && uri.rawUserInfo == null) { "MAIL_PUBLIC_URL must be a public HTTPS URL" }
        require(secret.length >= 32) { "Set a JWT_SECRET with at least 32 characters when using email" }
    }

    fun preferences(id: UUID): MailPreferences = DatabaseFactory.transaction { sql ->
        sql.one("SELECT email_verified_at IS NOT NULL, email_notifications FROM users WHERE id = ?", id) {
            MailPreferences(true, it.getBoolean(1), it.getBoolean(2))
        } ?: throw AppException.NotFound("User not found")
    }

    fun updatePreferences(id: UUID, enabled: Boolean): MailPreferences {
        val updated = DatabaseFactory.transaction { sql ->
            sql.update(
                """
                UPDATE users SET email_notifications = ? WHERE id = ? AND deleted_at IS NULL
                    AND (NOT ? OR email_verified_at IS NOT NULL)
                """.trimIndent(),
                enabled, id, enabled,
            )
        }
        if (updated == 0) throw AppException.Forbidden("Confirm your email first")
        return preferences(id)
    }

    suspend fun sendVerification(actor: Account) {
        val token = encode(ByteArray(32).also(random::nextBytes))
        val now = Instant.now()
        val hash = digest(token)
        val claimed = DatabaseFactory.transaction { sql ->
            sql.one("SELECT pg_advisory_xact_lock(609481301)") { Unit }
            val updated = sql.update(
                """
                UPDATE users SET email_verify_hash = ?, email_verify_sent_at = ?
                WHERE id = ? AND deleted_at IS NULL AND email_verified_at IS NULL
                    AND (email_verify_sent_at IS NULL OR email_verify_sent_at <= ?)
                """.trimIndent(),
                hash, now, actor.id, now.minusSeconds(120),
            )
            if (updated > 0) {
                val recent = sql.one("SELECT count(*) FROM users WHERE email_verify_sent_at > ?", now.minusSeconds(600)) {
                    it.getInt(1)
                } ?: 0
                if (recent > 20) throw AppException.BadRequest("Email limit reached. Try again later.")
            }
            updated
        }
        if (claimed == 0) return
        if (!deliver(actor.email, "UniMessenger — potwierdź adres e-mail",
                "Otwórz ten link i potwierdź adres w ciągu 24 godzin:\n${link("verify", token)}")) {
            // Keep the cooldown even if SMTP failed so retries remain rate limited.
            throw AppException.BadRequest("Email could not be sent. Try again in two minutes.")
        }
    }

    fun verify(token: String): Boolean = DatabaseFactory.transaction { sql ->
        val now = Instant.now()
        sql.update(
            """
            UPDATE users SET email_verified_at = ?, email_verify_hash = NULL
            WHERE email_verify_hash = ? AND deleted_at IS NULL AND email_verified_at IS NULL
                AND email_verify_sent_at > ?
            """.trimIndent(),
            now, digest(token), now.minusSeconds(86_400),
        ) > 0
    }

    fun unsubscribe(token: String): Boolean {
        val rawId = token.substringBefore('.')
        val id = runCatching { UUID.fromString(rawId) }.getOrNull() ?: return false
        if (!MessageDigest.isEqual(unsubscribeToken(id).toByteArray(), token.toByteArray())) return false
        return DatabaseFactory.transaction { sql ->
            sql.update("UPDATE users SET email_notifications = FALSE WHERE id = ?", id) > 0
        }
    }

    suspend fun sendGroupMail(actor: Account, channelId: UUID, subject: String, body: String): GroupMailResult {
        if (actor.role != Role.TEACHER) throw AppException.Forbidden("Only lecturers can send group email")
        val title = subject.trim()
        val text = body.trim()
        if (title.length !in 1..120 || title.any { it.isISOControl() } || text.length !in 1..4000) {
            throw AppException.BadRequest("Enter a subject (up to 120 characters) and a message (up to 4000 characters)")
        }
        val recipients = DatabaseFactory.transaction { sql ->
            val now = Instant.now()
            val claimed = sql.update(
                """
                UPDATE channels SET last_group_mail_at = ?
                WHERE id = ? AND created_by = ? AND kind = 'GROUP' AND deleted_at IS NULL
                    AND (last_group_mail_at IS NULL OR last_group_mail_at <= ?)
                """.trimIndent(),
                now, channelId, actor.id, now.minusSeconds(600),
            )
            if (claimed == 0) throw AppException.Forbidden("Only the group owner can send email, once every ten minutes")
            val members = sql.query(
                """
                SELECT u.id, u.email FROM users u JOIN channel_members cm ON cm.user_id = u.id
                WHERE cm.channel_id = ? AND u.id <> ? AND u.deleted_at IS NULL
                    AND u.email_verified_at IS NOT NULL AND u.email_notifications
                ORDER BY u.id LIMIT 51
                """.trimIndent(),
                channelId, actor.id,
            ) { it.uuid("id") to it.getString("email") }
            if (members.size > 50) throw AppException.BadRequest("Group email supports up to 50 subscribed recipients")
            members
        }
        val accepted = coroutineScope {
            recipients.map { (id, email) ->
                async {
                    deliveries.withPermit {
                        preferences(id).notifications && deliver(email, "UniMessenger — $title", "$text\n\n${footer(id)}")
                    }
                }
            }.awaitAll().count { it }
        }
        return GroupMailResult(accepted, recipients.size - accepted)
    }

    suspend fun notifyDirectMessage(channelId: UUID, senderId: UUID) {
        val recipient = DatabaseFactory.transaction { sql ->
            val now = Instant.now()
            sql.one(
                """
                UPDATE users u SET last_direct_mail_at = ? FROM channel_members cm, channels c
                WHERE c.id = ? AND c.kind = 'DIRECT' AND c.deleted_at IS NULL AND cm.channel_id = c.id
                    AND cm.user_id = u.id AND u.id <> ? AND u.deleted_at IS NULL AND u.email_notifications
                    AND u.email_verified_at IS NOT NULL AND (u.last_direct_mail_at IS NULL OR u.last_direct_mail_at <= ?)
                RETURNING u.id, u.email
                """.trimIndent(),
                now, channelId, senderId, now.minusSeconds(600),
            ) { it.uuid("id") to it.getString("email") }
        } ?: return
        val (id, email) = recipient
        deliver(email, "UniMessenger — nowa wiadomość", "Otwórz UniMessenger, aby odczytać wiadomość.\n\n${footer(id)}")
    }

    private suspend fun deliver(to: String, subject: String, body: String): Boolean = withContext(Dispatchers.IO) {
        try {
            sender.send(to, subject, body)
            true
        } catch (_: jakarta.mail.MessagingException) {
            false
        }
    }

    private fun footer(id: UUID): String = "Wyłącz powiadomienia e-mail: ${link("unsubscribe", unsubscribeToken(id))}"

    private fun link(action: String, token: String): String =
        "${publicUrl.trimEnd('/')}/mail/$action?token=$token"

    private fun unsubscribeToken(id: UUID): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return "$id.${encode(mac.doFinal("mail-unsubscribe:$id".toByteArray()))}"
    }

    private fun digest(token: String): String =
        encode(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        val random = SecureRandom()
    }
}
