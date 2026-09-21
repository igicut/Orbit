package com.example.orbit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** F-17/F-29: pravila filtriranja, rade na JVM bez emulatora */
class EventFiltersTest {

    private val now = 1_757_000_000_000L      // fiksno vreme, da testovi budu stabilni
    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    /** Centar Beograda kao pozicija uredjaja */
    private val belgrade = UserLocation(44.8125, 20.4612, accuracyMeters = 10f)

    private fun event(
        id: String,
        title: String = "Event",
        description: String = "Description",
        address: String? = null,
        ownerId: String = "owner",
        category: EventCategory = EventCategory.MUSIC,
        startTime: Long = now + day,
        latitude: Double = 44.8125,
        longitude: Double = 20.4612,
        avgRating: Float = 0f,
        price: Double? = null,
    ) = Event(
        id = id,
        ownerId = ownerId,
        title = title,
        description = description,
        imageUris = emptyList(),
        latitude = latitude,
        longitude = longitude,
        address = address,
        startTime = startTime,
        durationMinutes = null,
        category = category,
        capacity = null,
        price = price,
        visibility = Visibility.PUBLIC,
        accessCode = null,
        avgRating = avgRating,
        ratingCount = 0,
        createdAt = now,
        syncedToBackend = true,
    )

    // ---- tekst pretrage ----

    @Test
    fun `blank query keeps everything`() {
        val events = listOf(event("a"), event("b"))
        val result = events.applyFilters(EventFilters(), now = now)
        assertEquals(2, result.size)
    }

