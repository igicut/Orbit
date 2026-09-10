package com.example.orbit.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Response of GET /health. Useful during a demo to answer "is the phone actually
 * reaching my laptop, and can the laptop reach MySQL?" in one call.
 */
@Serializable
data class HealthDto(
    val status: String,
    val database: String,
)
