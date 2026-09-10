package com.example.orbit.data.repository

import com.example.orbit.data.remote.dto.EventDto

import com.example.orbit.data.remote.dto.UserDto

import com.example.orbit.domain.model.User

import com.example.orbit.data.local.entity.UserEntity

import com.example.orbit.data.local.entity.BlockedUserEntity

import com.example.orbit.data.local.dao.UserDao

import com.example.orbit.data.local.dao.BlockedUserRow

import com.example.orbit.data.local.dao.BlockedUserDao

import com.example.orbit.data.remote.dto.RatingRequestDto

import com.example.orbit.data.local.entity.RatingEntity

import com.example.orbit.data.local.dao.RatingDao

import com.example.orbit.data.local.CurrentUser

import com.example.orbit.data.local.entity.SavedEventEntity

import com.example.orbit.data.local.dao.SavedEventDao

import com.example.orbit.data.local.dao.EventDao
import com.example.orbit.data.remote.OrbitApiService
import com.example.orbit.data.remote.toDomain as dtoToDomain
import com.example.orbit.data.remote.toDto
import com.example.orbit.data.local.toDomain
import com.example.orbit.data.local.toEntity
import com.example.orbit.domain.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_CONFLICT = 409

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    private val currentUser: CurrentUser,
    private val savedEventDao: SavedEventDao,
    private val ratingDao: RatingDao,
    private val userDao: UserDao,
    private val blockedUserDao: BlockedUserDao,
    private val api: OrbitApiService,
) : EventRepository {

    override fun observeEvents(): Flow<List<Event>> =
        eventDao.observeAll(currentUser.id).map { rows -> rows.map { it.toDomain() } }

    override fun observeEventsByOwner(ownerId: String): Flow<List<Event>> =
        eventDao.observeByOwner(ownerId).map { rows -> rows.map { it.toDomain() } }

    override fun observeJoinedPrivateEvents(ownerId: String): Flow<List<Event>> =
        eventDao.observeJoinedPrivate(ownerId).map { rows -> rows.map { it.toDomain() } }

    override fun observeSavedEvents(): Flow<List<Event>> =
        savedEventDao.observeSavedEvents(currentUser.id).map { rows -> rows.map { it.toDomain() } }

    override fun observeIsEventSaved(eventId: String): Flow<Boolean> =
        savedEventDao.observeIsSaved(eventId)

    override suspend fun setEventSaved(eventId: String, saved: Boolean) {
        if (saved) {
            savedEventDao.save(
                SavedEventEntity(eventId = eventId, savedAt = System.currentTimeMillis())
            )
        } else {
            savedEventDao.unsave(eventId)
        }
    }

    override fun observeEvent(id: String): Flow<Event?> =
        eventDao.observeById(id).map { row -> row?.toDomain() }

    override suspend fun getEvent(id: String): Event? = eventDao.getById(id)?.toDomain()

    override suspend fun saveEvent(event: Event) = eventDao.upsert(event.toEntity())

    override suspend fun deleteEvent(id: String) = eventDao.deleteById(id)

    override suspend fun updateEvent(event: Event): Boolean {
        // Local first, so the owner's work survives a failed request.
        eventDao.upsert(event.toEntity())

        val response = try {
            api.updateEvent(event.id, event.toDto())
        } catch (e: IOException) {
            return false
        } catch (e: HttpException) {
            return false
        }

        // The server applies the same limits and may have rejected the change,
        // so its copy is the one worth keeping.
        response.body()?.let { eventDao.upsert(it.dtoToDomain().toEntity()) }
        return response.isSuccessful
    }

    /**
     * F-15 - pull public events near a point and cache them locally.
     *
     * On any network failure this returns quietly and leaves the cache alone, so
     * the list screen keeps showing the last known events instead of emptying.
     * The caller does not need to know whether the data came from the server.
     */
    /**
     * F-15 - retry anything created while the server was unreachable.
     *
     * Runs before the download rather than after, so an event that uploads now
     * comes back in the same refresh instead of waiting for the next one.
     *
     * Each push swallows its own failure, so one unreachable event cannot stop
     * the rest of the queue.
     */
    private suspend fun pushPendingEvents() {
        eventDao.getPendingUploads(currentUser.id).forEach { entity ->
            pushEvent(entity.toDomain())
        }
    }

    override suspend fun syncPublicEvents(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ) {
        // Send before receiving. If the network is down both fail harmlessly and
        // the pending rows keep their flag for the next attempt.
        pushPendingEvents()

        val remote = try {
            api.searchEvents(latitude, longitude, radiusKm)
        } catch (e: IOException) {
            return          // no network, or the server is not running
        } catch (e: HttpException) {
            return          // server answered, but with an error status
        }

        // Replace the cached copy rather than adding to it, so the local
        // database tracks the last search instead of growing without limit.
        // Events you own, saved or joined are kept - see deleteStalePublicCache.
        //
        // This runs only AFTER a successful fetch. A failed request returns
        // above without touching anything, so going offline leaves the previous
        // results readable instead of wiping the screen.
        eventDao.replacePublicCache(
            userId = currentUser.id,
            events = remote.map { dto -> dto.dtoToDomain().toEntity() },
        )
        cacheOwnerNames(remote)
    }

    /**
     * F-15 - push a locally created event to the server.
     *
     * If it fails the event simply stays in Room with syncedToBackend = false,
     * which is the flag a later retry pass looks for. Nothing is lost and the
     * user is not interrupted.
     */
    /**
     * F-21 - look a private event up by its access code and cache it.
     *
     * The code is normalised before sending: it is generated uppercase, and
     * people type lowercase. Doing it here as well as on the server means a
     * stray space cannot cause a puzzling "not found".
     */
    override suspend fun joinEventByAccessCode(code: String): JoinResult {
        val normalised = code.trim().uppercase()
        if (normalised.isEmpty()) return JoinResult.NotFound

        val dto = try {
            api.getEventByAccessCode(normalised)
        } catch (e: HttpException) {
            // 404 means the code is wrong; anything else is the server failing.
            return if (e.code() == 404) JoinResult.NotFound else JoinResult.NetworkError
        } catch (e: IOException) {
            return JoinResult.NetworkError
        }

        eventDao.upsert(dto.dtoToDomain().toEntity())
        cacheOwnerNames(listOf(dto))
        return JoinResult.Success(dto.id, dto.title)
    }

    override fun observeMyRating(eventId: String): Flow<Int?> =
        ratingDao.observeByUserAndEvent(eventId, currentUser.id).map { it?.value }

    /**
     * F-27 - send a rating and take the server's word for the new average.
     *
     * The response is the updated event, so the freshly computed avgRating and
     * ratingCount are written straight into Room. Computing the average on the
     * client would only ever be a guess - it cannot see other people's ratings.
     */
    override suspend fun submitRating(eventId: String, value: Int, comment: String?): Boolean {
        val response = try {
            api.submitRating(eventId, RatingRequestDto(value = value, comment = comment))
        } catch (e: IOException) {
            return false
        } catch (e: HttpException) {
            return false
        }

        val updated = response.body()
        if (!response.isSuccessful || updated == null) return false

        eventDao.upsert(updated.dtoToDomain().toEntity())

        // Keep a local copy so the stars show what you gave, with no network.
        // The id is derived from the pair, so re-rating replaces rather than
        // piling up rows - the server enforces the same rule with its index.
        ratingDao.upsert(
            RatingEntity(
                id = eventId + ":" + currentUser.id,
                eventId = eventId,
                userId = currentUser.id,
                value = value,
                comment = comment,
                createdAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    // ---- F-28: moderation -------------------------------------------------

    override fun observeUser(userId: String): Flow<User?> =
        userDao.observeById(userId).map { it?.toDomain() }

    /**
     * Pull a user profile into the local cache.
     *
     * Best effort by design: this only exists so an event can say who organised
     * it and a block can show a name. Failing to reach the server is not worth
     * surfacing - the screen falls back to showing the raw id.
     */
    override suspend fun cacheUser(userId: String) {
        val dto = try {
            api.getUser(userId)
        } catch (e: IOException) {
            return
        } catch (e: HttpException) {
            return
        }
        userDao.upsert(
            UserEntity(
                id = dto.id,
                displayName = dto.displayName,
                interests = dto.interests,
            )
        )
    }

    override suspend fun registerCurrentUser(): Boolean {
        if (currentUser.isRegistered) return true

        val profile = UserDto(
            id = currentUser.id,
            displayName = currentUser.displayName,
        )

        val response = try {
            api.registerUser(profile)
        } catch (e: IOException) {
            return false        // offline - tried again next launch
        } catch (e: HttpException) {
            return false
        }

        if (!response.isSuccessful) return false

        currentUser.isRegistered = true

        // Cache our own profile too, so the users table is consistent whether a
        // name came from us or from someone else.
        userDao.upsert(
            UserEntity(
                id = profile.id,
                displayName = profile.displayName,
                interests = profile.interests,
            )
        )
        return true
    }

    override suspend fun updateDisplayName(name: String): Boolean {
        // The setter clears the registered flag, so registerCurrentUser() below
        // sends the new name rather than short-circuiting on "already done".
        currentUser.displayName = name.trim()
        return registerCurrentUser()
    }

    override fun observeUserNames(): Flow<Map<String, String>> =
        userDao.observeAll().map { rows -> rows.associate { it.id to it.displayName } }

    /**
     * Store the organiser names that arrived alongside a batch of events.
     *
     * The server sends ownerName with every event, so a sync populates the local
     * users table as a side effect - no extra request per organiser, and names
     * survive for the blocked list and the detail screen too.
     */
    private suspend fun cacheOwnerNames(events: List<EventDto>) {
        events.forEach { dto ->
            val name = dto.ownerName ?: return@forEach
            userDao.upsert(UserEntity(id = dto.ownerId, displayName = name, interests = emptyList()))
        }
    }

    override fun observeIsBlocked(userId: String): Flow<Boolean> =
        blockedUserDao.observeIsBlocked(currentUser.id, userId)

    override fun observeBlockedUsers(): Flow<List<BlockedUserRow>> =
        blockedUserDao.observeBlockedWithNames(currentUser.id)

    /**
     * Blocking is entirely local: no request is sent anywhere.
     *
     * Identity here is a UUID generated on this device, so a server-side block
     * list would be orphaned the moment the app is reinstalled and a new id
     * generated. Keeping it local also means it works with no connection, and
     * the list of people you dislike never leaves the phone.
     *
     * Blocking yourself is silently ignored - it would hide your own events.
     */
    override suspend fun blockUser(userId: String) {
        if (userId == currentUser.id) return

        blockedUserDao.block(
            BlockedUserEntity(
                blockerId = currentUser.id,
                blockedId = userId,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun unblockUser(userId: String) {
        blockedUserDao.unblock(currentUser.id, userId)
    }

    override suspend fun pushEvent(event: Event) {
        val response = try {
            api.createEvent(event.toDto())
        } catch (e: IOException) {
            return          // offline - the row keeps syncedToBackend = false
        } catch (e: HttpException) {
            // 409 means the server already has this id. That happens when an
            // earlier push succeeded but the app died before the local flag was
            // updated. Treating it as failure would retry it forever, so record
            // the truth instead: it IS on the server.
            if (e.code() == HTTP_CONFLICT) {
                eventDao.upsert(event.copy(syncedToBackend = true).toEntity())
            }
            return
        }

        if (!response.isSuccessful) return

        // Prefer the server's copy over the one just sent. It stamps ownerId
        // from the header and zeroes the rating fields, so re-saving our own
        // version would quietly reintroduce whatever it corrected.
        //
        // EventDto.toDomain() sets syncedToBackend = true - anything that came
        // back from the server is synced by definition.
        val stored = response.body()?.dtoToDomain()
            ?: event.copy(syncedToBackend = true)

        eventDao.upsert(stored.toEntity())
    }
}
