package com.example.orbit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.orbit.domain.model.EventStatus
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.Visibility


@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val startTime: Long,
    val category: EventCategory,
    val visibility: Visibility,
    val imageUris: List<String>,
    val address: String?,
    val durationMinutes: Int?,
    val capacity: Int?,
    val price: Double?,
    val registeredCount: Int,
    val accessCode: String?,
    val avgRating: Float,
    val ratingCount: Int,
    val createdAt: Long,
    /** F-39: otkazan dogadjaj ostaje u kesu, samo nosi oznaku */
    val status: EventStatus = EventStatus.ACTIVE,
    val cancelReason: String? = null,
    val syncedToBackend: Boolean,
)
