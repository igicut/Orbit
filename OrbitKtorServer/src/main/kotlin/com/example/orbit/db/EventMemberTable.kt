package com.example.orbit.db

import org.jetbrains.exposed.v1.core.Table

/** F-21: ko je pristupnim kodom usao u privatni dogadjaj */
object EventMembers : Table("event_members") {
    val eventId = varchar("event_id", 36)
    val userId = varchar("user_id", 36).index()
    val joinedAt = long("joined_at")

    override val primaryKey = PrimaryKey(eventId, userId)
}
