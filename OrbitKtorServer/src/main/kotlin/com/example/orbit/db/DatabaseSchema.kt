package com.example.orbit.db

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/**
 * Creates every table that does not exist yet, on startup.
 *
 * SchemaUtils.create is CREATE TABLE IF NOT EXISTS - it will not alter a table
 * that already exists. If you change a column, drop the table in MySQL Workbench
 * and restart, or write the ALTER yourself. There is no destructive-migration
 * fallback on the server the way there is in Room.
 */
suspend fun createSchema(database: R2dbcDatabase) {
    suspendTransaction(database) {
        SchemaUtils.create(Events, Users, Ratings)
    }
}
