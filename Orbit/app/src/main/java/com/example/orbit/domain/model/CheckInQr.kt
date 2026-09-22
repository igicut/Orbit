package com.example.orbit.domain.model

/**
 * F-41: tekst u QR-u na ulazu, oblika "orbit:<idDogadjaja>:<kod>".
 * Prefiks odbacuje tudje QR kodove (npr. meni u kaficu), a id kaze za koji dogadjaj kod vazi.
 */
object CheckInQr {

    private const val PREFIX = "orbit"
    private const val SEPARATOR = ":"

    /** Tekst koji organizator prikazuje i stampa */
    fun text(eventId: String, code: String): String = PREFIX + SEPARATOR + eventId + SEPARATOR + code

    /** Kod iz skeniranog teksta, ali samo ako QR pripada bas ovom dogadjaju; inace null */
    fun codeFor(eventId: String, scanned: String): String? {
        val parts = scanned.trim().split(SEPARATOR)
        if (parts.size != 3) return null
        if (parts[0] != PREFIX || parts[1] != eventId) return null
        return parts[2].ifBlank { null }
    }
}
