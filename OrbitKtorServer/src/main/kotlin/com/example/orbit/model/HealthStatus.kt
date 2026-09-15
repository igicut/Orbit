package com.example.orbit.model

import kotlinx.serialization.Serializable

/** Telo odgovora za GET /health */
@Serializable
data class HealthStatus(
    val status: String,
    val database: String,
)
