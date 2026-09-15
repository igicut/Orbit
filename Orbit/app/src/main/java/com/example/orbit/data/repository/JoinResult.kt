package com.example.orbit.data.repository

/** F-21: ishod unosa koda, pogresan kod ili nema mreze */
sealed interface JoinResult {
    data class Success(val eventId: String, val eventTitle: String) : JoinResult
    data object NotFound : JoinResult
    data object NetworkError : JoinResult
}
