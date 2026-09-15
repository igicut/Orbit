package com.example.orbit.routes

import com.example.orbit.model.ExposedUser
import com.example.orbit.service.AuthService
import com.example.orbit.service.TokenService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

private const val MIN_PASSWORD_LENGTH = 8

/** bcrypt koristi najvise 72 bajta, biblioteka odbija duze */
private const val MAX_PASSWORD_BYTES = 71
private const val MAX_EMAIL_LENGTH = 254
private const val MAX_NAME_LENGTH = 100
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

@Serializable
data class AuthResponse(
    val token: String,
    val user: ExposedUser,
)

/** F-13: POST /auth/signup i /auth/login, obe vracaju JWT */
fun Route.authRoutes(authService: AuthService, tokenService: TokenService) {

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
}
