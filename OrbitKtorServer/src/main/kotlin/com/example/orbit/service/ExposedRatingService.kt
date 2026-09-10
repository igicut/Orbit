package com.example.orbit.service

import com.example.orbit.db.Ratings
import com.example.orbit.model.ExposedRating
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

/**
 * F-27 - all database access for ratings.
 */
class ExposedRatingService(private val database: R2dbcDatabase) {

    /**
     * One rating per person per event.
     *
     * The table has a unique index on (event_id, user_id), so a plain insert would
     * fail the second time somebody rates the same event. Look for the existing row
     * first and update it instead.
     *
     * Both branches run inside one transaction, so the check and the write cannot be
     * separated by another request. Without that, two rapid submissions could both
     * see "no existing row" and the second insert would fail on the index.
     */
    suspend fun upsert(rating: ExposedRating) {
        suspendTransaction(database) {
            val existingId = Ratings.selectAll()
                .where { (Ratings.eventId eq rating.eventId) and (Ratings.userId eq rating.userId) }
                .map { it[Ratings.id] }
                .singleOrNull()

            if (existingId != null) {
                Ratings.update({ Ratings.id eq existingId }) {
                    it[value] = rating.value
                    it[comment] = rating.comment
                    it[createdAt] = rating.createdAt
                }
            } else {
                Ratings.insert {
                    it[id] = rating.id
                    it[eventId] = rating.eventId
                    it[userId] = rating.userId
                    it[value] = rating.value
                    it[comment] = rating.comment
                    it[createdAt] = rating.createdAt
                }
            }
        }
    }

    suspend fun findForEvent(eventId: String): List<ExposedRating> = suspendTransaction(database) {
        Ratings.selectAll()
            .where { Ratings.eventId eq eventId }
            .orderBy(Ratings.createdAt to SortOrder.DESC)
            .map { it.toExposedRating() }
            .toList()
    }

    suspend fun findByUserForEvent(eventId: String, userId: String): ExposedRating? =
        suspendTransaction(database) {
            Ratings.selectAll()
                .where { (Ratings.eventId eq eventId) and (Ratings.userId eq userId) }
                .map { it.toExposedRating() }
                .singleOrNull()
        }

    /**
     * Average value and number of ratings for one event.
     *
     * Computed in Kotlin rather than with SQL AVG() because AVG returns NULL for an
     * event with no ratings, which then has to be unwrapped anyway - and the row
     * counts here are small. Returns 0f to 0 for an unrated event.
     */
    suspend fun summaryForEvent(eventId: String): Pair<Float, Int> = suspendTransaction(database) {
        val values = Ratings.selectAll()
            .where { Ratings.eventId eq eventId }
            .map { it[Ratings.value] }
            .toList()

        if (values.isEmpty()) 0f to 0
        else (values.sum().toFloat() / values.size) to values.size
    }

    /** Row -> model. One place, so the mapping cannot drift between queries. */
    private fun ResultRow.toExposedRating() = ExposedRating(
        id = this[Ratings.id],
        eventId = this[Ratings.eventId],
        userId = this[Ratings.userId],
        value = this[Ratings.value],
        comment = this[Ratings.comment],
        createdAt = this[Ratings.createdAt],
    )
}
