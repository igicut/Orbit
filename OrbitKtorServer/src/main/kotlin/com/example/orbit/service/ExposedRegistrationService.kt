package com.example.orbit.service

import com.example.orbit.db.Attendances
import com.example.orbit.db.Events
import com.example.orbit.db.Registrations
import com.example.orbit.db.Users
import com.example.orbit.model.AttendanceRecord
import com.example.orbit.model.Attendee
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

enum class RegistrationOutcome { REGISTERED, ALREADY_REGISTERED, FULL }

enum class CheckInOutcome { CHECKED_IN, CLOSED, FULL }

/** Prijave i potvrde dolaska; zauzeta mesta su u events.registered_count */
class ExposedRegistrationService(private val database: R2dbcDatabase) {

    /** Mesto i prijava u istoj transakciji, poslednje mesto dobija samo jedan */
    suspend fun register(eventId: String, userId: String): RegistrationOutcome =
        suspendTransaction(database) {
            if (isRegisteredHere(eventId, userId)) return@suspendTransaction RegistrationOutcome.ALREADY_REGISTERED
            if (!takeSpot(eventId)) return@suspendTransaction RegistrationOutcome.FULL

            insertRegistration(eventId, userId)
            RegistrationOutcome.REGISTERED
        }

    /** Oslobadja mesto; false ako prijave nije bilo */
    suspend fun cancel(eventId: String, userId: String): Boolean = suspendTransaction(database) {
        val deleted = Registrations.deleteWhere {
            (Registrations.eventId eq eventId) and (Registrations.userId eq userId)
        }
        if (deleted > 0) {
            Events.update({ (Events.id eq eventId) and (Events.registeredCount greater 0) }) {
                it[registeredCount] = registeredCount - 1
            }
        }
        deleted > 0
    }

    suspend fun registeredEventIds(userId: String): List<String> = suspendTransaction(database) {
        Registrations.selectAll()
            .where { Registrations.userId eq userId }
            .map { it[Registrations.eventId] }
            .toList()
    }

    /** Dolazak bez prijave zauzima mesto u istoj transakciji, kao prijava */
    suspend fun checkIn(eventId: String, userId: String, walkInAllowed: Boolean): CheckInOutcome =
        suspendTransaction(database) {
            if (!isRegisteredHere(eventId, userId)) {
                if (!walkInAllowed) return@suspendTransaction CheckInOutcome.CLOSED
                if (!takeSpot(eventId)) return@suspendTransaction CheckInOutcome.FULL
                insertRegistration(eventId, userId)
            }

            // Ponovljena potvrda ne menja prvo vreme dolaska
            Attendances.insertIgnore {
                it[Attendances.eventId] = eventId
                it[Attendances.userId] = userId
                it[checkedInAt] = System.currentTimeMillis()
            }
            CheckInOutcome.CHECKED_IN
        }

    suspend fun hasAttended(eventId: String, userId: String): Boolean = suspendTransaction(database) {
        Attendances.selectAll()
            .where { (Attendances.eventId eq eventId) and (Attendances.userId eq userId) }
            .limit(1)
            .toList()
            .isNotEmpty()
    }

    suspend fun attendances(userId: String): List<AttendanceRecord> = suspendTransaction(database) {
        Attendances.selectAll()
            .where { Attendances.userId eq userId }
            .map { AttendanceRecord(eventId = it[Attendances.eventId], checkedInAt = it[Attendances.checkedInAt]) }
            .toList()
    }

    /** Spisak za organizatora: svaka prijava sa imenom i dolaskom ako postoji */
    suspend fun attendees(eventId: String, startTime: Long): List<Attendee> = suspendTransaction(database) {
        Registrations
            .join(
                Attendances,
                JoinType.LEFT,
                additionalConstraint = {
                    (Attendances.eventId eq Registrations.eventId) and (Attendances.userId eq Registrations.userId)
                },
            )
            .join(Users, JoinType.LEFT, Registrations.userId, Users.id)
            .selectAll()
            .where { Registrations.eventId eq eventId }
            .orderBy(Registrations.registeredAt to SortOrder.ASC)
            .map { row ->
                val registeredAt = row[Registrations.registeredAt]
                Attendee(
                    userId = row[Registrations.userId],
                    displayName = row.getOrNull(Users.displayName),
                    registeredAt = registeredAt,
                    checkedInAt = row.getOrNull(Attendances.checkedInAt),
                    // Posle pocetka prijava nastaje samo potvrdom na licu mesta
                    walkIn = registeredAt >= startTime,
                )
            }
            .toList()
    }

    /** Uslovni UPDATE prolazi samo dok ima mesta; baza ga zakljucava */
    private suspend fun takeSpot(eventId: String): Boolean =
        Events.update({
            (Events.id eq eventId) and
                (Events.capacity.isNull() or (Events.registeredCount less Events.capacity))
        }) {
            it[registeredCount] = registeredCount + 1
        } > 0

    private suspend fun insertRegistration(eventId: String, userId: String) {
        Registrations.insert {
            it[Registrations.eventId] = eventId
            it[Registrations.userId] = userId
            it[registeredAt] = System.currentTimeMillis()
        }
    }

    /** Poziva se unutar vec otvorene transakcije */
    private suspend fun isRegisteredHere(eventId: String, userId: String): Boolean =
        Registrations.selectAll()
            .where { (Registrations.eventId eq eventId) and (Registrations.userId eq userId) }
            .limit(1)
            .toList()
            .isNotEmpty()
}
