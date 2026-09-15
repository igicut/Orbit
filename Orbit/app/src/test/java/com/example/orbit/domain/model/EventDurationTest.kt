package com.example.orbit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** F-35: unos trajanja u formi */
class EventDurationTest {

    @Test
    fun `both fields empty means no duration`() {
        assertEquals(EventDuration.Parsed.Empty, EventDuration.parse("", " "))
    }

    @Test
    fun `hours and minutes add up, a missing field counts as zero`() {
        assertEquals(EventDuration.Parsed.Valid(90), EventDuration.parse("1", "30"))
        assertEquals(EventDuration.Parsed.Valid(120), EventDuration.parse("2", ""))
        assertEquals(EventDuration.Parsed.Valid(45), EventDuration.parse("", "45"))
    }

    @Test
    fun `zero, negative, letters and minutes over 59 are invalid`() {
        assertEquals(EventDuration.Parsed.Invalid, EventDuration.parse("0", "0"))
        assertEquals(EventDuration.Parsed.Invalid, EventDuration.parse("-1", ""))
        assertEquals(EventDuration.Parsed.Invalid, EventDuration.parse("2h", ""))
        assertEquals(EventDuration.Parsed.Invalid, EventDuration.parse("1", "60"))
    }

    @Test
    fun `more than seven days is too long`() {
        assertEquals(EventDuration.Parsed.Valid(EventDuration.MAX_MINUTES), EventDuration.parse("168", "0"))
        assertEquals(EventDuration.Parsed.TooLong, EventDuration.parse("168", "1"))
        assertEquals(EventDuration.Parsed.TooLong, EventDuration.parse("99999999999", ""))
    }

    @Test
    fun `split is the inverse of parse`() {
        val (hours, minutes) = EventDuration.split(1440 + 75)
        assertEquals(EventDuration.Parsed.Valid(1515), EventDuration.parse(hours.toString(), minutes.toString()))
    }
}
