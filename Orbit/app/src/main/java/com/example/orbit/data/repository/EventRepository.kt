package com.example.orbit.data.repository

import com.example.orbit.domain.model.User

import com.example.orbit.data.local.dao.BlockedUserRow

import com.example.orbit.domain.model.AiSuggestion
import com.example.orbit.domain.model.Event
import kotlinx.coroutines.flow.Flow


interface EventRepository {

    fun observeEvents(): Flow<List<Event>>

    fun observeEventsByOwner(ownerId: String): Flow<List<Event>>

    /** F-21: privatni dogadjaji dobijeni kodom ili P2P */
    fun observeJoinedPrivateEvents(ownerId: String): Flow<List<Event>>

    /** Sacuvani dogadjaji, bez obzira ko ih je napravio */
    fun observeSavedEvents(): Flow<List<Event>>

    fun observeIsEventSaved(eventId: String): Flow<Boolean>

    suspend fun setEventSaved(eventId: String, saved: Boolean)

    fun observeEvent(id: String): Flow<Event?>

    suspend fun getEvent(id: String): Event?

    /** F-31: AI predlog kategorije i opisa, null na gresku */
    suspend fun suggestEventDetails(title: String, description: String): AiSuggestion?

    suspend fun saveEvent(event: Event)

    /** F-12: brise na serveru pa lokalno; false ako ne uspe */
    suspend fun deleteEvent(id: String): Boolean

    /** F-12: izmena dogadjaja; false ako server odbije */
    suspend fun updateEvent(event: Event): Boolean

    /** null radiusKm skida sve javne dogadjaje */
    suspend fun syncPublicEvents(latitude: Double, longitude: Double, radiusKm: Double?)
    suspend fun pushEvent(event: Event)

    /** F-21: proverava kod na serveru i kesira dogadjaj */
    suspend fun joinEventByAccessCode(code: String): JoinResult

    /** F-27: moja ocena dogadjaja, null ako je nema */
    fun observeMyRating(eventId: String): Flow<Int?>

    /** F-27: slanje ili izmena ocene; false ako server odbije */
    suspend fun submitRating(eventId: String, value: Int, comment: String? = null): Boolean

    // ---- F-28: moderacija ----

    /** Organizator dogadjaja, ako je ikad preuzet */
    fun observeUser(userId: String): Flow<User?>

    /** Pokusava da kesira profil korisnika */
    suspend fun cacheUser(userId: String)

    /** Kesirana imena organizatora po id-ju */
    fun observeUserNames(): Flow<Map<String, String>>

    /** F-13: objavljuje profil uredjaja; false ako nema servera */
    suspend fun registerCurrentUser(): Boolean

    /** F-13: menja ime i objavljuje ga serveru */
    suspend fun updateDisplayName(name: String): Boolean

    fun observeIsBlocked(userId: String): Flow<Boolean>

    fun observeBlockedUsers(): Flow<List<BlockedUserRow>>

    suspend fun blockUser(userId: String)

    suspend fun unblockUser(userId: String)
}
