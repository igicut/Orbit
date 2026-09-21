package com.example.orbit.data.local

import com.example.orbit.data.local.entity.EventEntity
import com.example.orbit.domain.model.Event

fun EventEntity.toDomain(): Event = Event(
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
    status = status,
    cancelReason = cancelReason,
    syncedToBackend = syncedToBackend,
)

fun Event.toEntity(): EventEntity = EventEntity(
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
    status = status,
    cancelReason = cancelReason,
    syncedToBackend = syncedToBackend,
)
