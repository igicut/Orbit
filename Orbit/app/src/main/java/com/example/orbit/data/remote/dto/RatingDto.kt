package com.example.orbit.data.remote.dto

import kotlinx.serialization.Serializable

/** F-27 - mirrors the server's ExposedRating. */
@Serializable
data class RatingDto(
    val id: String,
    val eventId: String,
    val userId: String,
    val value: Int,
    val comment: String? = null,
    val createdAt: Long = 0L,
)
