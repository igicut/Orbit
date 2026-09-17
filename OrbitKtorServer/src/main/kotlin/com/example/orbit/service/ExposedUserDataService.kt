package com.example.orbit.service

import com.example.orbit.db.BlockedUsers
import com.example.orbit.db.EventMembers
import com.example.orbit.model.ExposedEvent
import com.example.orbit.model.Visibility
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/** F-13: podaci naloga: blokirani i clanstva */
class ExposedUserDataService(private val database: R2dbcDatabase) {

    // ---- F-28: blokiranje ----

    suspend fun block(blockerId: String, blockedId: String) {
        suspendTransaction(database) {
            BlockedUsers.insertIgnore {
                it[BlockedUsers.blockerId] = blockerId
                it[BlockedUsers.blockedId] = blockedId
                it[createdAt] = System.currentTimeMillis()
            }
        }
    }

    suspend fun unblock(blockerId: String, blockedId: String) {
        suspendTransaction(database) {
            BlockedUsers.deleteWhere {
                (BlockedUsers.blockerId eq blockerId) and (BlockedUsers.blockedId eq blockedId)
            }
        }
    }

    suspend fun blockedIds(blockerId: String): List<String> = suspendTransaction(database) {
        BlockedUsers.selectAll()
            .where { BlockedUsers.blockerId eq blockerId }
            .map { it[BlockedUsers.blockedId] }
            .toList()
    }

    /**
     * Vlasnici cije dogadjaje ovaj nalog ne sme da vidi: i oni koje je blokirao,
     * i oni koji su blokirali njega. Drugi smer se ne sme vratiti aplikaciji kao spisak,
     * jer bi korisnik saznao ko ga je blokirao; sluzi samo za filtriranje na serveru.
     */
    suspend fun hiddenOwnerIds(userId: String): Set<String> = suspendTransaction(database) {
        BlockedUsers.selectAll()
            .where { (BlockedUsers.blockerId eq userId) or (BlockedUsers.blockedId eq userId) }
            .map { row ->
                if (row[BlockedUsers.blockerId] == userId) row[BlockedUsers.blockedId]
                else row[BlockedUsers.blockerId]
            }
            .toList()
            .toSet()
    }

    /** F-28: postoji li blokada izmedju dva naloga, u bilo kom smeru */
    private suspend fun isBlockedEitherWay(first: String, second: String): Boolean =
        suspendTransaction(database) {
            BlockedUsers.selectAll()
                .where {
                    ((BlockedUsers.blockerId eq first) and (BlockedUsers.blockedId eq second)) or
                        ((BlockedUsers.blockerId eq second) and (BlockedUsers.blockedId eq first))
                }
                .limit(1)
                .toList()
                .isNotEmpty()
        }

    // ---- F-21: clanstva u privatnim dogadjajima ----

    suspend fun join(eventId: String, userId: String) {
        suspendTransaction(database) {
            EventMembers.insertIgnore {
                it[EventMembers.eventId] = eventId
                it[EventMembers.userId] = userId
                it[joinedAt] = System.currentTimeMillis()
            }
        }
    }

    suspend fun joinedEventIds(userId: String): List<String> = suspendTransaction(database) {
        EventMembers.selectAll()
            .where { EventMembers.userId eq userId }
            .map { it[EventMembers.eventId] }
            .toList()
    }

    /**
     * Svoj dogadjaj se uvek vidi; blokada u bilo kom smeru ga krije (F-28);
     * inace javne vide svi, a privatne samo clanovi (F-21).
     */
    suspend fun canAccess(event: ExposedEvent, userId: String): Boolean {
        if (event.ownerId == userId) return true
        if (isBlockedEitherWay(userId, event.ownerId)) return false
        if (event.visibility == Visibility.PUBLIC) return true
        return suspendTransaction(database) {
            EventMembers.selectAll()
                .where { (EventMembers.eventId eq event.id) and (EventMembers.userId eq userId) }
                .limit(1)
                .toList()
                .isNotEmpty()
        }
    }
}
