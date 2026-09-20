package com.example.orbit.data.repository

/** F-13: ishod prijave ili registracije */
sealed interface AuthResult {
    data object Success : AuthResult
    data object WrongCredentials : AuthResult
    data object EmailTaken : AuthResult

    /** Zamena lozinke, a nijedan nalog nema taj email */
    data object UnknownEmail : AuthResult

    /** Server vratio 429, previse pokusaja sa ove adrese */
    data object TooManyAttempts : AuthResult
    data object NoConnection : AuthResult

    /** Server odbio unos ili pao */
    data object Failed : AuthResult
}
