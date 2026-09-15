package com.example.orbit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vremenski prozori i radijus potvrde dolaska, isti brojevi kao na serveru */
class AttendanceRulesTest {

    private val start = 1_757_000_000_000L
    private val minute = 60_000L

    private fun event(
        durationMinutes: Int? = 60,
        capacity: Int? = null,
        registeredCount: Int = 0,
    ) = Event(
        id = "event",
        ownerId = "owner",
        title = "Event",
        description = "Description",
        latitude = 44.8,
        longitude = 20.46,
        startTime = start,
        category = EventCategory.OTHER,
        visibility = Visibility.PUBLIC,
        durationMinutes = durationMinutes,
        capacity = capacity,
        registeredCount = registeredCount,
    )

    @Test
    fun `nobody checks in before the start`() {
        assertFalse(AttendanceRules.canCheckIn(event(), isRegistered = true, now = start - minute))
        assertFalse(AttendanceRules.canCheckIn(event(), isRegistered = false, now = start - minute))
    }

    @Test
    fun `registered guest can check in until the end`() {
        assertTrue(AttendanceRules.canCheckIn(event(durationMinutes = 60), isRegistered = true, now = start + 59 * minute))
        assertFalse(AttendanceRules.canCheckIn(event(durationMinutes = 60), isRegistered = true, now = start + 61 * minute))
    }

    @Test
    fun `walk-in only in the first 15 minutes`() {
        assertTrue(AttendanceRules.canCheckIn(event(), isRegistered = false, now = start + 15 * minute))
        assertFalse(AttendanceRules.canCheckIn(event(), isRegistered = false, now = start + 16 * minute))
    }

    @Test
    fun `walk-in needs a free spot, a registered guest does not`() {
        val full = event(capacity = 2, registeredCount = 2)
        assertFalse(AttendanceRules.canCheckIn(full, isRegistered = false, now = start + minute))
        assertTrue(AttendanceRules.canCheckIn(full, isRegistered = true, now = start + minute))
    }

    @Test
    fun `missing duration closes three hours after the start`() {
        val noDuration = event(durationMinutes = null)
        assertEquals(start + 180 * minute, AttendanceRules.endTime(noDuration))
        assertTrue(AttendanceRules.canCheckIn(noDuration, isRegistered = true, now = start + 179 * minute))
        assertFalse(AttendanceRules.canCheckIn(noDuration, isRegistered = true, now = start + 181 * minute))
    }

    @Test
    fun `event has ended only after start plus duration`() {
        assertFalse(AttendanceRules.hasEnded(event(durationMinutes = 60), now = start + 60 * minute))
        assertTrue(AttendanceRules.hasEnded(event(durationMinutes = 60), now = start + 60 * minute + 1))
        assertFalse(AttendanceRules.hasEnded(event(durationMinutes = null), now = start + 179 * minute))
    }

    @Test
    fun `distance matches the radius used by the server test`() {
        // 0.00135 stepeni geografske sirine je oko 150 m, 0.0019 oko 211 m
        val near = UserLocation(44.8 + 0.00135, 20.46, accuracyMeters = 5f)
        val far = UserLocation(44.8 + 0.0019, 20.46, accuracyMeters = 5f)

        assertTrue(AttendanceRules.distanceMeters(event(), near) in 145..155)
        assertTrue(AttendanceRules.distanceMeters(event(), far) > AttendanceRules.CHECK_IN_RADIUS_METERS)
    }
}
