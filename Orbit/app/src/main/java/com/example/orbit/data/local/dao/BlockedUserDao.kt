package com.example.orbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.orbit.data.local.entity.BlockedUserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedUserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun block(row: BlockedUserEntity)

    @Query("DELETE FROM blocked_users WHERE blockerId = :blockerId AND blockedId = :blockedId")
    suspend fun unblock(blockerId: String, blockedId: String)

    @Query("SELECT blockedId FROM blocked_users WHERE blockerId = :blockerId")
    fun observeBlockedIds(blockerId: String): Flow<List<String>>
}
