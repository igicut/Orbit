package com.example.orbit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Obelezivac u posebnoj tabeli jer sync prepisuje red */
@Entity(tableName = "saved_events")
data class SavedEventEntity(
    @PrimaryKey val eventId: String,
    /** Za sortiranje po vremenu cuvanja */
    val savedAt: Long,
)
