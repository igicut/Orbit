package com.example.orbit.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.example.orbit.db.Credentials
import com.example.orbit.db.PasswordResetCodes
import com.example.orbit.db.Users
import com.example.orbit.model.ExposedUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.security.SecureRandom
import java.util.UUID

/** Veci broj je sporiji i za napadaca koji pogadja lozinke */
private const val BCRYPT_COST = 12

/** Kod za novu lozinku: sest cifara, vazi 15 minuta i pet provera */
const val RESET_CODE_LENGTH = 6
private const val RESET_CODE_RANGE = 1_000_000
const val RESET_CODE_VALID_MINUTES = 15
private const val RESET_CODE_VALIDITY_MS = RESET_CODE_VALID_MINUTES * 60 * 1000L
private const val MAX_RESET_ATTEMPTS = 5

/** F-13: registracija, prijava i nova lozinka; lozinka i kod se cuvaju samo kao bcrypt hash */
class AuthService(
    private val database: R2dbcDatabase,
    private val userService: ExposedUserService,
) {

    /** Kod se ne sme pogoditi, pa SecureRandom umesto obicnog Random */
    private val random = SecureRandom()

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

    /**
     * Nov kod za novu lozinku; null ako nalog sa tim emailom ne postoji.
     * Nov zahtev zamenjuje stari kod, pa vazi samo poslednji poslat.
     */
    suspend fun createResetCode(email: String): String? {
        suspendTransaction(database) { findCredentials(email) } ?: return null

        // Sest cifara, i sa vodecim nulama
        val code = random.nextInt(RESET_CODE_RANGE).toString().padStart(RESET_CODE_LENGTH, '0')
        // Kod se cuva kao i lozinka, samo kao hash
        val hash = withContext(Dispatchers.Default) {
            BCrypt.withDefaults().hashToString(BCRYPT_COST, code.toCharArray())
        }

        suspendTransaction(database) {
            PasswordResetCodes.deleteWhere { PasswordResetCodes.email eq email }
            PasswordResetCodes.insert {
                it[PasswordResetCodes.email] = email
                it[codeHash] = hash
                it[expiresAt] = System.currentTimeMillis() + RESET_CODE_VALIDITY_MS
                it[attempts] = 0
            }
        }
        return code
    }

    /**
     * Postavlja novu lozinku ako je kod tacan; null ako koda nema, istekao je,
     * potroseni su pokusaji ili nije tacan. Svi ti slucajevi izgledaju isto.
     */
    suspend fun resetPassword(email: String, code: String, newPassword: String): ExposedUser? {
        // Pokusaj se broji pre provere i u istom UPDATE-u, pa ni istovremeni
        // zahtevi ne mogu da dobiju vise od MAX_RESET_ATTEMPTS provera
        val codeHash = suspendTransaction(database) {
            val counted = PasswordResetCodes.update({
                (PasswordResetCodes.email eq email) and
                    (PasswordResetCodes.attempts less MAX_RESET_ATTEMPTS) and
                    (PasswordResetCodes.expiresAt greater System.currentTimeMillis())
            }) {
                it[attempts] = attempts + 1
            }
            if (counted == 0) return@suspendTransaction null

            PasswordResetCodes.select(PasswordResetCodes.codeHash)
                .where { PasswordResetCodes.email eq email }
                .map { it[PasswordResetCodes.codeHash] }
                .singleOrNull()
        } ?: return null

        val matches = withContext(Dispatchers.Default) {
            BCrypt.verifyer().verify(code.toCharArray(), codeHash).verified
        }
        if (!matches) return null

        val newHash = withContext(Dispatchers.Default) {
            BCrypt.withDefaults().hashToString(BCRYPT_COST, newPassword.toCharArray())
        }

        val userId = suspendTransaction(database) {
            // Kod se brise pre upisa lozinke; ako ga je drugi zahtev vec iskoristio, ovde nema sta da se brise
            val deleted = PasswordResetCodes.deleteWhere { PasswordResetCodes.email eq email }
            if (deleted == 0) return@suspendTransaction null

            Credentials.update({ Credentials.email eq email }) {
                it[passwordHash] = newHash
                // Stari tokeni prestaju da vaze, vidi Security.kt
                it[passwordChangedAt] = System.currentTimeMillis()
            }
            findCredentials(email)?.first
        } ?: return null

        return userService.read(userId)
    }

    /** Kada je lozinka poslednji put promenjena; 0 ako nikad */
    suspend fun passwordChangedAt(userId: String): Long = suspendTransaction(database) {
        Credentials.select(Credentials.passwordChangedAt)
            .where { Credentials.userId eq userId }
            .map { it[Credentials.passwordChangedAt] }
            .singleOrNull() ?: 0L
    }

    /** Par (userId, hash) ili null */
    private suspend fun findCredentials(email: String): Pair<String, String>? =
        Credentials.selectAll()
            .where { Credentials.email eq email }
            .map { it[Credentials.userId] to it[Credentials.passwordHash] }
            .singleOrNull()
}
