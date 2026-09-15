package com.example.orbit.service

import com.example.orbit.db.Users
import com.example.orbit.model.ExposedUser
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/** Pristup bazi za users tabelu */
class ExposedUserService(private val database: R2dbcDatabase) {

    /** Registracija ili izmena, poziva se dok ne uspe */
    suspend fun register(user: ExposedUser): ExposedUser = suspendTransaction(database) {
        val exists = Users.selectAll()
            .where { Users.id eq user.id }
            .map { it[Users.id] }
            .singleOrNull() != null

        if (exists) {
            Users.update({ Users.id eq user.id }) {
                it[displayName] = user.displayName
                it[interests] = user.interests
            }
        } else {
            Users.insert {
                it[id] = user.id
                it[displayName] = user.displayName
                it[interests] = user.interests
            }
        }
        user
    }

    suspend fun read(id: String): ExposedUser? = suspendTransaction(database) {
        Users.selectAll()
            .where { Users.id eq id }
            .map { it.toExposedUser() }
            .singleOrNull()
    }

    /** Red u model, na jednom mestu */
    private fun ResultRow.toExposedUser() = ExposedUser(
        id = this[Users.id],
        displayName = this[Users.displayName],
        interests = this[Users.interests],
    )
}
