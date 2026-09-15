package com.example.orbit.data.remote.dto

import kotlinx.serialization.Serializable

/** F-27: telo PATCH zahteva, samo polja koja klijent bira */
@Serializable
data class RatingRequestDto(
    val value: Int,
    val comment: String? = null,
)
