package com.example.orbit.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * F-27 - body of PATCH /events/{id}/rating.
 *
 * Only the two fields the client is allowed to decide. The event comes from the
 * path, the rater from the X-User-Id header, and the row id from the server -
 * sending a full RatingDto would offer values the server must ignore anyway.
 */
@Serializable
data class RatingRequestDto(
    val value: Int,
    val comment: String? = null,
)
