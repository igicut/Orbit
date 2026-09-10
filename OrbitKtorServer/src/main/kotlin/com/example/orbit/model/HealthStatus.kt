package com.example.orbit.model

import kotlinx.serialization.Serializable

/** Response body for GET /health. */
@Serializable
data class HealthStatus(
    val status: String,
    val database: String,
)
