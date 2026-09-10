package com.example.orbit.data.remote.dto

import kotlinx.serialization.Serializable

/** F-13 - mirrors the server's ExposedUser. */
@Serializable
data class UserDto(
    val id: String,
    val displayName: String,
    val interests: List<String> = emptyList(),
)
