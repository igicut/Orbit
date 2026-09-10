package com.example.orbit.domain.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distance between two points on the earth.
 *
 * Pulled out of [EventEditRules] once the search filters needed the same sum.
 * Pure Kotlin, in the domain layer, so both the edit limits and the radius
 * filter measure distance the same way and both can be unit tested with no
 * Android dependency.
 */
object Geo {

    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Great-circle ("haversine") distance in kilometres.
     *
     * Straight-line, not travel distance. For "is this event near me" that is
     * the honest measure - a walking route depends on streets the app knows
     * nothing about, and pretending otherwise would be a worse answer, not a
     * better one.
     */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
