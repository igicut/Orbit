package com.example.orbit.data.repository

/** Ishod potvrde dolaska; provere lokacije radi ViewModel pre slanja */
sealed interface CheckInResult {
    data object Success : CheckInResult

    /** Nema dozvole, GPS-a ili signala */
    data object NoLocation : CheckInResult
    data object MockLocation : CheckInResult
    data class Inaccurate(val accuracyMeters: Int) : CheckInResult
    data class TooFar(val distanceMeters: Int) : CheckInResult

    /** Van vremenskog prozora za potvrdu */
    data object Closed : CheckInResult

    /** Bez prijave, a slobodnih mesta nema */
    data object Full : CheckInResult
    data object NoConnection : CheckInResult
    data object Failed : CheckInResult
}
