package com.example.orbit.data.local.dao

/**
 * One row of the blocked list: the id, plus the display name if this device has
 * ever seen it.
 *
 * A projection rather than the entity, because the name lives in a different
 * table. It is nullable on purpose - you can block somebody while offline, and
 * a list showing a raw id is better than one that refuses to show the block.
 */
data class BlockedUserRow(
    val blockedId: String,
    val displayName: String?,
)
