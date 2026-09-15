package com.example.orbit.model

import kotlinx.serialization.Serializable

@Serializable
data class ExposedEvent(
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
    val createdAt: Long = System.currentTimeMillis(),
    val syncedToBackend: Boolean = false,
    /** Ime organizatora iz users tabele, nije kolona */
    val ownerName: String? = null,
)

enum class Visibility { PUBLIC, PRIVATE }


enum class EventCategory(val label: String) {
    MUSIC("Music"),
    SPORT("Sport"),
    FOOD("Food & Drink"),
    ART("Art & Culture"),
    TECH("Tech"),
    OUTDOOR("Outdoor"),
    SOCIAL("Social"),
    OTHER("Other"),
}
