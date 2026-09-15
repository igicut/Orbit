package com.example.orbit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Lokalna kopija moje prijave, posebna tabela jer sync prepisuje dogadjaj */
@Entity(tableName = "registrations")
data class RegistrationEntity(
    @PrimaryKey val eventId: String,
    val registeredAt: Long,
)
