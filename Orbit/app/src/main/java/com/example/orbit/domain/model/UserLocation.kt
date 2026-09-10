package com.example.orbit.domain.model

/**
 * F-17 - where the device is, as the app cares about it.
 *
 * A plain data class rather than android.location.Location, and in the domain
 * layer rather than data/, because the search filters measure distance from it -
 * and domain code must not depend on data code. Nothing here touches the Android
 * framework, so it can be built in a test without mocking.
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    /** Radius of 68% confidence, in metres. Useful for judging a coarse fix. */
    val accuracyMeters: Float,
)
