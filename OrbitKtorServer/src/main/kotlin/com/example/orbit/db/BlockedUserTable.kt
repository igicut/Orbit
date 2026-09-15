package com.example.orbit.db

import org.jetbrains.exposed.v1.core.Table

/** F-28: ko je koga blokirao, prati nalog na svaki uredjaj */
object BlockedUsers : Table("blocked_users") {
    val blockerId = varchar("blocker_id", 36)
    val blockedId = varchar("blocked_id", 36)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(blockerId, blockedId)
}
