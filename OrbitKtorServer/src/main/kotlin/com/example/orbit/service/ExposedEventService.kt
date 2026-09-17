package com.example.orbit.service

import com.example.orbit.db.Attendances
import com.example.orbit.db.EventEmbeddings
import com.example.orbit.db.EventMembers
import com.example.orbit.db.Events
import com.example.orbit.db.Ratings
import com.example.orbit.db.Registrations
import com.example.orbit.db.Users
import com.example.orbit.model.EventCategory
import com.example.orbit.model.ExposedEvent
import com.example.orbit.model.Visibility
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.math.abs
import kotlin.math.cos

/** F-12: pristup bazi za dogadjaje */
class ExposedEventService(private val database: R2dbcDatabase) {

    /** Dogadjaji sa imenom organizatora, LEFT JOIN */
    private val eventsWithOwner
        get() = Events.leftJoin(Users, { Events.ownerId }, { Users.id })

    /** F-12: javni dogadjaji, svi parametri opcioni */
    suspend fun search(
        latitude: Double? = null,
        longitude: Double? = null,
        radiusKm: Double? = null,
        category: EventCategory? = null,
        query: String? = null,
        /** F-28: vlasnici sakriveni blokadom, u bilo kom smeru */
        excludeOwnerIds: Set<String> = emptySet(),
    ): List<ExposedEvent> = suspendTransaction(database) {

        var condition: Op<Boolean> = Events.visibility eq Visibility.PUBLIC

        if (excludeOwnerIds.isNotEmpty()) {
            condition = condition and (Events.ownerId notInList excludeOwnerIds)
        }

        if (category != null) {
            condition = condition and (Events.category eq category)
        }

        if (!query.isNullOrBlank()) {
            val pattern = "%" + query.trim() + "%"
            // Exposed parametrizuje vrednost, nema SQL injection
            condition = condition and (
                (Events.title like pattern) or (Events.description like pattern)
                )
        }

        // Bounding box umesto tacne udaljenosti, koristi indekse
        if (latitude != null && longitude != null && radiusKm != null && radiusKm > 0) {
            val latDelta = radiusKm / 111.0
            val lngDelta = radiusKm / (111.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.01))

            condition = condition and
                (Events.latitude greaterEq (latitude - latDelta)) and
                (Events.latitude lessEq (latitude + latDelta)) and
                (Events.longitude greaterEq (longitude - abs(lngDelta))) and
                (Events.longitude lessEq (longitude + abs(lngDelta)))
        }

