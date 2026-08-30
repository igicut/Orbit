package com.example.orbit.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.orbit.data.local.AppDatabase
import com.example.orbit.data.local.dao.BlockedUserDao
import com.example.orbit.data.local.dao.EventDao
import com.example.orbit.data.local.dao.RatingDao
import com.example.orbit.data.local.dao.UserDao
import com.example.orbit.data.local.seedDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        eventDaoProvider: Provider<EventDao>,
        scope: CoroutineScope,
    ): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "orbit.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    scope.launch { seedDatabase(eventDaoProvider.get()) }
                }
            })
            .build()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideRatingDao(db: AppDatabase): RatingDao = db.ratingDao()

    @Provides
    fun provideBlockedUserDao(db: AppDatabase): BlockedUserDao = db.blockedUserDao()
}
