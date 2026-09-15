package com.example.orbit.plugins

import com.example.orbit.service.TokenService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Duration.Companion.minutes

/** Ime provere, koristi se kao authenticate(JWT_AUTH) */
const val JWT_AUTH = "auth-jwt"

/** Ogranicenje za /auth rute, koristi se kao rateLimit(AUTH_RATE_LIMIT) */
val AUTH_RATE_LIMIT = RateLimitName("auth")

/** Dovoljno za par pogresno otkucanih lozinki, premalo za pogadjanje */
private const val AUTH_REQUESTS_PER_MINUTE = 10

/** Databases modul odavde uzima isti TokenService */
val TokenServiceKey = AttributeKey<TokenService>("TokenService")

/** F-13: JWT provera; kljuc iz JWT_SECRET, inace nasumican do restarta */
fun Application.configureSecurity() {
    val secret = System.getenv("JWT_SECRET")?.takeIf { it.isNotBlank() }
        ?: randomSecret().also {
            log.warn("JWT_SECRET is not set - using a random key, every restart logs users out")
        }

    val tokenService = TokenService(secret)
    attributes.put(TokenServiceKey, tokenService)

    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = "orbit"
            verifier(tokenService.verifier)
            // Potpis i rok su vec provereni, treba jos id korisnika
            validate { credential ->
                if (credential.payload.subject.isNullOrBlank()) null else JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Missing or invalid token")
            }
        }
    }

    // Preko limita plugin sam vraca 429 sa Retry-After; brojac je po IP adresi
    install(RateLimit) {
        register(AUTH_RATE_LIMIT) {
            rateLimiter(limit = AUTH_REQUESTS_PER_MINUTE, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteAddress }
        }
    }
}

private fun randomSecret(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.getEncoder().encodeToString(bytes)
}
