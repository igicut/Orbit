package com.example.orbit.domain.model

data class Rating(
    val id: String,
    val eventId: String,
    val userId: String,
    val value: Int,
    val comment: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
