package com.example.orbit.data.remote.dto

import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.Visibility
import kotlinx.serialization.Serializable

/** F-14: format dogadjaja kako ga salje server */
@Serializable
data class EventDto(
    val id: String,
    val ownerId: String,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val startTime: Long,
    val category: EventCategory,
    val visibility: Visibility,
    val imageUris: List<String> = emptyList(),
    val address: String? = null,
    val durationMinutes: Int? = null,
    val capacity: Int? = null,
    val price: Double? = null,
    val requiresReservation: Boolean = false,
    val accessCode: String? = null,
    val avgRating: Float = 0f,
    val ratingCount: Int = 0,
    val createdAt: Long = 0L,
    /** Ime organizatora sa servera, null ako nije registrovan */
    val ownerName: String? = null,
)
