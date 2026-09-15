package com.example.orbit.di

import android.content.Context
import androidx.room.Room
import com.example.orbit.data.local.AppDatabase
import com.example.orbit.data.local.dao.BlockedUserDao
import com.example.orbit.data.local.dao.EventDao
import com.example.orbit.data.local.dao.RatingDao
import com.example.orbit.data.local.dao.SavedEventDao
import com.example.orbit.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Nema vise seed-a */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "orbit.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideRatingDao(db: AppDatabase): RatingDao = db.ratingDao()

    @Provides
    fun provideBlockedUserDao(db: AppDatabase): BlockedUserDao = db.blockedUserDao()

    @Provides
    fun provideSavedEventDao(db: AppDatabase): SavedEventDao = db.savedEventDao()
}
