package com.example.orbit.db

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.json.json


object Users : Table("users") {
    val id = varchar("id", 36)
    val displayName = varchar("display_name", 100)
    val interests = json<List<String>>("interests", Json)

    override val primaryKey = PrimaryKey(id)
}
