package com.example.orbit.domain.model

data class User(
    val id: String,
    val displayName: String,
    val interests: List<String> = emptyList(),
)
