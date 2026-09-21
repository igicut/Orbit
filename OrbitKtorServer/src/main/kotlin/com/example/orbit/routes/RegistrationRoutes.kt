package com.example.orbit.routes

import com.example.orbit.model.EventStatus
import com.example.orbit.service.CheckInOutcome
import com.example.orbit.service.ExposedEventService
import com.example.orbit.service.ExposedRegistrationService
import com.example.orbit.service.ExposedUserDataService
import com.example.orbit.service.RegistrationOutcome
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

// ---- potvrda dolaska, isto kao AttendanceRules u aplikaciji ----
private const val CHECK_IN_RADIUS_METERS = 200
private const val WALK_IN_WINDOW_MINUTES = 15
private const val DEFAULT_DURATION_MINUTES = 180
private const val MINUTE_MS = 60_000L

@Serializable
data class CheckInRequest(val latitude: Double, val longitude: Double)

/** Prijava, otkazivanje i potvrda dolaska; vracaju osvezen dogadjaj sa brojem prijava */
fun Route.registrationRoutes(
    eventService: ExposedEventService,
    registrationService: ExposedRegistrationService,
    userDataService: ExposedUserDataService,
) {

    put("/events/{id}/registration") {
        val eventId = call.parameters["id"]
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing event id")
        val userId = call.userIdOrNull()
            ?: return@put call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        // Privatni bez pristupa izgleda kao da ne postoji
        val event = eventService.findById(eventId)
            ?.takeIf { userDataService.canAccess(it, userId) }
            ?: return@put call.respond(HttpStatusCode.NotFound, "No such event")

        if (event.ownerId == userId) {
            return@put call.respond(
                HttpStatusCode.BadRequest,
                "Organisers do not register for their own events",
            )
        }
        if (event.status == EventStatus.CANCELLED) {
            return@put call.respond(HttpStatusCode.Conflict, "Dogadjaj je otkazan")
        }
        if (event.startTime <= System.currentTimeMillis()) {
            return@put call.respond(HttpStatusCode.Conflict, "Registration closed when the event started")
        }

        if (registrationService.register(eventId, userId) == RegistrationOutcome.FULL) {
            return@put call.respond(HttpStatusCode.Conflict, "The event is full")
        }
        call.respond(HttpStatusCode.OK, eventService.findById(eventId)!!)
    }

    delete("/events/{id}/registration") {
        val eventId = call.parameters["id"]
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing event id")
        val userId = call.userIdOrNull()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        val event = eventService.findById(eventId)
            ?.takeIf { userDataService.canAccess(it, userId) }
            ?: return@delete call.respond(HttpStatusCode.NotFound, "No such event")

        // Posle pocetka prijava ostaje, treba za potvrdu dolaska
        if (event.startTime <= System.currentTimeMillis()) {
            return@delete call.respond(HttpStatusCode.Conflict, "The event has started, the registration stays")
        }

        registrationService.cancel(eventId, userId)
        call.respond(HttpStatusCode.OK, eventService.findById(eventId)!!)
    }

    /** Potvrda na licu mesta; vreme i udaljenost meri server */
    put("/events/{id}/attendance") {
        val eventId = call.parameters["id"]
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing event id")
        val userId = call.userIdOrNull()
            ?: return@put call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        val event = eventService.findById(eventId)
            ?.takeIf { userDataService.canAccess(it, userId) }
            ?: return@put call.respond(HttpStatusCode.NotFound, "No such event")

        if (event.ownerId == userId) {
            return@put call.respond(
                HttpStatusCode.BadRequest,
                "Organisers do not check in to their own events",
            )
        }

        if (event.status == EventStatus.CANCELLED) {
            return@put call.respond(HttpStatusCode.Conflict, "Dogadjaj je otkazan")
        }

        val body = call.receive<CheckInRequest>()
        if (body.latitude !in -90.0..90.0 || body.longitude !in -180.0..180.0) {
            return@put call.respond(HttpStatusCode.BadRequest, "Invalid coordinates")
        }

        // Serverski sat, telefon moze imati pogresno vreme
        val now = System.currentTimeMillis()
        val endTime = event.startTime + (event.durationMinutes ?: DEFAULT_DURATION_MINUTES) * MINUTE_MS
        if (now < event.startTime) {
            return@put call.respond(HttpStatusCode.Conflict, "Check-in opens when the event starts")
        }
        if (now > endTime) {
            return@put call.respond(HttpStatusCode.Conflict, "The event has ended")
        }

        val distanceMeters = distanceKm(event.latitude, event.longitude, body.latitude, body.longitude) * 1000
        if (distanceMeters > CHECK_IN_RADIUS_METERS) {
            return@put call.respond(
                HttpStatusCode.Forbidden,
                "You are ${distanceMeters.roundToInt()} m from the event, check-in works within $CHECK_IN_RADIUS_METERS m",
            )
        }

        val walkInAllowed = now <= event.startTime + WALK_IN_WINDOW_MINUTES * MINUTE_MS
        when (registrationService.checkIn(eventId, userId, walkInAllowed)) {
            CheckInOutcome.CHECKED_IN -> call.respond(HttpStatusCode.OK, eventService.findById(eventId)!!)
            CheckInOutcome.CLOSED -> call.respond(
                HttpStatusCode.Conflict,
                "Without registration, check-in is only possible in the first $WALK_IN_WINDOW_MINUTES minutes",
            )
            CheckInOutcome.FULL -> call.respond(HttpStatusCode.Conflict, "The event is full")
        }
    }

    /** Spisak prijavljenih i dolazaka vidi samo organizator */
    get("/events/{id}/attendees") {
        val eventId = call.parameters["id"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing event id")
        val userId = call.userIdOrNull()
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        val event = eventService.findById(eventId)
            ?.takeIf { userDataService.canAccess(it, userId) }
            ?: return@get call.respond(HttpStatusCode.NotFound, "No such event")

        if (event.ownerId != userId) {
            return@get call.respond(HttpStatusCode.Forbidden, "Only the organiser can see the guest list")
        }
        call.respond(HttpStatusCode.OK, registrationService.attendees(eventId, event.startTime))
    }
}
