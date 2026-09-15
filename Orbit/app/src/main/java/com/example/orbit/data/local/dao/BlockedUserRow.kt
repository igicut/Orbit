package com.example.orbit.data.local.dao

/** Red liste blokiranih, ime moze biti null */
data class BlockedUserRow(
    val blockedId: String,
    val displayName: String?,
)
