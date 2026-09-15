package com.example.orbit.db

import org.jetbrains.exposed.v1.core.Table

/** Prijave na dogadjaje; potvrdjeni dolasci su u attendances */
object Registrations : Table("registrations") {
    val eventId = varchar("event_id", 36)
    val userId = varchar("user_id", 36).index()
    val registeredAt = long("registered_at")

    override val primaryKey = PrimaryKey(eventId, userId)
}
