package com.example.orbit.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A bookmark: "this device wants to keep an eye on this event".
 *
 * A SEPARATE TABLE rather than an isSaved column on EventEntity, and the reason
 * matters. Syncing calls eventDao.upsert(), which REPLACES the whole row with
 * what the server sent. The server knows nothing about bookmarks, so a boolean
 * column would be silently wiped on every refresh - the same trap that made
 * syncedToBackend a local-only concern.
 *
 * Keeping the bookmark in its own table means a synced event row can be
 * overwritten as often as it likes without touching what the user saved.
 */
@Entity(tableName = "saved_events")
data class SavedEventEntity(
    @PrimaryKey val eventId: String,
    /** When it was saved - lets the list be ordered by "recently saved" later. */
    val savedAt: Long,
)
