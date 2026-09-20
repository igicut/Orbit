package com.example.orbit.data.repository

/** F-13: nalog na serveru; sesiju cuva CurrentUser */
interface AuthRepository {

    suspend fun logIn(email: String, password: String): AuthResult

    suspend fun signUp(displayName: String, email: String, password: String): AuthResult

    /** Postavlja novu lozinku po emailu i odmah prijavljuje */
    suspend fun resetPassword(email: String, newPassword: String): AuthResult

    /** Salje neposlate dogadjaje, brise lokalne podatke, pa token */
    suspend fun logOut()
}
