package com.example.orbit.data.repository

/** F-13: nalog na serveru; sesiju cuva CurrentUser */
interface AuthRepository {

    suspend fun logIn(email: String, password: String): AuthResult

    suspend fun signUp(displayName: String, email: String, password: String): AuthResult

    /** Trazi kod za novu lozinku; Success i kad nalog ne postoji, server to ne otkriva */
    suspend fun requestPasswordReset(email: String): AuthResult

    /** Postavlja novu lozinku uz kod sa emaila i odmah prijavljuje */
    suspend fun resetPassword(email: String, code: String, newPassword: String): AuthResult

    /** Salje neposlate dogadjaje, brise lokalne podatke, pa token */
    suspend fun logOut()
}
