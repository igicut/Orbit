package com.example.orbit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Moj potvrdjen dolazak; bez njega detalj ne nudi ocenu */
@Entity(tableName = "attendances")
data class AttendanceEntity(
    @PrimaryKey val eventId: String,
    val checkedInAt: Long,
)
