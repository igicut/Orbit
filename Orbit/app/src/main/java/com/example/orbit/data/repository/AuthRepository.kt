package com.example.orbit.data.repository

/** F-13: nalog na serveru; sesiju cuva CurrentUser */
interface AuthRepository {

    suspend fun logIn(email: String, password: String): AuthResult

    suspend fun signUp(displayName: String, email: String, password: String): AuthResult

    /** Salje neposlate dogadjaje, brise lokalne podatke, pa token */
    suspend fun logOut()
}
