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
import com.example.orbit.data.remote.dto.AiSuggestRequestDto
import com.example.orbit.domain.model.AiSuggestion
import com.example.orbit.domain.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_CONFLICT = 409
private const val HTTP_NOT_FOUND = 404

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

    override suspend fun deleteEvent(id: String): Boolean {
        val response = try {
            api.deleteEvent(id)
        } catch (e: IOException) {
            return false    // nema mreze ili server ne radi
        }

        // 404: server ga nema, dovoljno je obrisati lokalno
        if (!response.isSuccessful && response.code() != HTTP_NOT_FOUND) return false

        eventDao.deleteById(id)
        return true
    }

    override suspend fun updateEvent(event: Event): Boolean {
        // Prvo lokalno, da izmena prezivi neuspeli zahtev
        eventDao.upsert(event.toEntity())

        val response = try {
            api.updateEvent(event.id, event.toDto())
        } catch (e: IOException) {
            return false
        } catch (e: HttpException) {
            return false
        }

        // Server primenjuje ista ogranicenja, cuvamo njegovu verziju
        response.body()?.let { eventDao.upsert(it.dtoToDomain().toEntity()) }
        return response.isSuccessful
    }

    /** F-15: ponovo salje dogadjaje napravljene bez mreze */
    private suspend fun pushPendingEvents() {
        eventDao.getPendingUploads(currentUser.id).forEach { entity ->
            pushEvent(entity.toDomain())
        }
    }

    override suspend fun suggestEventDetails(title: String, description: String): AiSuggestion? {
        val dto = try {
            api.suggestEventDetails(AiSuggestRequestDto(title, description))
        } catch (e: IOException) {
            return null     // nema mreze ili server ne radi
        } catch (e: HttpException) {
            return null     // 503 nema kljuca, 502 AI pao, 400 los unos
        } catch (e: SerializationException) {
            return null     // odgovor nije ocekivanog oblika
        }
        return AiSuggestion(dto.category, dto.description)
    }

    /** F-15: skida javne dogadjaje u blizini i kesira ih */
    override suspend fun syncPublicEvents(
        latitude: Double,
        longitude: Double,
        radiusKm: Double?,
    ) {
        // Prvo saljemo, pa preuzimamo
        pushPendingEvents()

        val remote = try {
            api.searchEvents(latitude, longitude, radiusKm)
        } catch (e: IOException) {
            return          // nema mreze ili server ne radi
        } catch (e: HttpException) {
            return          // server vratio gresku
        }

        // Menja kes tek posle uspesnog preuzimanja
        eventDao.replacePublicCache(
            userId = currentUser.id,
            events = remote.map { dto -> dto.dtoToDomain().toEntity() },
        )
        cacheOwnerNames(remote)
    }

    /** F-21: trazi privatni dogadjaj po kodu i kesira ga */
    override suspend fun joinEventByAccessCode(code: String): JoinResult {
        val normalised = code.trim().uppercase()
        if (normalised.isEmpty()) return JoinResult.NotFound

        val dto = try {
            api.getEventByAccessCode(normalised)
        } catch (e: HttpException) {
            // 404 znaci pogresan kod, ostalo je greska servera
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

    /** F-27: salje ocenu, prosek uzima od servera */
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

        // Lokalna kopija; id iz para, ponovna ocena zamenjuje
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

    // ---- F-28: moderacija ----

    override fun observeUser(userId: String): Flow<User?> =
        userDao.observeById(userId).map { it?.toDomain() }

    /** Kesira profil korisnika, greske se ignorisu */
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
            return false        // offline, pokusava se pri sledecem pokretanju
        } catch (e: HttpException) {
            return false
        }

        if (!response.isSuccessful) return false

        currentUser.isRegistered = true

        // Kesiramo i svoj profil radi konzistentnosti
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
        // Setter brise registrovan flag, pa se ime ponovo salje
        currentUser.displayName = name.trim()
        return registerCurrentUser()
    }

    override fun observeUserNames(): Flow<Map<String, String>> =
        userDao.observeAll().map { rows -> rows.associate { it.id to it.displayName } }

    /** Cuva imena organizatora koja stignu uz dogadjaje */
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

    /** Blokiranje je samo lokalno; sebe ne mozes blokirati */
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

    /** F-15: salje lokalno napravljen dogadjaj serveru */
    override suspend fun pushEvent(event: Event) {
        val response = try {
            api.createEvent(event.toDto())
        } catch (e: IOException) {
            return          // offline, ostaje syncedToBackend = false
        } catch (e: HttpException) {
            // 409: vec postoji na serveru, oznacavamo kao poslat
            if (e.code() == HTTP_CONFLICT) {
                eventDao.upsert(event.copy(syncedToBackend = true).toEntity())
            }
            return
        }

        if (!response.isSuccessful) return

        // Cuvamo serversku verziju, ona je vec sinhronizovana
        val stored = response.body()?.dtoToDomain()
            ?: event.copy(syncedToBackend = true)

        eventDao.upsert(stored.toEntity())
    }
}
