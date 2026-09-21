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
    /** F-40: fotografija uz utisak */
    val imagePath: String? = null,
    /** Ime autora iz users tabele, nije kolona; samo u listi utisaka */
    val authorName: String? = null,
)
