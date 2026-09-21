package com.example.orbit.db

import org.jetbrains.exposed.v1.core.Table


object Ratings : Table("ratings") {
    val id = varchar("id", 36)
    val eventId = varchar("event_id", 36).index()
    val userId = varchar("user_id", 36)
    val value = integer("value")
    val comment = text("comment").nullable()

    /** F-40: jedna fotografija uz utisak, putanja sa ovog servera */
    val imagePath = varchar("image_path", 255).nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(eventId, userId)
    }
}
