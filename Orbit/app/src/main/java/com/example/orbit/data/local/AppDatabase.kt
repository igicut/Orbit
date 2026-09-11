package com.example.orbit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.orbit.data.local.dao.BlockedUserDao
import com.example.orbit.data.local.dao.EventDao
import com.example.orbit.data.local.dao.RatingDao
import com.example.orbit.data.local.dao.SavedEventDao
import com.example.orbit.data.local.dao.UserDao
import com.example.orbit.data.local.entity.BlockedUserEntity
import com.example.orbit.data.local.entity.EventEntity
import com.example.orbit.data.local.entity.RatingEntity
import com.example.orbit.data.local.entity.SavedEventEntity
import com.example.orbit.data.local.entity.UserEntity

@Database(
    entities = [
        EventEntity::class,
        UserEntity::class,
        RatingEntity::class,
        BlockedUserEntity::class,
        SavedEventEntity::class,
    ],
    // Bumped for the saved_events table. Destructive migration is on, so
    // existing local data is dropped and refetched from the server on the next
    // sync.
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun userDao(): UserDao
    abstract fun ratingDao(): RatingDao
    abstract fun blockedUserDao(): BlockedUserDao
    abstract fun savedEventDao(): SavedEventDao
}
