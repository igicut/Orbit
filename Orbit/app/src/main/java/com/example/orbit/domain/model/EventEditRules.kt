package com.example.orbit.domain.model

import kotlin.math.abs

/**
 * F-12 - what may be changed about an event once it exists, and by how much.
 *
 * Pure Kotlin in the domain layer so the same reasoning can be unit tested
 * without an Android or database dependency. The server enforces the same
 * limits; these exist so the form can refuse early with a readable message
 * rather than making a round trip to be told no.
 *
 * The reasoning behind each number, rather than the number itself, is what
 * matters: an event is a promise other people have planned around.
 */
object EventEditRules {

    /** Beyond this it is not a rescheduled event, it is a different one. */
    const val MAX_RESCHEDULE_DAYS = 14
    private const val MAX_RESCHEDULE_MS = MAX_RESCHEDULE_DAYS * 24L * 60 * 60 * 1000

    /** Inside this window an event may be postponed, but never pulled forward. */
    const val SHORT_NOTICE_HOURS = 24
    private const val SHORT_NOTICE_MS = SHORT_NOTICE_HOURS * 60L * 60 * 1000

    /** Far enough to be a genuine venue change, not a different city. */
    const val MAX_RELOCATION_KM = 50.0

    /** Editing an event that already happened would rewrite what people attended. */
    fun hasStarted(original: Event, now: Long = System.currentTimeMillis()): Boolean =
        original.startTime <= now

    fun exceedsRescheduleLimit(original: Event, newStartTime: Long): Boolean =
        abs(newStartTime - original.startTime) > MAX_RESCHEDULE_MS

    /**
     * True when the event is close enough that bringing it forward would strand
     * people who planned around the announced time. Postponing stays allowed.
     */
    fun isForbiddenEarlyMove(
        original: Event,
        newStartTime: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val startsSoon = original.startTime - now <= SHORT_NOTICE_MS
        return startsSoon && newStartTime < original.startTime
    }

    fun exceedsRelocationLimit(original: Event, latitude: Double, longitude: Double): Boolean =
        Geo.distanceKm(original.latitude, original.longitude, latitude, longitude) >
            MAX_RELOCATION_KM

}
