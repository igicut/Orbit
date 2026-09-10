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

    /**
     * The blocked list with names where known.
     *
     * A LEFT JOIN, not an inner one: a block must still appear even if that
     * user has never been fetched into the local users table.
     */
    @Query(
        "SELECT b.blockedId AS blockedId, u.displayName AS displayName " +
            "FROM blocked_users b " +
            "LEFT JOIN users u ON u.id = b.blockedId " +
            "WHERE b.blockerId = :blockerId " +
            "ORDER BY b.createdAt DESC"
    )
    fun observeBlockedWithNames(blockerId: String): Flow<List<BlockedUserRow>>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM blocked_users " +
            "WHERE blockerId = :blockerId AND blockedId = :blockedId)"
    )
    fun observeIsBlocked(blockerId: String, blockedId: String): Flow<Boolean>
}
