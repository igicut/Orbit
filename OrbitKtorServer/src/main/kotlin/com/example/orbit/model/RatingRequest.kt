package com.example.orbit.model

import kotlinx.serialization.Serializable

/**
 * F-27 - the body of PATCH /events/{id}/rating.
 *
 * Deliberately NOT ExposedRating. Three of that class's fields must not come from
 * the client: eventId is in the path, userId is in the X-User-Id header, and id
 * is decided by the server. Accepting the full model would let a caller submit a
 * rating under somebody else's name.
 */
@Serializable
data class RatingRequest(
    val value: Int,
    val comment: String? = null,
)
