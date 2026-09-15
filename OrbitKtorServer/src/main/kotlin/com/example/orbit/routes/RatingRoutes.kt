package com.example.orbit.routes

import com.example.orbit.model.ExposedRating
import com.example.orbit.model.RatingRequest
import com.example.orbit.service.ExposedEventService
import com.example.orbit.service.ExposedRatingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import java.util.UUID

/** F-27: rute za ocene */
fun Route.ratingRoutes(
    ratingService: ExposedRatingService,
    eventService: ExposedEventService,
) {

    /** Slanje ili izmena ocene; vraca osvezen dogadjaj */
    patch("/events/{id}/rating") {
        val eventId = call.parameters["id"]
            ?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing event id")
        val userId = call.userIdOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing $USER_ID_HEADER header")

        val event = eventService.findById(eventId)
            ?: return@patch call.respond(HttpStatusCode.NotFound, "No such event")

        // F-27: ne moze se oceniti dogadjaj koji nije poceo
        if (event.startTime > System.currentTimeMillis()) {
            return@patch call.respond(
                HttpStatusCode.Conflict,
                "Cannot rate an event that has not started yet",
            )
        }

        val body = call.receive<RatingRequest>()
        if (body.value !in 1..5) {
            return@patch call.respond(HttpStatusCode.BadRequest, "value must be between 1 and 5")
        }

        // Isti id pri ponovnom ocenjivanju
        val existing = ratingService.findByUserForEvent(eventId, userId)

        ratingService.upsert(
            ExposedRating(
                id = existing?.id ?: UUID.randomUUID().toString(),
                eventId = eventId,
                userId = userId,
                value = body.value,
                comment = body.comment,
            )
        )

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

        if (eventService.findById(eventId) == null) {
            return@get call.respond(HttpStatusCode.NotFound, "No such event")
        }

        call.respond(HttpStatusCode.OK, ratingService.findForEvent(eventId))
    }
}
