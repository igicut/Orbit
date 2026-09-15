package com.example.orbit.domain.model

/** F-35: trajanje iz polja sati i minuti; iste granice proverava server */
object EventDuration {

    /** Hakaton traje dan, sedam dana je vec greska u unosu */
    const val MAX_DAYS = 7
    const val MAX_MINUTES = MAX_DAYS * 24 * 60

    sealed interface Parsed {
        /** Oba polja prazna: trajanje nije zadato */
        data object Empty : Parsed
        data class Valid(val minutes: Int) : Parsed
        data object Invalid : Parsed
        data object TooLong : Parsed
    }

    fun parse(hours: String, minutes: String): Parsed {
        val h = hours.trim()
        val m = minutes.trim()
        if (h.isEmpty() && m.isEmpty()) return Parsed.Empty

        // Prazno polje uz popunjeno drugo znaci nula
        val hoursValue = if (h.isEmpty()) 0L else h.toLongOrNull() ?: return Parsed.Invalid
        val minutesValue = if (m.isEmpty()) 0L else m.toLongOrNull() ?: return Parsed.Invalid
        if (hoursValue < 0 || minutesValue !in 0..59) return Parsed.Invalid
        // Pre mnozenja, da ogroman broj sati ne prekoraci Long
        if (hoursValue > MAX_MINUTES) return Parsed.TooLong

        val total = hoursValue * 60 + minutesValue
        return when {
            total == 0L -> Parsed.Invalid
            total > MAX_MINUTES -> Parsed.TooLong
            else -> Parsed.Valid(total.toInt())
        }
    }

    /** Za popunjavanje forme pri izmeni */
    fun split(totalMinutes: Int): Pair<Int, Int> = totalMinutes / 60 to totalMinutes % 60
}
