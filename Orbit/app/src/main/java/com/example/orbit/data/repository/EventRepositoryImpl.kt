package com.example.orbit.data.repository

import com.example.orbit.data.remote.dto.EventDto

import com.example.orbit.data.local.dao.AttendanceDao
import com.example.orbit.data.local.entity.AttendanceEntity
import com.example.orbit.data.remote.dto.CheckInRequestDto
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.AttendedEvent
import com.example.orbit.domain.model.Attendee
import com.example.orbit.domain.model.UserLocation

import com.example.orbit.data.remote.dto.JoinRequestDto
import com.example.orbit.data.remote.dto.ProfileUpdateDto

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

import com.example.orbit.data.local.entity.RegistrationEntity

import com.example.orbit.data.local.dao.RegistrationDao

import com.example.orbit.data.image.ImageUploadResult
import com.example.orbit.data.image.ImageUploader
import com.example.orbit.data.local.dao.EventDao
import com.example.orbit.data.remote.ImageUrls
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
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_CONFLICT = 409
private const val HTTP_NOT_FOUND = 404
private const val HTTP_FORBIDDEN = 403

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    private val currentUser: CurrentUser,
    private val registrationDao: RegistrationDao,
    private val attendanceDao: AttendanceDao,
    private val ratingDao: RatingDao,
    private val userDao: UserDao,
    private val blockedUserDao: BlockedUserDao,
    private val api: OrbitApiService,
    private val imageUploader: ImageUploader,
) : EventRepository {

    override fun observeEvents(): Flow<List<Event>> =
        eventDao.observeAll(currentUser.id).map { rows -> rows.map { it.toDomain() } }

    override fun observeEventsByOwner(ownerId: String): Flow<List<Event>> =
        eventDao.observeByOwner(ownerId).map { rows -> rows.map { it.toDomain() } }

    override fun observeJoinedPrivateEvents(ownerId: String): Flow<List<Event>> =
        eventDao.observeJoinedPrivate(ownerId).map { rows -> rows.map { it.toDomain() } }

    override fun observeRegisteredEvents(): Flow<List<Event>> =
        registrationDao.observeRegisteredEvents(currentUser.id).map { rows -> rows.map { it.toDomain() } }

    override fun observeIsRegistered(eventId: String): Flow<Boolean> =
        registrationDao.observeIsRegistered(eventId)

    override suspend fun registerForEvent(eventId: String): RegistrationResult {
        val response = try {
            api.registerForEvent(eventId)
        } catch (e: IOException) {
            return RegistrationResult.NoConnection
        } catch (e: SerializationException) {
            return RegistrationResult.Failed
        }
        return applyRegistration(eventId, response, registered = true)
    }

    override suspend fun cancelRegistration(eventId: String): RegistrationResult {
        val response = try {
            api.cancelRegistration(eventId)
        } catch (e: IOException) {
            return RegistrationResult.NoConnection
        } catch (e: SerializationException) {
            return RegistrationResult.Failed
        }
        return applyRegistration(eventId, response, registered = false)
    }

    /** Zajednicki deo; server vraca dogadjaj sa novim brojem prijava */
    private suspend fun applyRegistration(
        eventId: String,
        response: Response<EventDto>,
        registered: Boolean,
    ): RegistrationResult {
        val updated = response.body()
        if (!response.isSuccessful || updated == null) {
            if (response.code() != HTTP_CONFLICT) return RegistrationResult.Failed

            // 409 znaci popunjeno ili vec poceo; sveza kopija ispravlja broj na ekranu
            val fresh = refreshEvent(eventId)
            val startTime = fresh?.startTime ?: getEvent(eventId)?.startTime ?: 0L
            val started = startTime <= System.currentTimeMillis()
            return if (!registered || started) RegistrationResult.Closed else RegistrationResult.Full
        }

        eventDao.upsert(updated.dtoToDomain().toEntity())
        if (registered) {
            registrationDao.insert(RegistrationEntity(eventId = eventId, registeredAt = System.currentTimeMillis()))
        } else {
            registrationDao.delete(eventId)
        }
        return RegistrationResult.Success
    }

    override fun observeHasAttended(eventId: String): Flow<Boolean> =
        attendanceDao.observeHasAttended(eventId)

    override fun observeAttendedEvents(): Flow<List<AttendedEvent>> =
        attendanceDao.observeAttendedEvents(currentUser.id).map { rows ->
            rows.map { AttendedEvent(event = it.event.toDomain(), checkedInAt = it.checkedInAt, myRating = it.myRating) }
        }

    override suspend fun checkIn(eventId: String, location: UserLocation): CheckInResult {
        val wasRegistered = registrationDao.isRegistered(eventId)
        val response = try {
            api.checkIn(eventId, CheckInRequestDto(location.latitude, location.longitude))
        } catch (e: IOException) {
            return CheckInResult.NoConnection
        } catch (e: SerializationException) {
            return CheckInResult.Failed
        }

        val updated = response.body()
        if (!response.isSuccessful || updated == null) {
            val code = response.code()
            if (code != HTTP_FORBIDDEN && code != HTTP_CONFLICT) return CheckInResult.Failed

            // Organizator je mozda pomerio dogadjaj; sveza kopija daje tacnu poruku
            val event = refreshEvent(eventId) ?: getEvent(eventId) ?: return CheckInResult.Failed
            if (code == HTTP_FORBIDDEN) {
                return CheckInResult.TooFar(AttendanceRules.distanceMeters(event, location))
            }

            // Prijavljenima kapacitet ne smeta, pa je njihov 409 uvek zatvoren prozor
            val isFull = event.capacity != null && event.registeredCount >= event.capacity
            return if (!wasRegistered && isFull) CheckInResult.Full else CheckInResult.Closed
        }

        val now = System.currentTimeMillis()
        eventDao.upsert(updated.dtoToDomain().toEntity())
        // Bez prijave server je napravio i prijavu
        if (!wasRegistered) registrationDao.insert(RegistrationEntity(eventId = eventId, registeredAt = now))
        attendanceDao.insert(AttendanceEntity(eventId = eventId, checkedInAt = now))
        return CheckInResult.Success
    }

    override suspend fun getAttendees(eventId: String): List<Attendee>? {
        val rows = try {
            api.getAttendees(eventId)
        } catch (e: IOException) {
            return null
        } catch (e: HttpException) {
            return null
        } catch (e: SerializationException) {
            return null
        }
        return rows.map {
            Attendee(
                userId = it.userId,
                displayName = it.displayName,
                registeredAt = it.registeredAt,
                checkedInAt = it.checkedInAt,
                walkIn = it.walkIn,
            )
        }
    }

    /** Posle odbijanja uzima dogadjaj sa servera u Room; null ako ne uspe */
    private suspend fun refreshEvent(eventId: String): Event? {
        val fresh = try {
            api.getEvent(eventId).dtoToDomain()
        } catch (e: IOException) {
            return null
        } catch (e: HttpException) {
            return null
        } catch (e: SerializationException) {
            return null
        }
        eventDao.upsert(fresh.toEntity())
        return fresh
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

        val prepared = withUploadedImages(event) ?: return false

        val response = try {
            api.updateEvent(event.id, prepared.toDto())
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
    override suspend fun pushPendingEvents() {
        eventDao.getPendingUploads(currentUser.id).forEach { entity ->
            pushEvent(entity.toDomain())
        }
    }

    /** F-13: posle prijave Room dobija sve sto pripada nalogu */
    override suspend fun syncAccountData(): Boolean {
        val data = try {
            api.getAccountData()
        } catch (e: IOException) {
            return false
        } catch (e: HttpException) {
            return false
        }

        val userId = currentUser.id
        val now = System.currentTimeMillis()
        val events = data.ownEvents + data.joinedEvents + data.registeredEvents

        // Prvo dogadjaji, pa redovi koji na njih pokazuju
        events.forEach { eventDao.upsert(it.dtoToDomain().toEntity()) }
        cacheOwnerNames(events)

        registrationDao.replaceAll(data.registeredEvents.map { RegistrationEntity(eventId = it.id, registeredAt = now) })
        attendanceDao.replaceAll(data.attendances.map { AttendanceEntity(eventId = it.eventId, checkedInAt = it.checkedInAt) })

        data.blockedUsers.forEach { userDao.upsert(UserEntity(it.id, it.displayName, it.interests)) }
        blockedUserDao.replaceForBlocker(
            blockerId = userId,
            rows = data.blockedUsers.map { BlockedUserEntity(blockerId = userId, blockedId = it.id, createdAt = now) },
        )

        // Isti id kao u submitRating, da nema duplikata
        ratingDao.replaceForUser(
            userId = userId,
            rows = data.ratings.map {
                RatingEntity(
                    id = it.eventId + ":" + userId,
                    eventId = it.eventId,
                    userId = userId,
                    value = it.value,
                    comment = it.comment,
                    createdAt = it.createdAt,
                )
            },
        )
        return true
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
            api.joinEvent(JoinRequestDto(normalised))
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

    override suspend fun publishDisplayName(): Boolean {
        if (currentUser.isRegistered) return true
        // Bez sesije server bi vratio 401, nema svrhe slati
        if (!currentUser.isLoggedIn.value) return false

        val response = try {
            api.updateProfile(ProfileUpdateDto(displayName = currentUser.displayName))
        } catch (e: IOException) {
            return false        // offline, pokusava se pri sledecem pokretanju
        }

        val profile = response.body()
        if (!response.isSuccessful || profile == null) return false

        currentUser.isRegistered = true

        // Kesiramo profil kako ga server vraca, sa interesovanjima
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
        return publishDisplayName()
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

    /** F-28: server pa Room, blokiranje prati nalog; sebe ne mozes blokirati */
    override suspend fun blockUser(userId: String): Boolean {
        if (userId == currentUser.id) return false

        val response = try {
            api.blockUser(userId)
        } catch (e: IOException) {
            return false
        }
        if (!response.isSuccessful) return false

        blockedUserDao.block(
            BlockedUserEntity(
                blockerId = currentUser.id,
                blockedId = userId,
                createdAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    override suspend fun unblockUser(userId: String): Boolean {
        val response = try {
            api.unblockUser(userId)
        } catch (e: IOException) {
            return false
        }
        if (!response.isSuccessful) return false

        blockedUserDao.unblock(currentUser.id, userId)
        return true
    }

    /** F-15: salje lokalno napravljen dogadjaj serveru */
    override suspend fun pushEvent(event: Event) {
        // Bez slika nema ni dogadjaja; ostaje u redu za sledecu sinhronizaciju
        val prepared = withUploadedImages(event) ?: return

        val response = try {
            api.createEvent(prepared.toDto())
        } catch (e: IOException) {
            return          // offline, ostaje syncedToBackend = false
        } catch (e: HttpException) {
            // 409: vec postoji na serveru, oznacavamo kao poslat
            if (e.code() == HTTP_CONFLICT) {
                eventDao.upsert(prepared.copy(syncedToBackend = true).toEntity())
            }
            return
        }

        if (!response.isSuccessful) return

        // Cuvamo serversku verziju, ona je vec sinhronizovana
        val stored = response.body()?.dtoToDomain()
            ?: prepared.copy(syncedToBackend = true)

        eventDao.upsert(stored.toEntity())
    }

    /**
     * F-37: lokalne slike zamenjuje putanjama sa servera.
     * null znaci da nema mreze, pa se ceo dogadjaj salje kasnije.
     */
    private suspend fun withUploadedImages(event: Event): Event? {
        if (event.imageUris.all { ImageUrls.isStored(it) }) return event

        val paths = mutableListOf<String>()
        event.imageUris.forEach { uri ->
            if (ImageUrls.isStored(uri)) {
                paths += uri
                return@forEach
            }
            when (val result = imageUploader.upload(uri)) {
                is ImageUploadResult.Uploaded -> paths += result.path
                ImageUploadResult.NoConnection -> return null
                // Fajl je nestao ili ga server ne prima; dogadjaj ide bez te slike
                ImageUploadResult.Rejected -> Unit
            }
        }
        return event.copy(imageUris = paths)
    }
}
