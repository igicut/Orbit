package com.example.orbit.service

import com.example.orbit.db.Users
import com.example.orbit.model.ExposedUser
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/**
 * Database access for the users table.
 *
 * This is the working reference for the services you are about to write. Every
 * pattern the Event service needs is here: suspendTransaction wrapping, insert
 * with a client-supplied String id, a Flow-based read, update and delete.
 *
 * Note selectAll() returns a Flow under R2DBC, so map/singleOrNull/toList are
 * kotlinx.coroutines.flow operators, not collection ones.
 */
class ExposedUserService(private val database: R2dbcDatabase) {

    /**
     * Register a device, or update what is already registered.
     *
     * Insert-or-update rather than a plain insert, because this is called on
     * every launch until it succeeds: a device that was offline on first run
     * retries later, and a plain insert would then fail on its own id. Making
     * it idempotent also means a future rename needs no second endpoint.
     */
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

    /** Row -> model. Keeping this in one place stops the mapping drifting per query. */
    private fun ResultRow.toExposedUser() = ExposedUser(
        id = this[Users.id],
        displayName = this[Users.displayName],
        interests = this[Users.interests],
    )
}
