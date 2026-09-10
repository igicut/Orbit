package com.example.orbit.data.repository

import com.example.orbit.domain.model.User

import com.example.orbit.data.local.dao.BlockedUserRow

import com.example.orbit.domain.model.Event
import kotlinx.coroutines.flow.Flow


interface EventRepository {

    fun observeEvents(): Flow<List<Event>>

    fun observeEventsByOwner(ownerId: String): Flow<List<Event>>

    /** F-21 - private events obtained by code or P2P, rather than created here. */
    fun observeJoinedPrivateEvents(ownerId: String): Flow<List<Event>>

    /** Events the user bookmarked, whoever created them. */
    fun observeSavedEvents(): Flow<List<Event>>

    fun observeIsEventSaved(eventId: String): Flow<Boolean>

    suspend fun setEventSaved(eventId: String, saved: Boolean)

    fun observeEvent(id: String): Flow<Event?>

    suspend fun getEvent(id: String): Event?

    suspend fun saveEvent(event: Event)

    suspend fun deleteEvent(id: String)

    /**
     * F-12 - save changes to an existing event.
     *
     * Returns false when the server refused or was unreachable. The local copy
     * is updated either way, so the owner never loses what they typed.
     */
    suspend fun updateEvent(event: Event): Boolean

    suspend fun syncPublicEvents(latitude: Double, longitude: Double, radiusKm: Double)
    suspend fun pushEvent(event: Event)

    /**
     * F-21 - resolve an access code against the server and cache the result.
     *
     * The event is stored locally on success, so it stays available offline and
     * appears on the map like any other.
     */
    suspend fun joinEventByAccessCode(code: String): JoinResult

    /** F-27 - the rating this device gave an event, or null if none yet. */
    fun observeMyRating(eventId: String): Flow<Int?>

    /** F-27 - submit or change a rating. Returns false if the server refused. */
    suspend fun submitRating(eventId: String, value: Int, comment: String? = null): Boolean

    // ---- F-28: moderation -------------------------------------------------

    /** The organiser of an event, if this device has ever fetched them. */
    fun observeUser(userId: String): Flow<User?>

    /** Best-effort fetch of a user profile into the local cache. */
    suspend fun cacheUser(userId: String)

    /** Cached organiser names, keyed by user id, for rendering lists. */
    fun observeUserNames(): Flow<Map<String, String>>

    /**
     * F-13 - publish this device's profile so its events show a name.
     * Does nothing once it has succeeded. Returns false if the server was
     * unreachable, in which case it is retried on the next launch.
     */
    suspend fun registerCurrentUser(): Boolean

    /**
     * F-13 - rename this device and publish the new name.
     *
     * Returns false when the server could not be reached; the name is still
     * changed locally and republished on the next launch.
     */
    suspend fun updateDisplayName(name: String): Boolean

    fun observeIsBlocked(userId: String): Flow<Boolean>

    fun observeBlockedUsers(): Flow<List<BlockedUserRow>>

    suspend fun blockUser(userId: String)

    suspend fun unblockUser(userId: String)
}
