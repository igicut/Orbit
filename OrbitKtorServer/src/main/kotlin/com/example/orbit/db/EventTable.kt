package com.example.orbit.db

import com.example.orbit.model.EventCategory
import com.example.orbit.model.EventStatus
import com.example.orbit.model.Visibility
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.json

object Events : Table("events") {
    val id = varchar("id", 36)
    val ownerId = varchar("owner_id", 36).index()
    val title = varchar("title", 200)
    val description = text("description")

    val latitude = double("latitude")
    val longitude = double("longitude")
    val address = varchar("address", 300).nullable()

    val startTime = long("start_time").index()
    val durationMinutes = integer("duration_minutes").nullable()

    val category = enumerationByName<EventCategory>("category", 32).index()
    val visibility = enumerationByName<Visibility>("visibility", 16).index()

    val imageUris = json<List<String>>("image_uris", Json)

    val capacity = integer("capacity").nullable()
    val price = double("price").nullable()

    /** Broj prijava, menja se u istoj transakciji kao prijava */
    val registeredCount = integer("registered_count").default(0)

    val accessCode = varchar("access_code", 8).nullable().index()

    val avgRating = float("avg_rating").default(0f)
    val ratingCount = integer("rating_count").default(0)

    val createdAt = long("created_at")

    /** F-39: podrazumevana vrednost je nuzna, postojeci redovi je nemaju */
    val status = enumerationByName<EventStatus>("status", 16).default(EventStatus.ACTIVE).index()
    val cancelReason = varchar("cancel_reason", 255).nullable()

    /** F-41: kod iz QR-a na ulazu; nije u ExposedEvent, da ga gosti ne dobiju uz dogadjaj */
    val checkInCode = varchar("check_in_code", 8).nullable()

    override val primaryKey = PrimaryKey(id)
}
