package com.example.orbit.db

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/** Pravi tabele koje ne postoje, ne menja postojece */
suspend fun createSchema(database: R2dbcDatabase) {
    suspendTransaction(database) {
        SchemaUtils.create(Events, Users, Ratings)
    }
}
