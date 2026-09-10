package com.example.orbit.data.remote

import com.example.orbit.data.remote.dto.RatingRequestDto

import com.example.orbit.data.remote.dto.EventDto
import com.example.orbit.data.remote.dto.HealthDto
import com.example.orbit.data.remote.dto.RatingDto
import com.example.orbit.data.remote.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * F-14 - every call the app can make to the Ktor server.
 *
 * You write the interface; Retrofit generates the implementation at runtime.
 *
 * Notes on the shapes here:
 *  - Paths have NO leading slash. A leading slash would make them absolute and
 *    discard the path part of the base URL.
 *  - Functions returning a value throw on a non-2xx response, so they are for
 *    calls where a failure is genuinely exceptional.
 *  - Functions returning Response<Unit> let you inspect the status code without
 *    an exception, which is what you want for create/update/delete.
 *  - The X-User-Id header is added to every request by an interceptor in
 *    NetworkModule, so it never appears here.
 */
interface OrbitApiService {

    // ---- diagnostics -------------------------------------------------------

    @GET("health")
    suspend fun health(): HealthDto

    // ---- events ------------------------------------------------------------

    /**
     * F-12 - public events near a point. `category` is optional; leave it null
     * for all categories.
     */
    @GET("events")
    suspend fun searchEvents(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("radiusKm") radiusKm: Double,
        @Query("category") category: String? = null,
        @Query("q") query: String? = null,
    ): List<EventDto>

    @GET("events/{id}")
    suspend fun getEvent(@Path("id") id: String): EventDto

    /** F-21 - join a private event using the code the organiser shared. */
    @GET("events/by-code/{code}")
    suspend fun getEventByAccessCode(@Path("code") code: String): EventDto

    /**
     * The server returns the event as it stored it. That matters: it stamps the
     * owner from the X-User-Id header and resets the rating fields, so the copy
     * it sends back is the authoritative one to keep locally.
     */
    @POST("events")
    suspend fun createEvent(@Body event: EventDto): Response<EventDto>

    /** Responds with the event as stored, so the client can adopt the result. */
    @PUT("events/{id}")
    suspend fun updateEvent(
        @Path("id") id: String,
        @Body event: EventDto,
    ): Response<EventDto>

    @DELETE("events/{id}")
    suspend fun deleteEvent(@Path("id") id: String): Response<Unit>

    // ---- ratings -----------------------------------------------------------

    /** F-27 - submit or change a rating. The server recomputes the average. */
    @PATCH("events/{id}/rating")
    suspend fun submitRating(
        @Path("id") eventId: String,
        @Body rating: RatingRequestDto,
    ): Response<EventDto>

    @GET("events/{id}/ratings")
    suspend fun getRatings(@Path("id") eventId: String): List<RatingDto>

    // ---- users -------------------------------------------------------------

    /**
     * Register this device so other people see a name instead of a UUID.
     * Safe to call more than once - the server treats it as register-or-update.
     */
    @POST("users")
    suspend fun registerUser(@Body user: UserDto): Response<UserDto>

    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): UserDto
}