        eventsWithOwner.selectAll()
            .where(condition)
            .orderBy(Events.startTime to SortOrder.ASC)
            .map { it.toExposedEvent() }
            .toList()
    }

    suspend fun findById(id: String): ExposedEvent? = suspendTransaction(database) {
        eventsWithOwner.selectAll()
            .where { Events.id eq id }
            .map { it.toExposedEvent() }
            .singleOrNull()
    }

    /** Vise dogadjaja po id-ju, za podatke naloga */
    suspend fun findByIds(ids: List<String>): List<ExposedEvent> {
        if (ids.isEmpty()) return emptyList()
        return suspendTransaction(database) {
            eventsWithOwner.selectAll()
                .where { Events.id inList ids }
                .orderBy(Events.startTime to SortOrder.ASC)
                .map { it.toExposedEvent() }
                .toList()
        }
    }

    /** F-12: cuva dogadjaj koji je napravio klijent */
    suspend fun create(event: ExposedEvent): String = suspendTransaction(database) {
        Events.insert {
            it[id] = event.id
            it[ownerId] = event.ownerId
            it[title] = event.title
            it[description] = event.description
            it[latitude] = event.latitude
            it[longitude] = event.longitude
            it[address] = event.address
            it[startTime] = event.startTime
            it[durationMinutes] = event.durationMinutes
            it[category] = event.category
            it[visibility] = event.visibility
            it[imageUris] = event.imageUris
            it[capacity] = event.capacity
            it[price] = event.price
            it[accessCode] = event.accessCode
            // Ocene i prijave se ne primaju od klijenta
            it[avgRating] = 0f
            it[ratingCount] = 0
            it[registeredCount] = 0
            it[createdAt] = event.createdAt
        }
        event.id
    }

    /** F-21: dogadjaj po pristupnom kodu, samo PRIVATE */
    suspend fun findByAccessCode(code: String): ExposedEvent? = suspendTransaction(database) {
        eventsWithOwner.selectAll()
            .where {
                (Events.accessCode eq code) and (Events.visibility eq Visibility.PRIVATE)
            }
            .map { it.toExposedEvent() }
            .singleOrNull()
    }

    /** Svi dogadjaji jednog vlasnika, i privatni */
    suspend fun findByOwner(ownerId: String): List<ExposedEvent> = suspendTransaction(database) {
        eventsWithOwner.selectAll()
            .where { Events.ownerId eq ownerId }
            .orderBy(Events.startTime to SortOrder.ASC)
            .map { it.toExposedEvent() }
            .toList()
    }

    /** F-12: menja samo dozvoljena polja; false ako bi kapacitet pao ispod broja prijava */
    suspend fun update(id: String, event: ExposedEvent): Boolean = suspendTransaction(database) {
        val newCapacity = event.capacity
        // Uslov u istom UPDATE-u, da se ne ukrsti sa novom prijavom
        val capacityFits = if (newCapacity == null) Op.TRUE else (Events.registeredCount lessEq newCapacity)

        Events.update({ (Events.id eq id) and capacityFits }) {
            it[title] = event.title
            it[description] = event.description
            it[latitude] = event.latitude
            it[longitude] = event.longitude
            it[address] = event.address
            it[startTime] = event.startTime
            it[durationMinutes] = event.durationMinutes
            it[category] = event.category
            it[imageUris] = event.imageUris
            it[capacity] = event.capacity
            it[price] = event.price
        } > 0
    }

    /** Brise dogadjaj i sve redove vezane za njega; nema FK da to uradi */
    suspend fun delete(id: String) {
        suspendTransaction(database) {
            Ratings.deleteWhere { Ratings.eventId eq id }
            EventEmbeddings.deleteWhere { EventEmbeddings.eventId eq id }
            Attendances.deleteWhere { Attendances.eventId eq id }
            Registrations.deleteWhere { Registrations.eventId eq id }
            EventMembers.deleteWhere { EventMembers.eventId eq id }
            Events.deleteWhere { Events.id eq id }
        }
    }

    /** F-27: preracunava prosek i broj ocena na dogadjaju */
    suspend fun refreshRatingSummary(eventId: String) {
        suspendTransaction(database) {
            val values = Ratings.selectAll()
                .where { Ratings.eventId eq eventId }
                .map { it[Ratings.value] }
                .toList()

            val average = if (values.isEmpty()) 0f else values.sum().toFloat() / values.size

            Events.update({ Events.id eq eventId }) {
                it[avgRating] = average
                it[ratingCount] = values.size
            }
        }
    }

    /** Red u model; syncedToBackend je uvek true */
    private fun ResultRow.toExposedEvent() = ExposedEvent(
        id = this[Events.id],
        ownerId = this[Events.ownerId],
        title = this[Events.title],
        description = this[Events.description],
        latitude = this[Events.latitude],
        longitude = this[Events.longitude],
        startTime = this[Events.startTime],
        category = this[Events.category],
        visibility = this[Events.visibility],
        imageUris = this[Events.imageUris],
        address = this[Events.address],
        durationMinutes = this[Events.durationMinutes],
        capacity = this[Events.capacity],
        price = this[Events.price],
        registeredCount = this[Events.registeredCount],
        accessCode = this[Events.accessCode],
        avgRating = this[Events.avgRating],
        ratingCount = this[Events.ratingCount],
        createdAt = this[Events.createdAt],
        syncedToBackend = true,
        // getOrNull jer LEFT JOIN moze da nema profil
        ownerName = getOrNull(Users.displayName),
    )
}
