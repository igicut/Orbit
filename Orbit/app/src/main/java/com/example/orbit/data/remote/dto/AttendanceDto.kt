package com.example.orbit.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Telo za PUT /events/{id}/attendance: ili koordinate (udaljenost racuna server),
 * ili F-41 kod sa QR-a na ulazu. Prazna polja se ne salju.
 */
@Serializable
data class CheckInRequestDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val code: String? = null,
)

/** F-41: kod za QR na ulazu, sa GET /events/{id}/check-in-code */
@Serializable
data class CheckInCodeDto(
    val code: String,
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
