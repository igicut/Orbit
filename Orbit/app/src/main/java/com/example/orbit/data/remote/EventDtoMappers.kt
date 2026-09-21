package com.example.orbit.data.remote

import com.example.orbit.data.remote.dto.EventDto
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.EventStatus

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
    registeredCount = registeredCount,
    accessCode = accessCode,
    avgRating = avgRating,
    ratingCount = ratingCount,
    createdAt = createdAt,
    // Nepoznat status sa servera znaci aktivan; stara verzija ga ne salje
    status = runCatching { EventStatus.valueOf(status) }.getOrDefault(EventStatus.ACTIVE),
    cancelReason = cancelReason,
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
    registeredCount = registeredCount,
    accessCode = accessCode,
    avgRating = avgRating,
    ratingCount = ratingCount,
    createdAt = createdAt,
    status = status.name,
    cancelReason = cancelReason,
)
