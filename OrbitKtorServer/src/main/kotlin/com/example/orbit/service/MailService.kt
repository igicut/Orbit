package com.example.orbit.service

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

private const val DEFAULT_SMTP_HOST = "smtp.gmail.com"
private const val DEFAULT_SMTP_PORT = 587

/** Bez ovoga spor SMTP server drzi korutinu beskonacno */
private const val SMTP_TIMEOUT_MS = 10_000

/**
 * F-13: slanje emaila preko SMTP-a. Nalog i lozinka samo iz env promenljivih;
 * bez njih isConfigured je false, a kod za novu lozinku ide u log servera.
 */
class MailService(
    private val host: String,
    private val port: Int,
    private val user: String?,
    private val password: String?,
    private val from: String?,
) {

    val isConfigured: Boolean get() = user != null && password != null

    /** Baca MessagingException kad slanje ne uspe; pozivalac odlucuje sta dalje */
    suspend fun send(to: String, subject: String, text: String) {
        val properties = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", "true")
            // Lozinka naloga ne sme da ide nesifrovano
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.starttls.required", "true")
            put("mail.smtp.connectiontimeout", SMTP_TIMEOUT_MS.toString())
            put("mail.smtp.timeout", SMTP_TIMEOUT_MS.toString())
        }
        val session = Session.getInstance(properties, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(user, password)
        })

        // Bez apply: MimeMessage ima svoja polja subject i from, pa bi zasenila parametre
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(from ?: user))
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
        message.setSubject(subject, "UTF-8")
        message.setText(text, "UTF-8")

        // Slanje blokira nit, zato van niti za zahteve
        withContext(Dispatchers.IO) { Transport.send(message) }
    }

    companion object {
        fun fromEnvironment(): MailService = MailService(
            host = System.getenv("SMTP_HOST")?.takeIf { it.isNotBlank() } ?: DEFAULT_SMTP_HOST,
            port = System.getenv("SMTP_PORT")?.toIntOrNull() ?: DEFAULT_SMTP_PORT,
            user = System.getenv("SMTP_USER")?.takeIf { it.isNotBlank() },
            password = System.getenv("SMTP_PASSWORD")?.takeIf { it.isNotBlank() },
            from = System.getenv("SMTP_FROM")?.takeIf { it.isNotBlank() },
        )
    }
}
