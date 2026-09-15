package com.example.orbit.data.remote.dto

import kotlinx.serialization.Serializable

/** Telo za PUT /events/{id}/attendance; udaljenost racuna server */
@Serializable
data class CheckInRequestDto(
    val latitude: Double,
    val longitude: Double,
)

/** Moj potvrdjen dolazak iz sync-a */
@Serializable
data class AttendanceRecordDto(
    val eventId: String,
    val checkedInAt: Long,
)

/** Red spiska prijavljenih sa GET /events/{id}/attendees */
@Serializable
data class AttendeeDto(
    val userId: String,
    val displayName: String? = null,
    val registeredAt: Long,
    val checkedInAt: Long? = null,
    val walkIn: Boolean = false,
)
