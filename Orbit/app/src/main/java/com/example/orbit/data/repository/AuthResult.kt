package com.example.orbit.data.repository

/** F-13: ishod prijave, registracije ili zamene lozinke */
sealed interface AuthResult {
    data object Success : AuthResult
    data object WrongCredentials : AuthResult
    data object EmailTaken : AuthResult

    /** Zamena lozinke: kod sa emaila nije tacan, istekao je ili je potrosen */
    data object InvalidCode : AuthResult

    /** Server vratio 429, previse pokusaja sa ove adrese */
    data object TooManyAttempts : AuthResult
    data object NoConnection : AuthResult

    /** Server odbio unos ili pao */
    data object Failed : AuthResult
}
