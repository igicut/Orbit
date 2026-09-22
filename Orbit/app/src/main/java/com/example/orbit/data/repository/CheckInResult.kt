package com.example.orbit.data.repository

/** Ishod potvrde dolaska (GPS ili F-41 QR); provere lokacije i QR-a radi ViewModel pre slanja */
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

    /** F-41: skeniran QR nije ulazni kod ovog dogadjaja */
    data object WrongCode : CheckInResult

    /** F-41: Google-ov skener nije dostupan (npr. jos se skida); pravi ga ViewModel */
    data object ScannerUnavailable : CheckInResult
    data object NoConnection : CheckInResult
    data object Failed : CheckInResult
}
