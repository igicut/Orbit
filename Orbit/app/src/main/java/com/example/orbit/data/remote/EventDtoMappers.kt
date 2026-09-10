package com.example.orbit.data.remote

import com.example.orbit.data.remote.dto.EventDto
import com.example.orbit.domain.model.Event

/**
 * F-14 - translation between the wire format and the domain model.
 *
 * Two fields deserve attention:
 *
 * syncedToBackend does not exist on the server - it is purely local bookkeeping.
 * Anything that came FROM the server is synced by definition, so toDomain() sets
 * it to true, and toDto() simply drops it.
 *
 * avgRating and ratingCount are computed by the server from everyone's ratings.
 * They are sent upward but never sent back, so an outgoing event cannot overwrite
 * the real values with a stale local copy.
 */
fun EventDto.toDomain(): Event = Event(
    id = id,
    ownerId = ownerId,
    title = title,
    description = description,
    latitude = latitude,
    longitude = longitude,
    startTime = startTime,
    category = category,
    visibility = visibility,
    imageUris = imageUris,
    address = address,
    durationMinutes = durationMinutes,
    capacity = capacity,
    price = price,
    requiresReservation = requiresReservation,
    accessCode = accessCode,
    avgRating = avgRating,
    ratingCount = ratingCount,
    createdAt = createdAt,
    syncedToBackend = true,
)

fun Event.toDto(): EventDto = EventDto(
    id = id,
    ownerId = ownerId,
    title = title,
    description = description,
    latitude = latitude,
    longitude = longitude,
    startTime = startTime,
    category = category,
    visibility = visibility,
    imageUris = imageUris,
    address = address,
    durationMinutes = durationMinutes,
    capacity = capacity,
    price = price,
    requiresReservation = requiresReservation,
    accessCode = accessCode,
    avgRating = avgRating,
    ratingCount = ratingCount,
    createdAt = createdAt,
)
