package com.example.orbit.data.remote

import com.example.orbit.data.remote.dto.RatingRequestDto

import com.example.orbit.data.remote.dto.AiSuggestRequestDto
import com.example.orbit.data.remote.dto.AiSuggestionDto
import com.example.orbit.data.remote.dto.ParsedSearchDto
import com.example.orbit.data.remote.dto.SearchParseRequestDto
import com.example.orbit.data.remote.dto.AttendeeDto
import com.example.orbit.data.remote.dto.AuthResponseDto
import com.example.orbit.data.remote.dto.CheckInRequestDto
import com.example.orbit.data.remote.dto.CancelEventRequest
import com.example.orbit.data.remote.dto.EventDto
import com.example.orbit.data.remote.dto.HealthDto
import com.example.orbit.data.remote.dto.ImageUploadDto
import com.example.orbit.data.remote.dto.JoinRequestDto
import com.example.orbit.data.remote.dto.LoginRequestDto
import com.example.orbit.data.remote.dto.ProfileUpdateDto
import com.example.orbit.data.remote.dto.RatingDto
import com.example.orbit.data.remote.dto.ResetPasswordRequestDto
import com.example.orbit.data.remote.dto.SignUpRequestDto
import com.example.orbit.data.remote.dto.UserDto
import com.example.orbit.data.remote.dto.UserSyncDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** F-14: svi pozivi ka Ktor serveru */
interface OrbitApiService {

    // ---- dijagnostika ----

    @GET("health")
    suspend fun health(): HealthDto

    // ---- nalog ----

    /** F-13: 201 sa tokenom, 409 ako email vec postoji */
    @POST("auth/signup")
    suspend fun signUp(@Body request: SignUpRequestDto): Response<AuthResponseDto>

    /** F-13: 200 sa tokenom, 401 za pogresan email ili lozinku */
    @POST("auth/login")
    suspend fun logIn(@Body request: LoginRequestDto): Response<AuthResponseDto>

    /** Zamena zaboravljene lozinke; vraca token kao i prijava */
    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequestDto): Response<AuthResponseDto>

    // ---- dogadjaji ----

    /** F-31: AI kategorija i opis; baca izuzetak na 502/503 */
    @POST("events/ai-suggest")
    suspend fun suggestEventDetails(@Body request: AiSuggestRequestDto): AiSuggestionDto

    /** F-43: 503 bez kljuca, 502 kad AI padne, 400 prazan ili predug tekst */
    @POST("search/parse")
    suspend fun parseSearch(@Body request: SearchParseRequestDto): ParsedSearchDto

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

    /** F-21: kod otvara privatni dogadjaj, server pamti clanstvo */
    @POST("events/join")
    suspend fun joinEvent(@Body request: JoinRequestDto): EventDto

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

    // ---- slike ----

    /** F-37: 201 sa putanjom; 413 prevelika, 415 nepodrzan format */
    @Multipart
    @POST("images")
    suspend fun uploadImage(@Part file: MultipartBody.Part): Response<ImageUploadDto>

    /** F-39: otkazivanje; 403 nije vlasnik, 409 vec otkazan ili zavrsen */
    @POST("events/{id}/cancel")
    suspend fun cancelEvent(
        @Path("id") eventId: String,
        @Body body: CancelEventRequest,
    ): Response<EventDto>

    // ---- prijave i dolasci ----

    /** 200 sa brojem prijava; 409 kad je popunjeno ili je poceo */
    @PUT("events/{id}/registration")
    suspend fun registerForEvent(@Path("id") eventId: String): Response<EventDto>

    @DELETE("events/{id}/registration")
    suspend fun cancelRegistration(@Path("id") eventId: String): Response<EventDto>

    /** 200 sa brojem prijava; 403 predaleko, 409 van prozora ili bez mesta */
    @PUT("events/{id}/attendance")
    suspend fun checkIn(
        @Path("id") eventId: String,
        @Body request: CheckInRequestDto,
    ): Response<EventDto>

    /** Samo organizator, ostali dobijaju 403 */
    @GET("events/{id}/attendees")
    suspend fun getAttendees(@Path("id") eventId: String): List<AttendeeDto>

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

    @GET("users/{id}")
    suspend fun getUser(@Path("id") id: String): UserDto

    /** Profil organizatora: javni dogadjaji, i buduci i prosli; 404 kad postoji blokada */
    @GET("users/{id}/events")
    suspend fun getUserEvents(@Path("id") id: String): List<EventDto>

    // ---- podaci naloga ----

    /** Menja samo ime; interesovanja ostaju na serveru */
    @PATCH("users/me")
    suspend fun updateProfile(@Body request: ProfileUpdateDto): Response<UserDto>

    /** Moji dogadjaji, pridruzeni, prijavljeni, blokirani i ocene */
    @GET("users/me/sync")
    suspend fun getAccountData(): UserSyncDto

    @PUT("users/me/blocked/{blockedId}")
    suspend fun blockUser(@Path("blockedId") userId: String): Response<Unit>

    @DELETE("users/me/blocked/{blockedId}")
    suspend fun unblockUser(@Path("blockedId") userId: String): Response<Unit>
}
