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

/**
 * F-14 - everything needed to talk to the Ktor server.
 *
 * Hilt builds this once and injects OrbitApiService wherever it is asked for, so
 * no class ever constructs its own Retrofit instance.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * ignoreUnknownKeys matters both ways. The server may add a field before the
     * app knows about it; without this flag every response would fail to parse.
     */
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
            // F-13 - identity. Attached to every request in one place, so no
            // individual call has to remember it.
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-User-Id", currentUser.id)
                    .build()
                chain.proceed(request)
            }
            // Full request and response bodies in Logcat on debug builds only -
            // this would leak user data if it ever shipped enabled.
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            // Short timeouts: the server is on the same Wi-Fi, so a slow response
            // means it is unreachable. Better to fail fast and fall back to the
            // local cache than to leave a spinner on screen for 30 seconds.
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
