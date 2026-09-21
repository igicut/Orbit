package com.example.orbit.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.orbit.data.local.dao.AttendanceDao
import com.example.orbit.data.local.dao.BlockedUserDao
import com.example.orbit.data.local.dao.EventDao
import com.example.orbit.data.local.dao.RatingDao
import com.example.orbit.data.local.dao.RegistrationDao
import com.example.orbit.data.local.dao.UserDao
import com.example.orbit.data.local.entity.AttendanceEntity
import com.example.orbit.data.local.entity.BlockedUserEntity
import com.example.orbit.data.local.entity.EventEntity
import com.example.orbit.data.local.entity.RatingEntity
import com.example.orbit.data.local.entity.RegistrationEntity
import com.example.orbit.data.local.entity.UserEntity

@Database(
    entities = [
        EventEntity::class,
        UserEntity::class,
        RatingEntity::class,
        BlockedUserEntity::class,
        RegistrationEntity::class,
        AttendanceEntity::class,
    ],
    // Verzija 4: potvrde dolaska; lokalni kes se brise i ponovo puni sa servera
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun userDao(): UserDao
    abstract fun ratingDao(): RatingDao
    abstract fun blockedUserDao(): BlockedUserDao
    abstract fun registrationDao(): RegistrationDao
    abstract fun attendanceDao(): AttendanceDao
}
