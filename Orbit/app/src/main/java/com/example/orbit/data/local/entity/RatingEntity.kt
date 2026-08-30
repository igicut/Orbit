package com.example.orbit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ratings")
data class RatingEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val userId: String,
    val value: Int,
    val comment: String?,
    val createdAt: Long,
)
