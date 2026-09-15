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

    /** Samo da/ne, dovoljno za ikonicu */
    @Query("SELECT EXISTS(SELECT 1 FROM saved_events WHERE eventId = :eventId)")
    fun observeIsSaved(eventId: String): Flow<Boolean>

    /** Sacuvani dogadjaji, INNER JOIN izbacuje obrisane */
    @Query(
        "SELECT e.* FROM events e " +
            "INNER JOIN saved_events s ON s.eventId = e.id " +
            "WHERE e.ownerId NOT IN " +
            "(SELECT blockedId FROM blocked_users WHERE blockerId = :userId) " +
            "ORDER BY e.startTime ASC"
    )
    fun observeSavedEvents(userId: String): Flow<List<EventEntity>>
}
