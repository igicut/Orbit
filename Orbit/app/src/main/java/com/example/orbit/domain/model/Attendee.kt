package com.example.orbit.domain.model

/** Red spiska prijavljenih; checkedInAt je null dok dolazak nije potvrdjen */
data class Attendee(
    val userId: String,
    val displayName: String?,
    val registeredAt: Long,
    val checkedInAt: Long?,
    /** Prijava je nastala potvrdom na licu mesta */
    val walkIn: Boolean,
)
