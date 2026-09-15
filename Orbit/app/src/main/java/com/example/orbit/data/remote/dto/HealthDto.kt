package com.example.orbit.data.remote.dto

import kotlinx.serialization.Serializable

/** Odgovor GET /health, za brzu proveru veze */
@Serializable
data class HealthDto(
    val status: String,
    val database: String,
)
