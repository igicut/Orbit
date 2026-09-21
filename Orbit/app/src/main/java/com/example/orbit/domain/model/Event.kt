package com.example.orbit.domain.model

/** Najvise fotografija po dogadjaju; isto kao MAX_IMAGES na serveru (EventRoutes.kt) */
const val MAX_EVENT_PHOTOS = 5

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
    /** Broj prijava sa servera; mesta su ograniceni samo uz capacity */
    val registeredCount: Int = 0,
    val accessCode: String? = null,
    val avgRating: Float = 0f,
    val ratingCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** F-39: otkazan dogadjaj ostaje u listi, samo nosi oznaku */
    val status: EventStatus = EventStatus.ACTIVE,
    val cancelReason: String? = null,
    val syncedToBackend: Boolean = false,
)

enum class Visibility { PUBLIC, PRIVATE }

/** F-39: otkazivanje je jednosmerno, nema povratka u ACTIVE */
enum class EventStatus { ACTIVE, CANCELLED }


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
