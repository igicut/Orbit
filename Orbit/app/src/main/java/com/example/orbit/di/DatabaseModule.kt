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

    /**
     * No seeding callback any more.
     *
     * seedDatabase() wrote two sample events on first launch, back when the app
     * had nothing else to show. The Ktor server now supplies real data through
     * syncPublicEvents(), so planting fabricated rows locally would only put
     * events in the list that exist on no server and belong to nobody - and
     * because they were written with syncedToBackend = false, the retry pass
     * would keep trying to upload them.
     *
     * The seeder is kept in data/local/DatabaseSeeder.kt but nothing calls it.
     */
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
