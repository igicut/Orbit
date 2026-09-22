package com.example.orbit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheckInQrTest {

    private val eventId = "5eed0002-0000-4000-8000-000000000001"

    @Test
    fun `the scanned text gives back the same code`() {
        val scanned = CheckInQr.text(eventId, "K7P2QX9M")

        assertEquals("orbit:$eventId:K7P2QX9M", scanned)
        assertEquals("K7P2QX9M", CheckInQr.codeFor(eventId, scanned))
    }

    @Test
    fun `a QR from another event is rejected`() {
        val scanned = CheckInQr.text("5eed0002-0000-4000-8000-000000000002", "K7P2QX9M")

        assertNull(CheckInQr.codeFor(eventId, scanned))
    }

    @Test
    fun `a QR that is not from Orbit is rejected`() {
        assertNull(CheckInQr.codeFor(eventId, "https://kafic.example/meni"))
        assertNull(CheckInQr.codeFor(eventId, "wifi:$eventId:K7P2QX9M"))
    }

    @Test
    fun `a QR without a code is rejected`() {
        assertNull(CheckInQr.codeFor(eventId, "orbit:$eventId:"))
        assertNull(CheckInQr.codeFor(eventId, "orbit:$eventId"))
    }

    @Test
    fun `spaces around the scanned text are ignored`() {
        assertEquals("K7P2QX9M", CheckInQr.codeFor(eventId, "  orbit:$eventId:K7P2QX9M\n"))
    }
}
