package com.example.orbit.model

import kotlinx.serialization.Serializable

/** F-27: telo PATCH zahteva, bez eventId i userId */
@Serializable
data class RatingRequest(
    val value: Int,
    val comment: String? = null,
    /** F-40: vec postavljena slika, dobijena od POST /images */
    val imagePath: String? = null,
)
