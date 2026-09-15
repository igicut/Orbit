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

    /** F-28: svi dogadjaji osim od blokiranih korisnika */
    @Query(
        "SELECT * FROM events " +
            "WHERE ownerId NOT IN " +
            "(SELECT blockedId FROM blocked_users WHERE blockerId = :userId) " +
            "ORDER BY startTime ASC"
    )
    fun observeAll(userId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE ownerId = :ownerId ORDER BY startTime ASC")
    fun observeByOwner(ownerId: String): Flow<List<EventEntity>>

    /** F-21: privatni dogadjaji kojima pristupam, a nisu moji */
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

    /** F-15: moji dogadjaji koji jos nisu poslati serveru */
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

    /** Brise kes javnih dogadjaja, osim mojih, privatnih i prijavljenih */
    @Query(
        "DELETE FROM events " +
            "WHERE visibility = 'PUBLIC' " +
            "AND ownerId != :userId " +
            "AND id NOT IN (SELECT eventId FROM registrations)"
    )
    suspend fun deleteStalePublicCache(userId: String)

    /** Menja kes u jednoj transakciji da lista ne treperi */
    @Transaction
    suspend fun replacePublicCache(userId: String, events: List<EventEntity>) {
        deleteStalePublicCache(userId)
        events.forEach { upsert(it) }
    }
}
