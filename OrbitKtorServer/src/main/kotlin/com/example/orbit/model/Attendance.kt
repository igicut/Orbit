package com.example.orbit.model

import kotlinx.serialization.Serializable

/** Red na spisku koji vidi organizator; walkIn je potvrda bez prethodne prijave */
@Serializable
data class Attendee(
    val userId: String,
    val displayName: String? = null,
    val registeredAt: Long,
    val checkedInAt: Long? = null,
    val walkIn: Boolean = false,
)

/** Moj potvrdjen dolazak, ide uz podatke naloga */
@Serializable
data class AttendanceRecord(
    val eventId: String,
    val checkedInAt: Long,
)
