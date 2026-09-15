package com.example.orbit.service

import com.example.orbit.db.Users
import com.example.orbit.model.ExposedUser
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/** Pristup bazi za users tabelu */
class ExposedUserService(private val database: R2dbcDatabase) {

    /** Menja samo ime, interesovanja ostaju; null ako profil ne postoji */
    suspend fun updateDisplayName(id: String, displayName: String): ExposedUser? {
        suspendTransaction(database) {
            Users.update({ Users.id eq id }) {
                it[Users.displayName] = displayName
            }
        }
        return read(id)
    }

    suspend fun read(id: String): ExposedUser? = suspendTransaction(database) {
        Users.selectAll()
            .where { Users.id eq id }
            .map { it.toExposedUser() }
            .singleOrNull()
    }

    /** Profili po id-ju, npr. blokirani korisnici */
    suspend fun readAll(ids: List<String>): List<ExposedUser> {
        if (ids.isEmpty()) return emptyList()
        return suspendTransaction(database) {
            Users.selectAll()
                .where { Users.id inList ids }
                .map { it.toExposedUser() }
                .toList()
        }
    }

    /** Red u model, na jednom mestu */
    private fun ResultRow.toExposedUser() = ExposedUser(
        id = this[Users.id],
        displayName = this[Users.displayName],
        interests = this[Users.interests],
    )
}
