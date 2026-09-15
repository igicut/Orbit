package com.example.orbit.di

import com.example.orbit.BuildConfig
import com.example.orbit.data.local.CurrentUser
import com.example.orbit.data.remote.OrbitApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** F-14: sve za komunikaciju sa Ktor serverom */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** ignoreUnknownKeys da novo polje sa servera ne obori parsiranje */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(currentUser: CurrentUser): OkHttpClient =
        OkHttpClient.Builder()
            // F-13: X-User-Id se dodaje na svaki zahtev
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-User-Id", currentUser.id)
                    .build()
                chain.proceed(request)
            }
            // Logovanje tela samo u debug verziji
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            // Kratki timeout-i, bolje brzo pasti na lokalni kes
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType())
            )
            .build()

    @Provides
    @Singleton
    fun provideOrbitApiService(retrofit: Retrofit): OrbitApiService =
        retrofit.create(OrbitApiService::class.java)
}
