package com.example.orbit.db

import org.jetbrains.exposed.v1.core.Table

/** Ko je otpremio koju sliku; slike iz seed podataka ovde nemaju red */
object Images : Table("images") {
    /** Ime fajla u uploads/, bez prefiksa /images/ */
    val name = varchar("name", 64)
    val uploaderId = varchar("uploader_id", 36)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(name)
}
