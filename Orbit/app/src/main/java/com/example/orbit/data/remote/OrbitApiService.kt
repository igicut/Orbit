package com.example.orbit.data.remote

import com.example.orbit.data.remote.dto.RatingRequestDto

import com.example.orbit.data.remote.dto.AiSuggestRequestDto
import com.example.orbit.data.remote.dto.AiSuggestionDto
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

/** F-14: svi pozivi ka Ktor serveru */
interface OrbitApiService {

    // ---- dijagnostika ----

    @GET("health")
    suspend fun health(): HealthDto

    // ---- dogadjaji ----

    /** F-31: AI kategorija i opis; baca izuzetak na 502/503 */
    @POST("events/ai-suggest")
    suspend fun suggestEventDetails(@Body request: AiSuggestRequestDto): AiSuggestionDto

    /** F-12: javni dogadjaji u blizini; null radiusKm = bez limita */
    @GET("events")
    suspend fun searchEvents(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("radiusKm") radiusKm: Double?,
        @Query("category") category: String? = null,
        @Query("q") query: String? = null,
    ): List<EventDto>

    @GET("events/{id}")
    suspend fun getEvent(@Path("id") id: String): EventDto

    /** F-21: pridruzivanje privatnom dogadjaju preko koda */
    @GET("events/by-code/{code}")
    suspend fun getEventByAccessCode(@Path("code") code: String): EventDto

    /** Server vraca sacuvani dogadjaj, cuvamo njegovu verziju */
    @POST("events")
    suspend fun createEvent(@Body event: EventDto): Response<EventDto>

    /** Vraca dogadjaj kako je sacuvan na serveru */
    @PUT("events/{id}")
    suspend fun updateEvent(
        @Path("id") id: String,
        @Body event: EventDto,
    ): Response<EventDto>

    @DELETE("events/{id}")
    suspend fun deleteEvent(@Path("id") id: String): Response<Unit>

    // ---- ocene ----

    /** F-27: slanje ili izmena ocene, server racuna prosek */
    @PATCH("events/{id}/rating")
    suspend fun submitRating(
        @Path("id") eventId: String,
        @Body rating: RatingRequestDto,
    ): Response<EventDto>

    @GET("events/{id}/ratings")
    suspend fun getRatings(@Path("id") eventId: String): List<RatingDto>

    // ---- korisnici ----

    /** Registracija uredjaja, moze vise puta (upsert) */
    @POST("users")
    suspend fun registerUser(@Body user: UserDto): Response<UserDto>

    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): UserDto
}
