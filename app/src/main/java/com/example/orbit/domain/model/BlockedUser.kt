package com.example.orbit.domain.model

data class BlockedUser(
    val blockerId: String,
    val blockedId: String,
    val createdAt: Long = System.currentTimeMillis(),
)
