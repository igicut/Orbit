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
    val registeredCount: Int = 0,
    val accessCode: String? = null,
    val avgRating: Float = 0f,
    val ratingCount: Int = 0,
    val createdAt: Long = 0L,
    val status: String = "ACTIVE",
    val cancelReason: String? = null,
    /** Ime organizatora sa servera, null ako nije registrovan */
    val ownerName: String? = null,
    /** F-32: slicnost sa upitom; stize samo iz pretrage, ne cuva se u Room-u */
    val relevance: Double? = null,
)

/** F-21: telo za POST /events/join */
@Serializable
data class JoinRequestDto(
    val accessCode: String,
)

/** F-39: telo za POST /events/{id}/cancel */
@Serializable
data class CancelEventRequest(
    val reason: String? = null,
)
