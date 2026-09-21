package com.example.orbit.domain.model

/** Ukupna ocena organizatora preko svih njegovih dogadjaja */
data class OrganiserRating(
    val average: Float,
    /** Broj ocena, ne broj dogadjaja */
    val count: Int,
)

/**
 * Prosek je tezinski: dogadjaj sa 40 ocena vredi vise od dogadjaja sa jednom.
 * Obican prosek proseka bi dao da jedna petica izjednaci cetrdeset trojki.
 * Null kad nijedan dogadjaj jos nema ocenu.
 */
fun organiserRating(events: List<Event>): OrganiserRating? {
    var sum = 0f
    var count = 0
    events.forEach { event ->
        sum += event.avgRating * event.ratingCount
        count += event.ratingCount
    }
    if (count == 0) return null
    return OrganiserRating(average = sum / count, count = count)
}
