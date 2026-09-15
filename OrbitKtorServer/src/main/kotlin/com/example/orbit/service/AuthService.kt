package com.example.orbit.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.example.orbit.db.Credentials
import com.example.orbit.db.Users
import com.example.orbit.model.ExposedUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.util.UUID

/** Veci broj je sporiji i za napadaca koji pogadja lozinke */
private const val BCRYPT_COST = 12

/** F-13: registracija i prijava; lozinka se cuva samo kao bcrypt hash */
class AuthService(
    private val database: R2dbcDatabase,
    private val userService: ExposedUserService,
) {

    /** null ako nalog sa tim emailom vec postoji */
    suspend fun signUp(email: String, password: String, displayName: String): ExposedUser? {
        // bcrypt je namerno spor, zato van niti za zahteve
        val hash = withContext(Dispatchers.Default) {
            BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray())
        }
        val user = ExposedUser(id = UUID.randomUUID().toString(), displayName = displayName)

        return suspendTransaction(database) {
            if (findCredentials(email) != null) return@suspendTransaction null

            // Profil i lozinka nastaju zajedno ili nikako
            Users.insert {
                it[id] = user.id
                it[Users.displayName] = user.displayName
                it[interests] = user.interests
            }
            Credentials.insert {
                it[userId] = user.id
                it[Credentials.email] = email
                it[passwordHash] = hash
                it[createdAt] = System.currentTimeMillis()
            }
            user
        }
    }

    /** null za nepoznat email ili pogresnu lozinku */
    suspend fun logIn(email: String, password: String): ExposedUser? {
        val (userId, hash) = suspendTransaction(database) { findCredentials(email) }
            ?: return null

        val matches = withContext(Dispatchers.Default) {
            BCrypt.verifyer().verify(password.toCharArray(), hash).verified
        }
        return if (matches) userService.read(userId) else null
    }

    /** Par (userId, hash) ili null */
    private suspend fun findCredentials(email: String): Pair<String, String>? =
        Credentials.selectAll()
            .where { Credentials.email eq email }
            .map { it[Credentials.userId] to it[Credentials.passwordHash] }
            .singleOrNull()
}
