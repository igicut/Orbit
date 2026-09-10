package com.example.orbit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.orbit.data.local.entity.EventEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface EventDao {

    /**
     * F-28 - every event except those organised by somebody this device blocked.
     *
     * Filtering in SQL rather than in Kotlin means the blocked events never
     * reach a list, a map marker or a count, and there is no chance of one
     * screen remembering to filter while another forgets.
     */
    @Query(
        "SELECT * FROM events " +
            "WHERE ownerId NOT IN " +
            "(SELECT blockedId FROM blocked_users WHERE blockerId = :userId) " +
            "ORDER BY startTime ASC"
    )
    fun observeAll(userId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE ownerId = :ownerId ORDER BY startTime ASC")
    fun observeByOwner(ownerId: String): Flow<List<EventEntity>>

    /**
     * F-21 - private events this device has access to but did not create:
     * ones joined with an access code, or later received over P2P.
     *
     * Visibility is stored as the enum name by the type converter, so the
     * comparison is against the text 'PRIVATE'.
     */
    @Query(
        "SELECT * FROM events WHERE visibility = 'PRIVATE' AND ownerId != :ownerId " +
            "AND ownerId NOT IN " +
            "(SELECT blockedId FROM blocked_users WHERE blockerId = :ownerId) " +
            "ORDER BY startTime ASC"
    )
    fun observeJoinedPrivate(ownerId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    fun observeById(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: String): EventEntity?

    /**
     * F-15 - events created on this device that never reached the server.
     *
     * Restricted to events this device owns. Anything that arrived FROM the
     * server is already synced by definition, and re-uploading somebody else's
     * event under our own header would change its owner.
     *
     * Oldest first, so a backlog is sent in the order it was created.
     */
    @Query(
        "SELECT * FROM events " +
            "WHERE syncedToBackend = 0 AND ownerId = :userId " +
            "ORDER BY createdAt ASC"
    )
    suspend fun getPendingUploads(userId: String): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(event: EventEntity)

    /**
     * Drops cached public events that nothing depends on.
     *
     * Three groups are deliberately spared:
     *   - your own events        (ownerId matches)
     *   - joined private events  (visibility is not PUBLIC)
     *   - bookmarked events      (present in saved_events)
     *
     * Everything else is just a copy of what the server said last time and can
     * be thrown away, because the next refresh will bring it back.
     */
    @Query(
        "DELETE FROM events " +
            "WHERE visibility = 'PUBLIC' " +
            "AND ownerId != :userId " +
            "AND id NOT IN (SELECT eventId FROM saved_events)"
    )
    suspend fun deleteStalePublicCache(userId: String)

    /**
     * Swap the cached public events for a fresh set, in one transaction.
     *
     * @Transaction matters for more than crash safety here: Room notifies its
     * Flows when a transaction COMMITS, not on each statement. Without it the
     * delete and the inserts would emit separately and the list would visibly
     * blink empty on every refresh.
     */
    @Transaction
    suspend fun replacePublicCache(userId: String, events: List<EventEntity>) {
        deleteStalePublicCache(userId)
        events.forEach { upsert(it) }
    }
}
