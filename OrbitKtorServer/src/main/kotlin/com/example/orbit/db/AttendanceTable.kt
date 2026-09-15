package com.example.orbit.db

import org.jetbrains.exposed.v1.core.Table

/** Potvrdjeni dolasci; svaki dolazak ima i red u registrations */
object Attendances : Table("attendances") {
    val eventId = varchar("event_id", 36)
    val userId = varchar("user_id", 36).index()
    val checkedInAt = long("checked_in_at")

    override val primaryKey = PrimaryKey(eventId, userId)
}
