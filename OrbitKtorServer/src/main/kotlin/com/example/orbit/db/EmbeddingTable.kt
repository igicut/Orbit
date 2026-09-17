package com.example.orbit.db

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.json

/**
 * F-32: vektor naslova i opisa, odvojen od events da ga ne vuce svaki upit.
 * Red postoji samo za dogadjaje koje je Gemini stigao da obradi.
 */
object EventEmbeddings : Table("event_embeddings") {
    val eventId = varchar("event_id", 36)
    val vector = json<List<Float>>("vector", Json)
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(eventId)
}
