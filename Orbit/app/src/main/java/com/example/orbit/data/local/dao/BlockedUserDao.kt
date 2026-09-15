package com.example.orbit.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.orbit.data.local.entity.BlockedUserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedUserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun block(row: BlockedUserEntity)

    @Query("DELETE FROM blocked_users WHERE blockerId = :blockerId AND blockedId = :blockedId")
    suspend fun unblock(blockerId: String, blockedId: String)

    @Query("DELETE FROM blocked_users WHERE blockerId = :blockerId")
    suspend fun deleteForBlocker(blockerId: String)

    /** Posle sync-a blokirani su isti kao na serveru */
    @Transaction
    suspend fun replaceForBlocker(blockerId: String, rows: List<BlockedUserEntity>) {
        deleteForBlocker(blockerId)
        rows.forEach { block(it) }
    }

    @Query("SELECT blockedId FROM blocked_users WHERE blockerId = :blockerId")
    fun observeBlockedIds(blockerId: String): Flow<List<String>>

    /** Blokirani sa imenima, LEFT JOIN da ne nestanu */
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
