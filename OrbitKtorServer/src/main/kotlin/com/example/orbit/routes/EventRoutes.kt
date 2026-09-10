package com.example.orbit.routes

import com.example.orbit.model.EventCategory
import com.example.orbit.model.ExposedEvent
import com.example.orbit.service.ExposedEventService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put

private const val DEFAULT_RADIUS_KM = 10.0

// ---- F-12: edit limits ---------------------------------------------------
// Mirrored in the Android client so the form can refuse early with a readable
// message; these are the ones that actually bind.
private const val MAX_RESCHEDULE_DAYS = 14
private const val MAX_RESCHEDULE_MS = MAX_RESCHEDULE_DAYS * 24L * 60 * 60 * 1000
private const val SHORT_NOTICE_HOURS = 24
private const val SHORT_NOTICE_MS = SHORT_NOTICE_HOURS * 60L * 60 * 1000
private const val MAX_RELOCATION_KM = 50.0

/** Great-circle distance, for the relocation limit. */
private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    return earthRadiusKm * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}
private const val MAX_RADIUS_KM = 500.0

/**
 * F-12 / F-21 - the event endpoints the Android client calls.
 */
fun Route.eventRoutes(eventService: ExposedEventService) {

    /**
     * Create.
     *
     * ownerId is taken from the X-User-Id header and overwrites whatever the body
     * said, so a caller cannot create an event owned by somebody else. The rating
     * summary is zeroed for the same reason.
     *
     * A duplicate id returns 409 rather than 500. That matters for F-15: when the
     * client retries a push whose response was lost, the event is already here, and
     * the client should treat 409 as "already synced" rather than as a failure.
     */
    post("/events") {
        val userId = call.userIdOrNull()
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing $USER_ID_HEADER header")

        val incoming = call.receive<ExposedEvent>()

        if (incoming.title.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, "title must not be blank")
        }
        if (eventService.findById(incoming.id) != null) {
            return@post call.respond(HttpStatusCode.Conflict, "An event with this id already exists")
        }

        val event = incoming.copy(
            ownerId = userId,
            avgRating = 0f,
            ratingCount = 0,
            syncedToBackend = true,
        )
        eventService.create(event)
        call.respond(HttpStatusCode.Created, event)
    }

    /**
     * Search. Query parameters: lat, lng (required), radiusKm and category (optional).
     *
     * lat/lng are rejected rather than defaulted when missing. (0,0) is a real
     * place in the Gulf of Guinea, so silently defaulting returns an empty list
     * that looks like a bug in the app rather than a bad request.
     */
    get("/events") {
        val params = call.request.queryParameters

        val latitude = params["lat"]?.toDoubleOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "lat is required and must be a number")
        val longitude = params["lng"]?.toDoubleOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "lng is required and must be a number")

        if (latitude !in -90.0..90.0) {
            return@get call.respond(HttpStatusCode.BadRequest, "lat must be between -90 and 90")
        }
        if (longitude !in -180.0..180.0) {
            return@get call.respond(HttpStatusCode.BadRequest, "lng must be between -180 and 180")
        }

        val radiusKm = params["radiusKm"]?.let {
            it.toDoubleOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "radiusKm must be a number")
        } ?: DEFAULT_RADIUS_KM

        if (radiusKm <= 0.0 || radiusKm > MAX_RADIUS_KM) {
            return@get call.respond(
                HttpStatusCode.BadRequest,
                "radiusKm must be greater than 0 and at most $MAX_RADIUS_KM",
            )
        }

        // valueOf throws on an unknown name, which would surface as a 500.
        val category = params["category"]?.let { name ->
            EventCategory.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Unknown category: $name")
        }

        call.respond(
            HttpStatusCode.OK,
            eventService.search(latitude, longitude, radiusKm, category, params["q"]),
        )
    }

    get("/events/{id}") {
        val id = call.parameters["id"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")

        val event = eventService.findById(id)
        if (event != null) {
            call.respond(HttpStatusCode.OK, event)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    /**
     * F-21 - join a private event by its access code.
     *
     * Three segments, so this never collides with GET /events/{id}, which has two.
     * The service also checks the event is actually PRIVATE.
     */
    get("/events/by-code/{code}") {
        val code = call.parameters["code"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing access code")

        // Codes are generated uppercase; accept whatever case was typed.
        val event = eventService.findByAccessCode(code.trim().uppercase())
        if (event != null) {
            call.respond(HttpStatusCode.OK, event)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    /** Owner only. */
    put("/events/{id}") {
        val id = call.parameters["id"]
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
        val userId = call.userIdOrNull()
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing $USER_ID_HEADER header")

        val existing = eventService.findById(id)
            ?: return@put call.respond(HttpStatusCode.NotFound)

        if (existing.ownerId != userId) {
            return@put call.respond(HttpStatusCode.Forbidden, "You do not own this event")
        }

        val incoming = call.receive<ExposedEvent>()
        val now = System.currentTimeMillis()

        // ---- F-12: what may be changed, and by how much --------------------
        // Enforced here as well as in the app. The client is not the authority;
        // a hidden control is not the same as a forbidden request.

        // An event that has already begun is history. Editing it would rewrite
        // what people actually attended.
        if (existing.startTime <= now) {
            return@put call.respond(
                HttpStatusCode.Conflict,
                "An event that has already started cannot be edited",
            )
        }

        if (incoming.startTime <= now) {
            return@put call.respond(
                HttpStatusCode.BadRequest,
                "The new start time must be in the future",
            )
        }

        // Beyond a fortnight it is not a rescheduled event, it is a different
        // one - and everyone who saved it planned around the old date.
        val shift = kotlin.math.abs(incoming.startTime - existing.startTime)
        if (shift > MAX_RESCHEDULE_MS) {
            return@put call.respond(
                HttpStatusCode.BadRequest,
                "An event cannot be moved more than $MAX_RESCHEDULE_DAYS days from its original time",
            )
        }

        // Close to the start, postponing is fine but bringing it forward is not:
        // anyone who planned around the old time would simply miss it.
        val startsSoon = existing.startTime - now <= SHORT_NOTICE_MS
        if (startsSoon && incoming.startTime < existing.startTime) {
            return@put call.respond(
                HttpStatusCode.Conflict,
                "Within $SHORT_NOTICE_HOURS hours of the start, an event can only be postponed",
            )
        }

        // Moving it across the country is a different event too.
        val movedKm = distanceKm(
            existing.latitude, existing.longitude,
            incoming.latitude, incoming.longitude,
        )
        if (movedKm > MAX_RELOCATION_KM) {
            return@put call.respond(
                HttpStatusCode.BadRequest,
                "An event cannot be moved more than $MAX_RELOCATION_KM km from its original location",
            )
        }

        if (incoming.capacity != null && incoming.capacity < 1) {
            return@put call.respond(HttpStatusCode.BadRequest, "Capacity must be at least 1")
        }
        if (incoming.price != null && incoming.price < 0.0) {
            return@put call.respond(HttpStatusCode.BadRequest, "Price cannot be negative")
        }

        eventService.update(id, incoming)
        call.respond(HttpStatusCode.OK, eventService.findById(id)!!)
    }

    /** Owner only. */
    delete("/events/{id}") {
        val id = call.parameters["id"]
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")
        val userId = call.userIdOrNull()
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing $USER_ID_HEADER header")

        val existing = eventService.findById(id)
            ?: return@delete call.respond(HttpStatusCode.NotFound)

        if (existing.ownerId != userId) {
            return@delete call.respond(HttpStatusCode.Forbidden, "You do not own this event")
        }

        eventService.delete(id)
        call.respond(HttpStatusCode.NoContent)
    }
}
