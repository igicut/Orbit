package com.example.orbit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ukupna ocena organizatora, bez servera */
class OrganiserRatingTest {

    private fun event(avgRating: Float, ratingCount: Int) = Event(
        id = "e$avgRating$ratingCount",
        ownerId = "owner",
        title = "Event",
        description = "Description",
        latitude = 44.8,
        longitude = 20.4,
        startTime = 0L,
        category = EventCategory.MUSIC,
        visibility = Visibility.PUBLIC,
        avgRating = avgRating,
        ratingCount = ratingCount,
    )

    @Test
    fun `no rated events gives no rating`() {
        assertNull(organiserRating(emptyList()))
        assertNull(organiserRating(listOf(event(0f, 0), event(0f, 0))))
    }

    @Test
    fun `a single event gives its own average`() {
        val rating = organiserRating(listOf(event(4.5f, 2)))!!
        assertEquals(4.5f, rating.average, 0.001f)
        assertEquals(2, rating.count)
    }

    @Test
    fun `the average is weighted by the number of ratings`() {
        // Jedna petica i cetrdeset trojki: obican prosek proseka bi dao 4.0
        val rating = organiserRating(listOf(event(5f, 1), event(3f, 40)))!!
        assertEquals(3.049f, rating.average, 0.001f)
        assertEquals(41, rating.count)
    }

    @Test
    fun `unrated events do not pull the average down`() {
        val rating = organiserRating(listOf(event(4f, 3), event(0f, 0)))!!
        assertEquals(4f, rating.average, 0.001f)
    }
}
