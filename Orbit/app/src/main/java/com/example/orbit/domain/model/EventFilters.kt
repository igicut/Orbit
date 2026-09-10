package com.example.orbit.domain.model

import java.util.Calendar
import java.util.TimeZone

/**
 * F-17 / F-29 - how far away an event may be and still count as "near me".
 *
 * A fixed set of choices rather than a free slider: the values people actually
 * mean are few and far apart ("on this street" / "in my part of town" / "in the
 * city"), and a slider invites fiddling with a number that has no precise
 * meaning. Chips also survive a small screen, which a slider plus its label
 * does not.
 *
 * [km] is null for ANYWHERE, which switches distance filtering off entirely -
 * the same value used when the device location is unknown.
 */
enum class SearchRadius(val km: Double?) {
    WALK(1.0),
    NEARBY(5.0),
    CITY(25.0),
    REGION(100.0),
    ANYWHERE(null),
}

/**
 * F-29 - the date range half of the filter bar.
 *
 * Windows rather than a from/to date picker: choosing two dates to answer
 * "what is on this weekend" is far more work than tapping one chip, and the
 * ranges below are the ones people ask for.
 */
enum class DateWindow {
    ANY,
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
}

/** F-29 - ordering of the result list. */
enum class EventSort {
    /** Next to happen first. The useful default for a discovery list. */
    SOONEST,

    /** Closest first. Falls back to SOONEST when there is no location. */
    NEAREST,

    /** Highest rated first; unrated events sink to the bottom. */
    TOP_RATED,
}

/**
 * F-29 - everything the Events screen is currently filtering by.
 *
 * One immutable object rather than five separate StateFlows, so the list can be
 * recomputed from a single combine() and there is no way to update one half of
 * a filter and forget the other.
 */
data class EventFilters(
    val query: String = "",
    val radius: SearchRadius = DEFAULT_RADIUS,
    /** null means every category. */
    val category: EventCategory? = null,
    val dateWindow: DateWindow = DateWindow.ANY,
    val sort: EventSort = EventSort.SOONEST,
) {

    /**
     * How many filters are narrowing the list, for the badge on the Filters
     * button. The text query is excluded - it has its own visible field, so
     * counting it would double-report something the user can already see.
     */
    val activeCount: Int
        get() = listOf(
            radius != DEFAULT_RADIUS,
            category != null,
            dateWindow != DateWindow.ANY,
            sort != EventSort.SOONEST,
        ).count { it }

    /**
     * True when at least one active filter can only be answered with a device
     * position. The UI uses this to decide when asking for location permission
     * is justified - which is when the user reaches for one of these, not on
     * the way in.
     */
    val needsLocation: Boolean
        get() = radius.km != null || sort == EventSort.NEAREST

    companion object {
        /**
         * Distance filtering starts switched off, deliberately.
         *
         * The app has no location until the user grants one, and a screen that
         * claims to be showing events within 25 km while actually showing all of
         * them is worse than one that admits it is showing everything. Starting
         * at ANYWHERE also means the events list never raises a permission
         * dialog before the user has asked for anything that needs it.
         */
        val DEFAULT_RADIUS = SearchRadius.ANYWHERE
    }
}

/**
 * F-17 / F-29 - apply the filters to a list of events.
 *
 * A pure function in the domain layer: no Android, no Room, no coroutines. That
 * makes the whole filtering rule set testable by calling it with a list, and it
 * keeps the ViewModel down to plumbing.
 *
 * @param origin where the user is, or null when unknown. Distance filtering and
 *   NEAREST sorting are both skipped when it is null - there is nothing to
 *   measure from, and silently filtering by a guessed location would hide
 *   events for no reason the user could see.
 * @param organiserNames used so the text query also matches who is running the
 *   event, not only what it is called.
 */
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

        // Without a location there is nothing to sort by, so fall back rather
        // than returning an arbitrary order the user cannot explain.
        EventSort.NEAREST ->
            if (origin == null) filtered.sortedBy { it.startTime }
            else filtered.sortedBy { distanceFrom(origin, it) }

        // Descending rating, then soonest, so equally rated events still read
        // in a sensible order instead of shuffling between recompositions.
        EventSort.TOP_RATED ->
            filtered.sortedWith(
                compareByDescending<Event> { it.avgRating }.thenBy { it.startTime }
            )
    }
}

private fun distanceFrom(origin: UserLocation, event: Event): Double =
    Geo.distanceKm(origin.latitude, origin.longitude, event.latitude, event.longitude)

/**
 * The text query, matched against everything a person might plausibly type.
 *
 * Organiser name is included deliberately: "what else is that group running" is
 * a real question, and without this the only way to answer it is to open an
 * event and read the byline.
 */
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

/**
 * Start and end of the chosen window, or null for "any time".
 *
 * Built with Calendar in the device's own time zone, because "today" means the
 * user's today. Ranges start at [now] rather than at midnight: an event that
 * started three hours ago is not something to offer under "today".
 */
private fun dateWindowBounds(window: DateWindow, now: Long): LongRange? {
    if (window == DateWindow.ANY) return null

    val calendar = Calendar.getInstance(TimeZone.getDefault()).apply { timeInMillis = now }

    // Move to the last instant of the chosen period, then take everything
    // between now and there.
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
