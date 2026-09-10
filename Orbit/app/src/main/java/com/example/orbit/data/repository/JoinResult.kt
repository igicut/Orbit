package com.example.orbit.data.repository

/**
 * F-21 - what happened when someone entered an access code.
 *
 * A sealed type rather than a nullable Event because "no such code" and "could
 * not reach the server" need different messages: the first is the user's
 * mistake, the second is not, and telling someone their code is wrong when the
 * server is simply down is the kind of thing that wastes an evening.
 */
sealed interface JoinResult {
    data class Success(val eventId: String, val eventTitle: String) : JoinResult
    data object NotFound : JoinResult
    data object NetworkError : JoinResult
}
