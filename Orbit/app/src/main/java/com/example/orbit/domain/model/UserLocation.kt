package com.example.orbit.domain.model

/** F-17: pozicija uredjaja, bez Android zavisnosti */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    /** Radijus preciznosti u metrima */
    val accuracyMeters: Float,
)
