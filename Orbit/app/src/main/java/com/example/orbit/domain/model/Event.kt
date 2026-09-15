package com.example.orbit.domain.model

data class Event(
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
    val createdAt: Long = System.currentTimeMillis(),
    val syncedToBackend: Boolean = false,
)

enum class Visibility { PUBLIC, PRIVATE }


enum class EventCategory {
    MUSIC,
    SPORT,
    FOOD,
    ART,
    TECH,
    OUTDOOR,
    SOCIAL,
    OTHER,

}
