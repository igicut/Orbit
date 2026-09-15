package com.example.orbit.domain.model

import kotlin.math.abs

/** F-12: ogranicenja za izmenu postojeceg dogadjaja */
object EventEditRules {

    /** Preko ovoga to je vec drugi dogadjaj */
    const val MAX_RESCHEDULE_DAYS = 14
    private const val MAX_RESCHEDULE_MS = MAX_RESCHEDULE_DAYS * 24L * 60 * 60 * 1000

    /** U ovom roku dogadjaj se sme samo odloziti */
    const val SHORT_NOTICE_HOURS = 24
    private const val SHORT_NOTICE_MS = SHORT_NOTICE_HOURS * 60L * 60 * 1000

    /** Promena mesta, ali ne drugi grad */
    const val MAX_RELOCATION_KM = 50.0

    /** Zapoceti dogadjaj se vise ne menja */
    fun hasStarted(original: Event, now: Long = System.currentTimeMillis()): Boolean =
        original.startTime <= now

    fun exceedsRescheduleLimit(original: Event, newStartTime: Long): Boolean =
        abs(newStartTime - original.startTime) > MAX_RESCHEDULE_MS

    /** Blizu pocetka: pomeranje unapred zabranjeno, odlaganje dozvoljeno */
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
