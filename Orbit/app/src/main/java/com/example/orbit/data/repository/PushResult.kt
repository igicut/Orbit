package com.example.orbit.data.repository

import com.example.orbit.domain.model.Event

/** F-15: ishod slanja dogadjaja napravljenog na telefonu */
sealed interface PushResult {
    /** Server ga ima; event je njegova verzija, sa pristupnim kodom */
    data class Sent(val event: Event) : PushResult

    /** Nema mreze ili server privremeno ne radi; ceka sledecu sinhronizaciju */
    data object Pending : PushResult

    /**
     * Server ga je trajno odbio (400), npr. pocetak je prosao dok je telefon bio van mreze.
     * Lokalna kopija je obrisana, jer bi se inace slala zauvek; reason je poruka servera.
     */
    data class Rejected(val reason: String) : PushResult
}
