package com.example.orbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.orbit.data.local.entity.EventEntity
import com.example.orbit.data.local.entity.RegistrationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RegistrationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: RegistrationEntity)

    @Query("DELETE FROM registrations WHERE eventId = :eventId")
    suspend fun delete(eventId: String)

    @Query("DELETE FROM registrations")
    suspend fun deleteAll()

    /** Posle sync-a lokalne prijave su iste kao na serveru */
    @Transaction
    suspend fun replaceAll(rows: List<RegistrationEntity>) {
        deleteAll()
        rows.forEach { insert(it) }
    }

    @Query("SELECT EXISTS(SELECT 1 FROM registrations WHERE eventId = :eventId)")
    fun observeIsRegistered(eventId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM registrations WHERE eventId = :eventId)")
    suspend fun isRegistered(eventId: String): Boolean

    /** Dogadjaji na koje sam prijavljen, bez blokiranih organizatora */
    @Query(
        "SELECT e.* FROM events e " +
            "INNER JOIN registrations r ON r.eventId = e.id " +
            "WHERE e.ownerId NOT IN " +
            "(SELECT blockedId FROM blocked_users WHERE blockerId = :userId) " +
            "ORDER BY e.startTime ASC"
    )
    fun observeRegisteredEvents(userId: String): Flow<List<EventEntity>>
}
