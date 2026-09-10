package com.example.orbit.model

import kotlinx.serialization.Serializable

@Serializable
data class ExposedUser(
    val id: String,
    val displayName: String,
    val interests: List<String> = emptyList(),
)
