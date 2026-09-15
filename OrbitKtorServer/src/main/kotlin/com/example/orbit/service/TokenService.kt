package com.example.orbit.service

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.time.Duration
import java.time.Instant

private const val ISSUER = "orbit-server"
private const val AUDIENCE = "orbit-app"

/** Bez refresh tokena, posle isteka ponovna prijava */
private val TOKEN_VALIDITY: Duration = Duration.ofDays(30)

/** F-13: pravi i proverava JWT potpisan tajnim kljucem */
class TokenService(secret: String) {

    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()

    /** subject je id korisnika */
    fun createToken(userId: String): String {
        val now = Instant.now()
        return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(userId)
            .withIssuedAt(now)
            .withExpiresAt(now.plus(TOKEN_VALIDITY))
            .sign(algorithm)
    }
}
