package com.example.orbit.model

import kotlinx.serialization.Serializable

@Serializable
data class ExposedRating(
    val id: String,
    val eventId: String,
    val userId: String,
    val value: Int,
    val comment: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
