package com.example.orbit.domain.model

import java.util.Calendar
import java.util.TimeZone

/** F-17/F-29: fiksni izbori radijusa, null km = bilo gde */
enum class SearchRadius(val km: Double?) {
    WALK(1.0),
    NEARBY(5.0),
    CITY(25.0),
    REGION(100.0),
    ANYWHERE(null),
}

/** F-29: vremenski prozor za filter */
enum class DateWindow {
    ANY,
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
}

/** F-29: redosled liste rezultata */
enum class EventSort {
    SOONEST,

    /** Bez lokacije se vraca na SOONEST */
    NEAREST,

    /** Neocenjeni idu na kraj */
    TOP_RATED,
}

/** F-29: svi aktivni filteri u jednom objektu */
data class EventFilters(
    val query: String = "",
    val radius: SearchRadius = DEFAULT_RADIUS,
    /** null = sve kategorije */
    val category: EventCategory? = null,
    val dateWindow: DateWindow = DateWindow.ANY,
    val sort: EventSort = EventSort.SOONEST,
) {

    /** Broj aktivnih filtera za bedz, bez teksta pretrage */
    val activeCount: Int
        get() = listOf(
            radius != DEFAULT_RADIUS,
            category != null,
            dateWindow != DateWindow.ANY,
            sort != EventSort.SOONEST,
        ).count { it }

    /** Da li neki filter trazi lokaciju uredjaja */
    val needsLocation: Boolean
        get() = radius.km != null || sort == EventSort.NEAREST

    companion object {
        /** Pocinje bez filtera udaljenosti, dok nema lokacije */
        val DEFAULT_RADIUS = SearchRadius.ANYWHERE
    }
}

/** F-17/F-29: primenjuje filtere; bez origin nema filtera udaljenosti */
fun List<Event>.applyFilters(
    filters: EventFilters,
    origin: UserLocation? = null,
    organiserNames: Map<String, String> = emptyMap(),
    now: Long = System.currentTimeMillis(),
): List<Event> {

    val radiusKm = filters.radius.km
    val window = dateWindowBounds(filters.dateWindow, now)
    val queryText = filters.query.trim()

    val filtered = filter { event ->
        matchesQuery(event, queryText, organiserNames) &&
            (filters.category == null || event.category == filters.category) &&
            (window == null || event.startTime in window) &&
            (radiusKm == null || origin == null || distanceFrom(origin, event) <= radiusKm)
    }

    return when (filters.sort) {
        EventSort.SOONEST -> filtered.sortedBy { it.startTime }

        // Bez lokacije sortiramo po vremenu
        EventSort.NEAREST ->
            if (origin == null) filtered.sortedBy { it.startTime }
            else filtered.sortedBy { distanceFrom(origin, it) }

        // Po oceni, pa po vremenu za stabilan redosled
        EventSort.TOP_RATED ->
            filtered.sortedWith(
                compareByDescending<Event> { it.avgRating }.thenBy { it.startTime }
            )
    }
}

private fun distanceFrom(origin: UserLocation, event: Event): Double =
    Geo.distanceKm(origin.latitude, origin.longitude, event.latitude, event.longitude)

/** Tekst pretrage, ukljucuje i ime organizatora */
private fun matchesQuery(
    event: Event,
    query: String,
    organiserNames: Map<String, String>,
): Boolean {
    if (query.isBlank()) return true
    return event.title.contains(query, ignoreCase = true) ||
        event.description.contains(query, ignoreCase = true) ||
        event.address?.contains(query, ignoreCase = true) == true ||
        organiserNames[event.ownerId]?.contains(query, ignoreCase = true) == true
}

/** Granice izabranog perioda u lokalnoj zoni, null = bilo kad */
private fun dateWindowBounds(window: DateWindow, now: Long): LongRange? {
    if (window == DateWindow.ANY) return null

    val calendar = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = now }

    // Idemo do kraja perioda, pa uzimamo od sada
    when (window) {
        DateWindow.TODAY -> Unit
        DateWindow.THIS_WEEK -> calendar.add(Calendar.DAY_OF_YEAR, 6)
        DateWindow.THIS_MONTH -> calendar.add(Calendar.DAY_OF_YEAR, 29)
        DateWindow.ANY -> Unit
    }

    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)

    return now..calendar.timeInMillis
}
