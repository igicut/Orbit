package com.example.orbit.data.local.entity

import androidx.room.Entity


@Entity(tableName = "blocked_users", primaryKeys = ["blockerId", "blockedId"])
data class BlockedUserEntity(
    val blockerId: String,
    val blockedId: String,
    val createdAt: Long,
)
