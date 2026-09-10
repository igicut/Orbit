package com.example.orbit.data.remote.dto

import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.Visibility
import kotlinx.serialization.Serializable

/**
 * F-14 - the wire format for an event: exactly what the Ktor server sends and
 * receives (its ExposedEvent).
 *
 * Why a third Event class, alongside the domain Event and EventEntity: this one
 * is owned by the server's API. If the backend renames a field, only this class
 * and its mapper change. The same reasoning as EventEntity, one layer further out.
 *
 * The enums come from the domain layer rather than being redeclared. That is
 * allowed - data may depend on domain, never the reverse - and it means there is
 * no third copy of the category list to keep in sync.
 */
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
    /** Organiser's display name, joined server-side. Null if never registered. */
    val ownerName: String? = null,
)
