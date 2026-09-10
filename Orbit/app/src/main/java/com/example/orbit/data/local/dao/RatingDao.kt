package com.example.orbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.orbit.data.local.entity.RatingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RatingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rating: RatingEntity)

    @Query("SELECT * FROM ratings WHERE eventId = :eventId ORDER BY createdAt DESC")
    fun observeForEvent(eventId: String): Flow<List<RatingEntity>>

    @Query("SELECT * FROM ratings WHERE eventId = :eventId AND userId = :userId")
    suspend fun getByUserAndEvent(eventId: String, userId: String): RatingEntity?

    /**
     * The rating this device gave, as a live value so the stars stay in step
     * with what was submitted - including offline, from the local copy.
     */
    @Query("SELECT * FROM ratings WHERE eventId = :eventId AND userId = :userId")
    fun observeByUserAndEvent(eventId: String, userId: String): Flow<RatingEntity?>
}
