package com.example.orbit.routes

import com.example.orbit.model.ExposedUser
import com.example.orbit.service.AuthService
import com.example.orbit.service.MailService
import com.example.orbit.service.RESET_CODE_LENGTH
import com.example.orbit.service.RESET_CODE_VALID_MINUTES
import com.example.orbit.service.TokenService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.application
import io.ktor.server.application.log
import io.ktor.server.request.receive
import jakarta.mail.MessagingException
import kotlinx.coroutines.launch
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

private const val MIN_PASSWORD_LENGTH = 8

/** bcrypt koristi najvise 72 bajta, biblioteka odbija duze */
private const val MAX_PASSWORD_BYTES = 71
private const val MAX_EMAIL_LENGTH = 254
/** Isto kao MAX_NAME_LENGTH u aplikaciji; vazi i za registraciju i za izmenu imena (MeRoutes) */
internal const val MAX_NAME_LENGTH = 40
private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

@Serializable
data class SignUpRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

/** F-13: telo za POST /auth/forgot-password */
@Serializable
data class ForgotPasswordRequest(val email: String)

/** F-13: telo za POST /auth/reset-password; kod je stigao na email */
@Serializable
data class ResetPasswordRequest(
    val email: String,
    // Prazan podrazumevano: stara aplikacija bez koda dobija 400, a ne 500
    val code: String = "",
    val password: String,
)

/** Isti odgovor za postojeci i nepostojeci nalog, da se po njemu ne vidi koji emailovi postoje */
private const val RESET_CODE_SENT_MESSAGE = "Ako nalog sa ovim emailom postoji, kod je poslat"

/** Isti tekst za pogresan, istekao i potrosen kod */
private const val INVALID_CODE_MESSAGE = "Kod nije ispravan ili je istekao"

private val RESET_CODE_REGEX = Regex("^\\d{$RESET_CODE_LENGTH}$")

private const val RESET_MAIL_SUBJECT = "Orbit - kod za novu lozinku"

private fun resetMailText(code: String) =
    "Kod za postavljanje nove lozinke: $code\n\n" +
        "Kod vazi $RESET_CODE_VALID_MINUTES minuta. " +
        "Ako niste trazili novu lozinku, zanemarite ovu poruku; stara lozinka i dalje vazi."

/**
 * Pravi kod i salje ga na email. Bez SMTP naloga kod ide u log servera,
 * da bi se funkcija mogla prikazati i bez email naloga.
 */
private suspend fun Application.deliverResetCode(
    email: String,
    authService: AuthService,
    mailService: MailService,
) {
    val code = authService.createResetCode(email) ?: return

    if (!mailService.isConfigured) {
        log.warn("SMTP_USER/SMTP_PASSWORD are not set - password reset code for $email is $code")
        return
    }
    try {
        mailService.send(email, RESET_MAIL_SUBJECT, resetMailText(code))
    } catch (e: MessagingException) {
        // Klijent je vec dobio odgovor; greska ostaje u logu servera
        log.error("Could not send the password reset code to $email", e)
    }
}

@Serializable
data class AuthResponse(
    val token: String,
    val user: ExposedUser,
)

/** F-13: POST /auth/signup, /auth/login i /auth/reset-password vracaju JWT; /auth/forgot-password salje kod */
fun Route.authRoutes(authService: AuthService, tokenService: TokenService, mailService: MailService) {

    post("/auth/signup") {
        val request = call.receive<SignUpRequest>()
        val email = request.email.trim().lowercase()
        val displayName = request.displayName.trim()

        if (email.length > MAX_EMAIL_LENGTH || !email.matches(EMAIL_REGEX)) {
            return@post call.respond(HttpStatusCode.BadRequest, "A valid email is required")
        }
        if (request.password.length < MIN_PASSWORD_LENGTH) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                "Password must have at least $MIN_PASSWORD_LENGTH characters",
            )
        }
        if (request.password.toByteArray().size > MAX_PASSWORD_BYTES) {
            return@post call.respond(HttpStatusCode.BadRequest, "Password is too long")
        }
        if (displayName.isBlank() || displayName.length > MAX_NAME_LENGTH) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                "displayName is required and limited to $MAX_NAME_LENGTH characters",
            )
        }

        val user = authService.signUp(email, request.password, displayName)
            ?: return@post call.respond(
                HttpStatusCode.Conflict,
                "An account with this email already exists",
            )

        call.respond(HttpStatusCode.Created, AuthResponse(tokenService.createToken(user.id), user))
    }

    post("/auth/login") {
        val request = call.receive<LoginRequest>()
        val email = request.email.trim().lowercase()

        // Preduga lozinka ne moze biti tacna, a bcrypt bi bacio izuzetak
        val user = if (request.password.toByteArray().size > MAX_PASSWORD_BYTES) {
            null
        } else {
            authService.logIn(email, request.password)
        }

        // Ista poruka za oba slucaja, ne otkriva koji emailovi postoje
        if (user == null) {
            return@post call.respond(HttpStatusCode.Unauthorized, "Wrong email or password")
        }

        call.respond(HttpStatusCode.OK, AuthResponse(tokenService.createToken(user.id), user))
    }

    /** Prvi korak zaboravljene lozinke: kod na email naloga */
    post("/auth/forgot-password") {
        val email = call.receive<ForgotPasswordRequest>().email.trim().lowercase()

        if (email.length > MAX_EMAIL_LENGTH || !email.matches(EMAIL_REGEX)) {
            return@post call.respond(HttpStatusCode.BadRequest, "A valid email is required")
        }

        // Odgovor ne ceka bcrypt i slanje; inace bi se po trajanju videlo da li nalog postoji
        val application = call.application
        application.launch { application.deliverResetCode(email, authService, mailService) }

        call.respond(HttpStatusCode.OK, RESET_CODE_SENT_MESSAGE)
    }

    /** Drugi korak: kod sa emaila dokazuje da je korisnik vlasnik naloga */
    post("/auth/reset-password") {
        val request = call.receive<ResetPasswordRequest>()
        val email = request.email.trim().lowercase()

        if (email.length > MAX_EMAIL_LENGTH || !email.matches(EMAIL_REGEX)) {
            return@post call.respond(HttpStatusCode.BadRequest, "A valid email is required")
        }
        // Lozinka se proverava pre koda, da greska u lozinki ne potrosi pokusaj
        if (request.password.length < MIN_PASSWORD_LENGTH) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                "Password must have at least $MIN_PASSWORD_LENGTH characters",
            )
        }
        if (request.password.toByteArray().size > MAX_PASSWORD_BYTES) {
            return@post call.respond(HttpStatusCode.BadRequest, "Password is too long")
        }
        // Kod pogresnog oblika ne trosi pokusaj
        if (!request.code.matches(RESET_CODE_REGEX)) {
            return@post call.respond(HttpStatusCode.BadRequest, INVALID_CODE_MESSAGE)
        }

        val user = authService.resetPassword(email, request.code, request.password)
            ?: return@post call.respond(HttpStatusCode.BadRequest, INVALID_CODE_MESSAGE)

        call.respond(HttpStatusCode.OK, AuthResponse(tokenService.createToken(user.id), user))
    }
}
