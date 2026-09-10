package com.example.orbit.service

import com.example.orbit.db.Events
import com.example.orbit.db.Ratings
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

/**
 * F-12 - all database access for events.
 *
 * search() and findById() are implemented so the Android search screen has
 * something real to talk to. The rest are still stubs for you to fill in;
 * ExposedUserService and the two implemented functions here are the reference.
 */
class ExposedEventService(private val database: R2dbcDatabase) {

    /**
     * Events with the organiser's name attached.
     *
     * A LEFT join, not an inner one: an event whose owner never registered must
     * still be returned, just without a name. Doing it here means one request
     * carries everything the list needs - the alternative is the client asking
     * for each organiser separately, which is a request per row.
     */
    private val eventsWithOwner
        get() = Events.leftJoin(Users, { Events.ownerId }, { Users.id })

    /**
     * F-12 - public events, optionally narrowed.
     *
     * Every parameter is optional. With none of them this returns every public
     * event, which is what the "all events" screen asks for.
     *
     *  query      - matched against title and description, case-insensitively
     *  lat/lng    - with radiusKm, restricts to a bounding box around the point
     *  category   - exact match
     *
     * Private events are never returned here. They are reachable only through
     * findByAccessCode(), which is the whole point of them being private.
     */
    suspend fun search(
        latitude: Double? = null,
        longitude: Double? = null,
        radiusKm: Double? = null,
        category: EventCategory? = null,
        query: String? = null,
    ): List<ExposedEvent> = suspendTransaction(database) {

        var condition: Op<Boolean> = Events.visibility eq Visibility.PUBLIC

        if (category != null) {
            condition = condition and (Events.category eq category)
        }

        if (!query.isNullOrBlank()) {
            val pattern = "%" + query.trim() + "%"
            // Exposed parameterises the value, so this is not string concatenation
            // into SQL - a query containing a quote cannot break out.
            condition = condition and (
                (Events.title like pattern) or (Events.description like pattern)
                )
        }

        // A bounding box rather than a true great-circle distance: it is a plain
        // WHERE clause, it uses the lat/lng indexes, and for a city-sized radius
        // the difference is metres.
        //
        // One degree of latitude is ~111 km everywhere. A degree of longitude
        // shrinks towards the poles, hence the cos() - forgetting it makes the
        // box far too wide in Belgrade and useless in Norway.
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

    /**
     * F-12 - store an event the client created.
     *
     * The id arrives from the client, already generated, so the event keeps one
     * identity everywhere: in Room, here, and on a phone it is later shared to.
     * The caller has already rejected a duplicate id, so a plain insert is right
     * - silently overwriting would let one device replace another's event.
     */
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
            it[requiresReservation] = event.requiresReservation
            it[accessCode] = event.accessCode
            // Ratings are never accepted from the client - they are derived from
            // the ratings table and start empty.
            it[avgRating] = 0f
            it[ratingCount] = 0
            it[createdAt] = event.createdAt
        }
        event.id
    }

    // ---------------------------------------------------------------- stubs

    /** F-21 - only PRIVATE events should ever match. */
    /**
     * F-21 - resolve a shared access code to its event.
     *
     * The visibility check is not redundant. Only private events are given a code,
     * but pinning the query to PRIVATE means a public event can never be reached
     * this way even if one somehow ends up with a code set.
     *
     * Codes are generated uppercase; the route uppercases what it receives, so a
     * user typing lowercase still matches.
     */
    suspend fun findByAccessCode(code: String): ExposedEvent? = suspendTransaction(database) {
        eventsWithOwner.selectAll()
            .where {
                (Events.accessCode eq code) and (Events.visibility eq Visibility.PRIVATE)
            }
            .map { it.toExposedEvent() }
            .singleOrNull()
    }

    /**
     * Every event belonging to one person, private ones included.
     *
     * Unlike [search] this does not filter by visibility: an owner is always
     * allowed to see their own private events.
     */
    suspend fun findByOwner(ownerId: String): List<ExposedEvent> = suspendTransaction(database) {
        eventsWithOwner.selectAll()
            .where { Events.ownerId eq ownerId }
            .orderBy(Events.startTime to SortOrder.ASC)
            .map { it.toExposedEvent() }
            .toList()
    }

    /**
     * F-12 - change an event.
     *
     * Only the columns a person is allowed to change are written. Deliberately
     * left alone:
     *
     *   ownerId, createdAt   - identity and history, never editable
     *   avgRating, ratingCount - derived from the ratings table; accepting them
     *                            from a client would let anyone invent a score
     *   visibility           - people joined a private event expecting privacy,
     *                          and a public one expecting to keep access
     *   accessCode           - already shared; regenerating it locks people out
     */
    suspend fun update(id: String, event: ExposedEvent) {
        suspendTransaction(database) {
            Events.update({ Events.id eq id }) {
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
                it[requiresReservation] = event.requiresReservation
            }
        }
    }

    /**
     * Remove an event.
     *
     * Its ratings go with it: the ratings table declares the event id as a
     * foreign key with ON DELETE CASCADE, so the database does that part.
     */
    suspend fun delete(id: String) {
        suspendTransaction(database) {
            Events.deleteWhere { Events.id eq id }
        }
    }

    /**
     * F-27 - recompute the denormalised rating summary on the event row.
     *
     * avgRating and ratingCount are copies of what the ratings table says, kept
     * on the event so listing events needs no join. Copies go stale, so this
     * must run after every rating change - the routes do exactly that.
     *
     * Computed in SQL rather than by loading every rating: with a popular event
     * the difference is a single row versus hundreds crossing the wire.
     */
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

    /**
     * Row -> model, in one place so the mapping cannot drift between queries.
     * syncedToBackend is true by definition: this row came from the server.
     */
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
        requiresReservation = this[Events.requiresReservation],
        accessCode = this[Events.accessCode],
        avgRating = this[Events.avgRating],
        ratingCount = this[Events.ratingCount],
        createdAt = this[Events.createdAt],
        syncedToBackend = true,
        // getOrNull, because a left join leaves this absent when the organiser
        // has no profile row.
        ownerName = getOrNull(Users.displayName),
    )
}
