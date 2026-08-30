package com.example.orbit.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.orbit.data.local.entity.EventEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY startTime ASC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE ownerId = :ownerId ORDER BY startTime ASC")
    fun observeByOwner(ownerId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    fun observeById(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: String): EventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(event: EventEntity)
}
