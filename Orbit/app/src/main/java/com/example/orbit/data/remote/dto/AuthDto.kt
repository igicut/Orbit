package com.example.orbit.data.remote.dto

import kotlinx.serialization.Serializable

/** F-13: telo za POST /auth/signup */
@Serializable
data class SignUpRequestDto(
    val email: String,
    val password: String,
    val displayName: String,
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

/** F-13: telo za POST /auth/reset-password */
@Serializable
data class ResetPasswordRequestDto(
    val email: String,
    val password: String,
)

/** Odgovor prijave, registracije i zamene lozinke: token i profil */
@Serializable
data class AuthResponseDto(
    val token: String,
    val user: UserDto,
)
