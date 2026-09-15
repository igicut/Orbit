package com.example.orbit.data.repository

import com.example.orbit.domain.model.User

import com.example.orbit.data.local.dao.BlockedUserRow

import com.example.orbit.domain.model.AiSuggestion
import com.example.orbit.domain.model.AttendedEvent
import com.example.orbit.domain.model.Attendee
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.UserLocation
import kotlinx.coroutines.flow.Flow


interface EventRepository {

    fun observeEvents(): Flow<List<Event>>

    fun observeEventsByOwner(ownerId: String): Flow<List<Event>>

    /** F-21: privatni dogadjaji dobijeni kodom ili P2P */
    fun observeJoinedPrivateEvents(ownerId: String): Flow<List<Event>>

    /** Dogadjaji na koje sam prijavljen; za listu i podsetnike */
    fun observeRegisteredEvents(): Flow<List<Event>>

    fun observeIsRegistered(eventId: String): Flow<Boolean>

    /** Prijava na serveru pa lokalno; mesto se zauzima atomski */
    suspend fun registerForEvent(eventId: String): RegistrationResult

    suspend fun cancelRegistration(eventId: String): RegistrationResult

    /** Moj potvrdjen dolazak; otkljucava ocenu */
    fun observeHasAttended(eventId: String): Flow<Boolean>

    /** F-36: poseceni dogadjaji iz Room-a, puni ih sync naloga */
    fun observeAttendedEvents(): Flow<List<AttendedEvent>>

    /** Server proverava vreme, udaljenost i mesto; bez prijave je i prijavljuje */
    suspend fun checkIn(eventId: String, location: UserLocation): CheckInResult

    /** Spisak za organizatora, uvek sa servera; null bez mreze ili dozvole */
    suspend fun getAttendees(eventId: String): List<Attendee>?

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

    /** F-15: salje dogadjaje napravljene bez mreze */
    suspend fun pushPendingEvents()

    /** F-13: vraca podatke naloga sa servera u Room; false bez servera */
    suspend fun syncAccountData(): Boolean

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

    /** F-13: objavljuje ime korisnika; false ako nema servera */
    suspend fun publishDisplayName(): Boolean

    /** F-13: menja ime i objavljuje ga serveru */
    suspend fun updateDisplayName(name: String): Boolean

    fun observeIsBlocked(userId: String): Flow<Boolean>

    fun observeBlockedUsers(): Flow<List<BlockedUserRow>>

    /** Na serveru pa lokalno; false ako server nije dostupan */
    suspend fun blockUser(userId: String): Boolean

    suspend fun unblockUser(userId: String): Boolean
}
