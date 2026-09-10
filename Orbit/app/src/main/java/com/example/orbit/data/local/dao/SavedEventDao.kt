package com.example.orbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.orbit.data.local.entity.EventEntity
import com.example.orbit.data.local.entity.SavedEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(row: SavedEventEntity)

    @Query("DELETE FROM saved_events WHERE eventId = :eventId")
    suspend fun unsave(eventId: String)

    /**
     * EXISTS rather than fetching the row: the screen only needs a yes or no to
     * decide which icon to draw, and this returns a boolean straight from SQL.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM saved_events WHERE eventId = :eventId)")
    fun observeIsSaved(eventId: String): Flow<Boolean>

    /**
     * The saved events themselves, joined back to the events table.
     *
     * An INNER JOIN, so an event deleted locally disappears from the saved list
     * automatically instead of leaving a bookmark pointing at nothing.
     */
    @Query(
        "SELECT e.* FROM events e " +
            "INNER JOIN saved_events s ON s.eventId = e.id " +
            "WHERE e.ownerId NOT IN " +
            "(SELECT blockedId FROM blocked_users WHERE blockerId = :userId) " +
            "ORDER BY e.startTime ASC"
    )
    fun observeSavedEvents(userId: String): Flow<List<EventEntity>>
}
