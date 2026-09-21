package com.example.orbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.orbit.data.local.entity.RatingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RatingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rating: RatingEntity)

    @Query("DELETE FROM ratings WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)

    /** Moje ocene sa servera zamenjuju lokalne */
    @Transaction
    suspend fun replaceForUser(userId: String, rows: List<RatingEntity>) {
        deleteForUser(userId)
        rows.forEach { upsert(it) }
    }

    /** Moja ocena kao Flow, radi i offline */
    @Query("SELECT * FROM ratings WHERE eventId = :eventId AND userId = :userId")
    fun observeByUserAndEvent(eventId: String, userId: String): Flow<RatingEntity?>
}