    @Test
    fun `query matches title case-insensitively`() {
        val events = listOf(event("a", title = "Jazz Night"), event("b", title = "Football"))
        val result = events.applyFilters(EventFilters(query = "jazz"), now = now)
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun `query matches the organiser name`() {
        val events = listOf(
            event("a", title = "Untitled", ownerId = "u1"),
            event("b", title = "Untitled", ownerId = "u2"),
        )
        val result = events.applyFilters(
            EventFilters(query = "petar"),
            organiserNames = mapOf("u1" to "Petar Petrovic", "u2" to "Marko Markovic"),
            now = now,
        )
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun `query matches the address`() {
        val events = listOf(
            event("a", address = "Knez Mihailova 1"),
            event("b", address = "Bulevar Kralja Aleksandra 73"),
        )
        val result = events.applyFilters(EventFilters(query = "knez"), now = now)
        assertEquals(listOf("a"), result.map { it.id })
    }

    // ---- kategorija ----

    @Test
    fun `null category keeps every category`() {
        val events = listOf(
            event("a", category = EventCategory.MUSIC),
            event("b", category = EventCategory.SPORT),
        )
        assertEquals(2, events.applyFilters(EventFilters(category = null), now = now).size)
    }

    @Test
    fun `category filter keeps only that category`() {
        val events = listOf(
            event("a", category = EventCategory.MUSIC),
            event("b", category = EventCategory.SPORT),
        )
        val result = events.applyFilters(
            EventFilters(category = EventCategory.SPORT), now = now,
        )
        assertEquals(listOf("b"), result.map { it.id })
    }

    // ---- udaljenost ----

    @Test
    fun `radius filters out events beyond it`() {
        val events = listOf(
            event("near", latitude = 44.8125, longitude = 20.4612),
            // Novi Sad, oko 70 km
            event("far", latitude = 45.2671, longitude = 19.8335),
        )
        val result = events.applyFilters(
            EventFilters(radius = SearchRadius.CITY), origin = belgrade, now = now,
        )
        assertEquals(listOf("near"), result.map { it.id })
    }

    @Test
    fun `a wider radius admits the same event`() {
        val events = listOf(event("far", latitude = 45.2671, longitude = 19.8335))
        val result = events.applyFilters(
            EventFilters(radius = SearchRadius.REGION), origin = belgrade, now = now,
        )
        assertEquals(listOf("far"), result.map { it.id })
    }

    @Test
    fun `anywhere does not filter by distance`() {
        val events = listOf(
            // Tokio, sto dalje moze
            event("far", latitude = 35.6762, longitude = 139.6503),
        )
        val result = events.applyFilters(
            EventFilters(radius = SearchRadius.ANYWHERE), origin = belgrade, now = now,
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `without a location the radius is ignored rather than hiding everything`() {
        val events = listOf(event("far", latitude = 35.6762, longitude = 139.6503))
        val result = events.applyFilters(
            EventFilters(radius = SearchRadius.WALK), origin = null, now = now,
        )
        assertEquals(
            "no origin means nothing to measure from, so the list must not be emptied",
            1, result.size,
        )
    }

    // ---- vremenski prozori ----

    @Test
    fun `today excludes tomorrow`() {
        val events = listOf(
            event("soon", startTime = now + hour),
            event("tomorrow", startTime = now + 2 * day),
        )
        val result = events.applyFilters(EventFilters(dateWindow = DateWindow.TODAY), now = now)
        assertEquals(listOf("soon"), result.map { it.id })
    }

    @Test
    fun `this week includes a few days out but not a month`() {
        val events = listOf(
            event("thisweek", startTime = now + 3 * day),
            event("nextmonth", startTime = now + 40 * day),
        )
        val result = events.applyFilters(
            EventFilters(dateWindow = DateWindow.THIS_WEEK), now = now,
        )
        assertEquals(listOf("thisweek"), result.map { it.id })
    }

    // ---- F-43: vikend; 2026-09-20 je nedelja, pa 23. sreda, 25. petak, 26. subota, 27. nedelja ----

    /** Lokalno vreme, isti TimeZone koji koristi dateWindowBounds */
    private fun at(month: Int, dayOfMonth: Int, hourOfDay: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, month - 1, dayOfMonth, hourOfDay, minute)
        }.timeInMillis

    private fun weekendIds(events: List<Event>, now: Long): Set<String> =
        events.applyFilters(EventFilters(dateWindow = DateWindow.WEEKEND), now = now)
            .map { it.id }
            .toSet()

    @Test
    fun `weekend on a wednesday covers saturday and sunday only`() {
        val events = listOf(
            event("friday", startTime = at(9, 25, 20)),
            event("saturday", startTime = at(9, 26, 18)),
            event("sunday late", startTime = at(9, 27, 23)),
            event("monday", startTime = at(9, 28, 10)),
        )
        assertEquals(setOf("saturday", "sunday late"), weekendIds(events, now = at(9, 23, 10)))
    }

    @Test
    fun `friday night is not yet the weekend`() {
        val events = listOf(
            event("friday night", startTime = at(9, 25, 23, 30)),
            event("saturday noon", startTime = at(9, 26, 12)),
        )
        assertEquals(setOf("saturday noon"), weekendIds(events, now = at(9, 25, 23)))
    }

    @Test
    fun `on saturday the weekend starts now and ends on sunday`() {
        val events = listOf(
            event("saturday evening", startTime = at(9, 26, 20)),
            event("sunday", startTime = at(9, 27, 18)),
            event("next saturday", startTime = at(10, 3, 18)),
        )
        assertEquals(setOf("saturday evening", "sunday"), weekendIds(events, now = at(9, 26, 14)))
    }

    @Test
    fun `late on sunday it is still this weekend, not the next one`() {
        val events = listOf(
            event("sunday night", startTime = at(9, 27, 23)),
            event("next saturday", startTime = at(10, 3, 18)),
        )
        assertEquals(setOf("sunday night"), weekendIds(events, now = at(9, 27, 22)))
    }

    @Test
    fun `a date window excludes events that already started`() {
        val events = listOf(
            event("past", startTime = now - hour),
            event("future", startTime = now + hour),
        )
        val result = events.applyFilters(EventFilters(dateWindow = DateWindow.TODAY), now = now)
        assertEquals(listOf("future"), result.map { it.id })
    }

    @Test
    fun `finished events are dropped, they belong to the History tab`() {
        val events = listOf(event("past", startTime = now - 5 * day))
        assertEquals(emptyList<String>(), events.applyFilters(EventFilters(), now = now).map { it.id })
    }

    @Test
    fun `an event in progress stays, it can still take a walk-in`() {
        val events = listOf(
            event("running", startTime = now - 5 * 60 * 1000L),
            event("finished", startTime = now - 4 * hour),
        )
        assertEquals(listOf("running"), events.applyFilters(EventFilters(), now = now).map { it.id })
    }

    // ---- sortiranje ----

    @Test
    fun `soonest orders by start time`() {
        val events = listOf(
            event("later", startTime = now + 5 * day),
            event("sooner", startTime = now + day),
        )
        val result = events.applyFilters(EventFilters(sort = EventSort.SOONEST), now = now)
        assertEquals(listOf("sooner", "later"), result.map { it.id })
    }

    @Test
    fun `nearest orders by distance from the user`() {
        val events = listOf(
            event("novisad", latitude = 45.2671, longitude = 19.8335),
            event("centre", latitude = 44.8130, longitude = 20.4620),
        )
        val result = events.applyFilters(
            EventFilters(sort = EventSort.NEAREST, radius = SearchRadius.ANYWHERE),
            origin = belgrade,
            now = now,
        )
        assertEquals(listOf("centre", "novisad"), result.map { it.id })
    }

    @Test
    fun `nearest falls back to soonest when there is no location`() {
        val events = listOf(
            event("later", startTime = now + 5 * day),
            event("sooner", startTime = now + day),
        )
        val result = events.applyFilters(
            EventFilters(sort = EventSort.NEAREST), origin = null, now = now,
        )
        assertEquals(listOf("sooner", "later"), result.map { it.id })
    }

    @Test
    fun `top rated puts the best first and unrated last`() {
        val events = listOf(
            event("unrated", avgRating = 0f),
            event("good", avgRating = 4.8f),
            event("ok", avgRating = 3.1f),
        )
        val result = events.applyFilters(EventFilters(sort = EventSort.TOP_RATED), now = now)
        assertEquals(listOf("good", "ok", "unrated"), result.map { it.id })
    }

    // ---- F-45: cena ----

    @Test
    fun `free keeps events without a price and with price zero`() {
        val events = listOf(
            event("noprice", price = null),
            event("zero", price = 0.0),
            event("paid", price = 300.0),
        )
        val result = events.applyFilters(EventFilters(price = PriceLimit.FREE), now = now)
        assertEquals(setOf("noprice", "zero"), result.map { it.id }.toSet())
    }

    @Test
    fun `a price limit includes the limit itself and free events`() {
        val events = listOf(
            event("free", price = null),
            event("exact", price = 1000.0),
            event("over", price = 1000.5),
        )
        val result = events.applyFilters(EventFilters(price = PriceLimit.UP_TO_1000), now = now)
        assertEquals(setOf("free", "exact"), result.map { it.id }.toSet())
    }

    @Test
    fun `any price does not filter`() {
        val events = listOf(event("free"), event("expensive", price = 20000.0))
        assertEquals(2, events.applyFilters(EventFilters(price = PriceLimit.ANY), now = now).size)
    }

    // ---- kombinacije i bedz ----

    @Test
    fun `filters compose rather than override each other`() {
        val events = listOf(
            event("keep", title = "Jazz", category = EventCategory.MUSIC, startTime = now + hour),
            event("wrongcategory", title = "Jazz", category = EventCategory.SPORT, startTime = now + hour),
            event("wrongdate", title = "Jazz", category = EventCategory.MUSIC, startTime = now + 40 * day),
            event("wrongtitle", title = "Rock", category = EventCategory.MUSIC, startTime = now + hour),
        )
        val result = events.applyFilters(
            EventFilters(
                query = "jazz",
                category = EventCategory.MUSIC,
                dateWindow = DateWindow.TODAY,
            ),
            now = now,
        )
        assertEquals(listOf("keep"), result.map { it.id })
    }

    @Test
    fun `active count ignores the text query but counts the rest`() {
        assertEquals(0, EventFilters().activeCount)
        assertEquals(0, EventFilters(query = "jazz").activeCount)
        assertEquals(1, EventFilters(category = EventCategory.MUSIC).activeCount)
        assertEquals(1, EventFilters(price = PriceLimit.FREE).activeCount)
        assertEquals(
            3,
            EventFilters(
                radius = SearchRadius.WALK,
                category = EventCategory.MUSIC,
                dateWindow = DateWindow.TODAY,
            ).activeCount,
        )
    }

    @Test
    fun `needsLocation is true only for options that measure distance`() {
        assertFalse(EventFilters().needsLocation)
        assertFalse(EventFilters(category = EventCategory.MUSIC).needsLocation)
        assertTrue(EventFilters(radius = SearchRadius.CITY).needsLocation)
        assertTrue(EventFilters(sort = EventSort.NEAREST).needsLocation)
    }

    // ---- sama formula udaljenosti ----

    @Test
    fun `distance between Belgrade and Novi Sad is about 70 km`() {
        val km = Geo.distanceKm(44.8125, 20.4612, 45.2671, 19.8335)
        assertTrue("expected roughly 70 km, got $km", km in 65.0..80.0)
    }

    @Test
    fun `distance from a point to itself is zero`() {
        assertEquals(0.0, Geo.distanceKm(44.8125, 20.4612, 44.8125, 20.4612), 0.0001)
    }

    // ---- F-32: skorovi sa servera ----

    @Test
    fun `semantic scores add events the text did not match`() {
        val events = listOf(
            event("quiz", title = "Kviz veče"),
            event("board", title = "Veče društvenih igara"),
        )

        val result = events.applyFilters(
            EventFilters(query = "nešto zabavno u društvu"),
            relevance = mapOf("board" to 0.71f),
            now = now,
        )

        assertEquals(listOf("board"), result.map { it.id })
    }

    @Test
    fun `a literal match is never dropped because of scores`() {
        val events = listOf(
            event("hackathon", title = "Hakaton na ETF-u"),
            event("related", title = "Programerski maraton"),
        )

        // Server je rangirao samo srodni dogadjaj, doslovan pogodak nema skor
        val result = events.applyFilters(
            EventFilters(query = "Hakaton"),
            relevance = mapOf("related" to 0.68f),
            now = now,
        )

        assertEquals(listOf("related", "hackathon"), result.map { it.id })
    }

    @Test
    fun `results are ordered by score while the sort is the default`() {
        val events = listOf(
            event("low", title = "Prvi", startTime = now + hour),
            event("high", title = "Drugi", startTime = now + day),
        )

        val result = events.applyFilters(
            EventFilters(query = "opušteno veče"),
            relevance = mapOf("low" to 0.58f, "high" to 0.82f),
            now = now,
        )

        assertEquals(listOf("high", "low"), result.map { it.id })
    }

    @Test
    fun `an explicitly chosen sort wins over the score`() {
        val events = listOf(
            event("soon", title = "Prvi", startTime = now + hour, avgRating = 2f),
            event("rated", title = "Drugi", startTime = now + day, avgRating = 5f),
        )

        val result = events.applyFilters(
            EventFilters(query = "opušteno veče", sort = EventSort.TOP_RATED),
            relevance = mapOf("soon" to 0.9f, "rated" to 0.6f),
            now = now,
        )

        assertEquals(listOf("rated", "soon"), result.map { it.id })
    }

    @Test
    fun `scores never bypass the other filters`() {
        val events = listOf(
            event("faraway", title = "Koncert", latitude = 45.2671, longitude = 19.8335),
            event("wrong-category", title = "Trka", category = EventCategory.SPORT),
        )

        val result = events.applyFilters(
            EventFilters(query = "muzika", radius = SearchRadius.CITY, category = EventCategory.MUSIC),
            origin = belgrade,
            relevance = mapOf("faraway" to 0.95f, "wrong-category" to 0.93f),
            now = now,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `without scores the list behaves exactly as before`() {
        val events = listOf(
            event("a", title = "Jazz Night", startTime = now + day),
            event("b", title = "Jazz Brunch", startTime = now + hour),
        )

        val result = events.applyFilters(EventFilters(query = "jazz"), now = now)

        assertEquals(listOf("b", "a"), result.map { it.id })
    }
}
