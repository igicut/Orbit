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

/**
 * F-27 - rating endpoints.
 *
 * The paths sit under /events because a rating belongs to an event, but they live
 * in their own file because they are a separate concern. Route files group by
 * concern, not by URL prefix.
 */
fun Route.ratingRoutes(
    ratingService: ExposedRatingService,
    eventService: ExposedEventService,
) {

    /**
     * Submit or change a rating. PATCH rather than POST because it modifies part
     * of an existing event.
     *
     * The event is checked first: without it, rating a deleted event would quietly
     * insert a row pointing at nothing, and those orphans never get cleaned up.
     *
     * Responds with the refreshed event so the client gets the new average without
     * a second request.
     */
    patch("/events/{id}/rating") {
        val eventId = call.parameters["id"]
            ?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing event id")
        val userId = call.userIdOrNull()
            ?: return@patch call.respond(HttpStatusCode.BadRequest, "Missing $USER_ID_HEADER header")

        val event = eventService.findById(eventId)
            ?: return@patch call.respond(HttpStatusCode.NotFound, "No such event")

        // F-27 - an event that has not happened cannot be judged. Enforced here
        // as well as hidden in the UI: the client is not the security boundary,
        // and hiding a control is not the same as forbidding the request.
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

        // Reuse the existing row's id when re-rating, so the primary key is stable.
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
