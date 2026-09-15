package com.example.orbit.data.remote

import com.example.orbit.data.remote.dto.EventDto
import com.example.orbit.domain.model.Event

/** F-14: DTO u domen i nazad; syncedToBackend je lokalni */
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
