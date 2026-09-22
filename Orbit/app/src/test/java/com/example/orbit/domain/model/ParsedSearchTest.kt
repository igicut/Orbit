package com.example.orbit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** F-43: prevod odgovora AI-ja u filtere, bez servera */
class ParsedSearchTest {

    @Test
    fun `every field maps to its filter`() {
        val filters = ParsedSearch(
            keywords = "dzez",
            category = "MUSIC",
            radius = "NEARBY",
            dateWindow = "WEEKEND",
            sort = "NEAREST",
        ).toFilters()

        assertEquals("dzez", filters.query)
        assertEquals(EventCategory.MUSIC, filters.category)
        assertEquals(SearchRadius.NEARBY, filters.radius)
        assertEquals(DateWindow.WEEKEND, filters.dateWindow)
        assertEquals(EventSort.NEAREST, filters.sort)
    }

    @Test
    fun `the price limit maps to its filter`() {
        val filters = ParsedSearch(keywords = "muzika", price = "FREE").toFilters()

        assertEquals(PriceLimit.FREE, filters.price)
    }

    @Test
    fun `an empty answer gives the default filters`() {
        assertEquals(EventFilters(), ParsedSearch().toFilters())
    }

    @Test
    fun `unknown names are ignored instead of crashing`() {
        val filters = ParsedSearch(
            category = "CONCERTS",
            radius = "VERY_CLOSE",
            dateWindow = "TOMORROW",
            sort = "CHEAPEST",
        ).toFilters()

        assertNull(filters.category)
        assertEquals(EventFilters.DEFAULT_RADIUS, filters.radius)
        assertEquals(DateWindow.ANY, filters.dateWindow)
        assertEquals(EventSort.SOONEST, filters.sort)
    }

    @Test
    fun `keywords are trimmed`() {
        assertEquals("muzika", ParsedSearch(keywords = "  muzika ").toFilters().query)
    }
}
