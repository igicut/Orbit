package com.example.orbit.db

import org.jetbrains.exposed.v1.core.Table

/** F-13: lozinke odvojene od profila, nikad se ne salju */
object Credentials : Table("user_credentials") {
    val userId = varchar("user_id", 36)
    val email = varchar("email", 254).uniqueIndex()
    val passwordHash = varchar("password_hash", 60)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(userId)
}
