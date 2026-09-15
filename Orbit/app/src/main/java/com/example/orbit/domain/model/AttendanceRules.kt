package com.example.orbit.domain.model

import kotlin.math.roundToInt

/** Potvrda dolaska; iste vrednosti proverava server u RegistrationRoutes */
object AttendanceRules {

    /** GPS u gradu greski 10-50 m, a pin za park ne pokazuje ulaz */
    const val CHECK_IN_RADIUS_METERS = 200

    /** Priblizna lokacija sa Android 12+ greski i do 3 km */
    const val MAX_ACCURACY_METERS = 100

    /** Gost bez prijave moze da potvrdi samo na pocetku dogadjaja */
    const val WALK_IN_WINDOW_MINUTES = 15

    /** Kad organizator ne zada trajanje (polje u formi je opciono) */
    const val DEFAULT_DURATION_MINUTES = 180
    private const val MINUTE_MS = 60_000L

    fun endTime(event: Event): Long =
        event.startTime + (event.durationMinutes ?: DEFAULT_DURATION_MINUTES) * MINUTE_MS

    fun hasEnded(event: Event, now: Long = System.currentTimeMillis()): Boolean = now > endTime(event)

    /** Da li detalj nudi dugme za potvrdu; server odlucuje po svom satu */
    fun canCheckIn(event: Event, isRegistered: Boolean, now: Long = System.currentTimeMillis()): Boolean {
        if (now < event.startTime || hasEnded(event, now)) return false
        if (isRegistered) return true

        val hasFreeSpot = event.capacity == null || event.registeredCount < event.capacity
        return hasFreeSpot && now <= event.startTime + WALK_IN_WINDOW_MINUTES * MINUTE_MS
    }

    fun distanceMeters(event: Event, location: UserLocation): Int =
        (Geo.distanceKm(event.latitude, event.longitude, location.latitude, location.longitude) * 1000)
            .roundToInt()
}
