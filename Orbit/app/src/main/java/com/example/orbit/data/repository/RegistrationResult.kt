package com.example.orbit.data.repository

/** Ishod prijave ili otkazivanja */
sealed interface RegistrationResult {
    data object Success : RegistrationResult

    /** Sva mesta su zauzeta */
    data object Full : RegistrationResult

    /** Dogadjaj je poceo, prijava se vise ne menja */
    data object Closed : RegistrationResult
    data object NoConnection : RegistrationResult
    data object Failed : RegistrationResult
}
