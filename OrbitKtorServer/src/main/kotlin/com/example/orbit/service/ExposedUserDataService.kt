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

    /** Javni vide svi; privatni samo vlasnik i clanovi */
    suspend fun canAccess(event: ExposedEvent, userId: String): Boolean {
        if (event.visibility == Visibility.PUBLIC || event.ownerId == userId) return true
        return suspendTransaction(database) {
            EventMembers.selectAll()
                .where { (EventMembers.eventId eq event.id) and (EventMembers.userId eq userId) }
                .limit(1)
                .toList()
                .isNotEmpty()
        }
    }
}
