package com.example.orbit.domain.model

import java.util.Calendar
import java.util.TimeZone

/**
 * F-17/F-29: fiksni izbori radijusa, null km = bilo gde.
 * F-43: server salje ova imena kao tekst (AiSuggestService.parseSearch); promena ovde menja i tamo.
 */
enum class SearchRadius(val km: Double?) {
    WALK(1.0),
    NEARBY(5.0),
    CITY(25.0),
    REGION(100.0),
    ANYWHERE(null),
}

/**
 * F-29: vremenski prozor za filter.
 * F-43: server salje ova imena kao tekst (AiSuggestService.parseSearch); promena ovde menja i tamo.
 */
enum class DateWindow {
    ANY,
    TODAY,
    THIS_WEEK,
    THIS_MONTH,

    /** F-43: subota i nedelja; tokom vikenda od sada do nedelje uvece */
    WEEKEND,
}

/**
 * F-29: redosled liste rezultata.
 * F-43: server salje ova imena kao tekst (AiSuggestService.parseSearch); promena ovde menja i tamo.
 */
enum class EventSort {
    SOONEST,

    /** Bez lokacije se vraca na SOONEST */
    NEAREST,

    /** Neocenjeni idu na kraj */
    TOP_RATED,
}

/** F-45: gornja granica cene u dinarima, null = bez ogranicenja */
enum class PriceLimit(val maxRsd: Double?) {
    ANY(null),
    FREE(0.0),
    UP_TO_1000(1000.0),
    UP_TO_5000(5000.0),
}

/** F-29: svi aktivni filteri u jednom objektu */
data class EventFilters(
    val query: String = "",
    val radius: SearchRadius = DEFAULT_RADIUS,
    /** null = sve kategorije */
    val category: EventCategory? = null,
    val dateWindow: DateWindow = DateWindow.ANY,
    val sort: EventSort = EventSort.SOONEST,
    val price: PriceLimit = PriceLimit.ANY,
) {

    /** Broj aktivnih filtera za bedz, bez teksta pretrage */
    val activeCount: Int
        get() = listOf(
            radius != DEFAULT_RADIUS,
            category != null,
            dateWindow != DateWindow.ANY,
            sort != EventSort.SOONEST,
            price != PriceLimit.ANY,
        ).count { it }

    /** Da li neki filter trazi lokaciju uredjaja */
    val needsLocation: Boolean
        get() = radius.km != null || sort == EventSort.NEAREST

    companion object {
        /** Pocinje bez filtera udaljenosti, dok nema lokacije */
        val DEFAULT_RADIUS = SearchRadius.ANYWHERE
    }
}

/**
 * F-17/F-29: primenjuje filtere; bez origin nema filtera udaljenosti.
 * F-32: `relevance` su skorovi sa servera; oni samo dodaju dogadjaje koje tekst nije pogodio.
 * F-36: zavrseni dogadjaji ovde vise ne izlaze, za njih postoji tab History.
 */
fun List<Event>.applyFilters(
    filters: EventFilters,
    origin: UserLocation? = null,
    organiserNames: Map<String, String> = emptyMap(),
    relevance: Map<String, Float> = emptyMap(),
    now: Long = System.currentTimeMillis(),
): List<Event> {

    val radiusKm = filters.radius.km
    val window = dateWindowBounds(filters.dateWindow, now)
    val queryText = filters.query.trim()
    val maxPrice = filters.price.maxRsd

    val filtered = filter { event ->
        // Zavrseni su na tabu History; onaj koji traje ostaje, jer jos prima upad bez prijave
        !AttendanceRules.hasEnded(event, now) &&
            (matchesQuery(event, queryText, organiserNames) || event.id in relevance) &&
            (filters.category == null || event.category == filters.category) &&
            (window == null || event.startTime in window) &&
            // Dogadjaj bez cene je besplatan, isto kao na detalju
            (maxPrice == null || (event.price ?: 0.0) <= maxPrice) &&
            (radiusKm == null || origin == null || distanceFrom(origin, event) <= radiusKm)
    }

    // Izabrani redosled je jaci od relevantnosti; podrazumevani joj ustupa mesto
    val rankByRelevance = filters.sort == EventSort.SOONEST &&
        queryText.isNotEmpty() && relevance.isNotEmpty()

    if (rankByRelevance) {
        return filtered.sortedWith(
            compareByDescending<Event> { relevance[it.id] ?: Float.NEGATIVE_INFINITY }
                .thenBy { it.startTime }
        )
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
    if (window == DateWindow.WEEKEND) return weekendBounds(now)

    val calendar = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = now }

    // Idemo do kraja perioda, pa uzimamo od sada
    when (window) {
        DateWindow.TODAY -> Unit
        DateWindow.THIS_WEEK -> calendar.add(Calendar.DAY_OF_YEAR, 6)
        DateWindow.THIS_MONTH -> calendar.add(Calendar.DAY_OF_YEAR, 29)
        DateWindow.ANY, DateWindow.WEEKEND -> Unit
    }

    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)

    return now..calendar.timeInMillis
}

/**
 * F-43: od subote 00:00 do nedelje 23:59:59; tokom vikenda pocinje od sada.
 * U Calendar-u je nedelja 1, a subota 7, pa se dani do subote dobijaju oduzimanjem.
 */
private fun weekendBounds(now: Long): LongRange {
    val calendar = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = now }
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

    val start = if (isWeekend) {
        now
    } else {
        calendar.add(Calendar.DAY_OF_YEAR, Calendar.SATURDAY - dayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.timeInMillis
    }

    // Kraj je nedelja istog vikenda; u nedelju je to danas
    calendar.timeInMillis = now
    val daysToSunday = if (dayOfWeek == Calendar.SUNDAY) 0 else Calendar.SATURDAY - dayOfWeek + 1
    calendar.add(Calendar.DAY_OF_YEAR, daysToSunday)
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)

    return start..calendar.timeInMillis
}
