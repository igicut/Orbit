package com.example.orbit.routes

import com.example.orbit.model.ExposedRating
import com.example.orbit.model.RatingRequest
import com.example.orbit.service.ExposedEventService
import com.example.orbit.service.ExposedRatingService
import com.example.orbit.service.ExposedRegistrationService
import com.example.orbit.service.ExposedUserDataService
import com.example.orbit.service.ImageStorage
import com.example.orbit.service.isStoredPath
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import java.util.UUID

/** F-40: najduzi komentar uz ocenu */
private const val MAX_COMMENT_LENGTH = 1000

/** F-27: rute za ocene */
fun Route.ratingRoutes(
    ratingService: ExposedRatingService,
    eventService: ExposedEventService,
    userDataService: ExposedUserDataService,
    registrationService: ExposedRegistrationService,
    imageStorage: ImageStorage,
) {

    /** Slanje ili izmena ocene; vraca osvezen dogadjaj */
    patch("/events/{id}/rating") {
        val eventId = call.parameters["id"]
            ?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing event id")
        val userId = call.userIdOrNull()
            ?: return@patch call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        // Privatni bez pristupa izgleda kao da ne postoji
        val event = eventService.findById(eventId)
            ?.takeIf { userDataService.canAccess(it, userId) }
            ?: return@patch call.respond(HttpStatusCode.NotFound, "No such event")

        // F-27: ne moze se oceniti dogadjaj koji nije poceo
        if (event.startTime > System.currentTimeMillis()) {
            return@patch call.respond(
                HttpStatusCode.Conflict,
                "Cannot rate an event that has not started yet",
            )
        }

        // Ocenjuju samo potvrdjeni dolasci; organizator ne potvrdjuje, pa ne ocenjuje svoj
        if (!registrationService.hasAttended(eventId, userId)) {
            return@patch call.respond(
                HttpStatusCode.Forbidden,
                "Only people who checked in can rate this event",
            )
        }

        val body = call.receive<RatingRequest>()
        if (body.value !in 1..5) {
            return@patch call.respond(HttpStatusCode.BadRequest, "value must be between 1 and 5")
        }

        // F-40: kao kod dogadjaja, samo slika koja je vec na ovom serveru
        val imagePath = body.imagePath
        if (imagePath != null && !isStoredPath(imagePath)) {
            return@patch call.respond(HttpStatusCode.BadRequest, "Slika mora prvo da se posalje na POST /images")
        }

        // Prazan komentar znaci bez komentara; granica da jedan utisak ne postane esej
        val comment = body.comment?.trim()?.take(MAX_COMMENT_LENGTH)?.takeIf { it.isNotEmpty() }

        // Isti id pri ponovnom ocenjivanju
        val existing = ratingService.findByUserForEvent(eventId, userId)

        ratingService.upsert(
            ExposedRating(
                id = existing?.id ?: UUID.randomUUID().toString(),
                eventId = eventId,
                userId = userId,
                value = body.value,
                comment = comment,
                imagePath = imagePath,
            )
        )

        // Zamenjena ili uklonjena slika vise nije ni u jednom redu, pa se brise fajl
        val oldImage = existing?.imagePath
        if (oldImage != null && oldImage != imagePath) {
            imageStorage.deleteAll(listOf(oldImage))
        }

        eventService.refreshRatingSummary(eventId)

        val refreshed = eventService.findById(eventId)
        if (refreshed != null) {
            call.respond(HttpStatusCode.OK, refreshed)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    get("/events/{id}/ratings") {
        val eventId = call.parameters["id"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing event id")
        val userId = call.userIdOrNull()
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        val event = eventService.findById(eventId)
        if (event == null || !userDataService.canAccess(event, userId)) {
            return@get call.respond(HttpStatusCode.NotFound, "No such event")
        }

        // F-28: utisci blokiranih u bilo kom smeru se ne vide, isto kao njihovi dogadjaji
        val hidden = userDataService.hiddenOwnerIds(userId)
        val visible = ratingService.findForEvent(eventId).filter { it.userId !in hidden }
        call.respond(HttpStatusCode.OK, visible)
    }
}
