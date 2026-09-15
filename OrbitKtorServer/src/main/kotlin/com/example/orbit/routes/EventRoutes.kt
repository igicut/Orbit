package com.example.orbit.routes

import com.example.orbit.model.EventCategory
import com.example.orbit.model.ExposedEvent
import com.example.orbit.service.ExposedEventService
import com.example.orbit.service.ExposedUserDataService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable

// ---- F-12: ogranicenja izmene, isto kao u aplikaciji ----
private const val MAX_RESCHEDULE_DAYS = 14
private const val MAX_RESCHEDULE_MS = MAX_RESCHEDULE_DAYS * 24L * 60 * 60 * 1000
private const val SHORT_NOTICE_HOURS = 24
private const val SHORT_NOTICE_MS = SHORT_NOTICE_HOURS * 60L * 60 * 1000
private const val MAX_RELOCATION_KM = 50.0

/** F-35: isto kao EventDuration u aplikaciji; trajanje odredjuje kraj potvrde dolaska */
private const val MAX_DURATION_MINUTES = 7 * 24 * 60

/** Udaljenost za ogranicenje premestanja i potvrdu dolaska */
internal fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    return earthRadiusKm * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}
private const val MAX_RADIUS_KM = 500.0

@Serializable
data class JoinRequest(val accessCode: String)

/** F-12/F-21: rute za dogadjaje */
fun Route.eventRoutes(
    eventService: ExposedEventService,
    userDataService: ExposedUserDataService,
) {

    /** Kreiranje; ownerId iz tokena, duplikat id vraca 409 */
    post("/events") {
        val userId = call.userIdOrNull()
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        val incoming = call.receive<ExposedEvent>()

        if (incoming.title.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, "title must not be blank")
        }
        // Kapacitet odredjuje broj mesta za prijavu, null je bez ogranicenja
        if (incoming.capacity != null && incoming.capacity < 1) {
            return@post call.respond(HttpStatusCode.BadRequest, "Capacity must be at least 1")
        }
        if (incoming.price != null && incoming.price < 0.0) {
            return@post call.respond(HttpStatusCode.BadRequest, "Price cannot be negative")
        }
        if (incoming.durationMinutes != null && incoming.durationMinutes !in 1..MAX_DURATION_MINUTES) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                "Duration must be between 1 and $MAX_DURATION_MINUTES minutes",
            )
        }
        if (eventService.findById(incoming.id) != null) {
            return@post call.respond(HttpStatusCode.Conflict, "An event with this id already exists")
        }

        val event = incoming.copy(
            ownerId = userId,
            avgRating = 0f,
            ratingCount = 0,
            registeredCount = 0,
            syncedToBackend = true,
        )
        eventService.create(event)
        call.respond(HttpStatusCode.Created, event)
    }

    /** Pretraga; lat/lng obavezni, bez radiusKm nema limita */
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
        }

        if (radiusKm != null && (radiusKm <= 0.0 || radiusKm > MAX_RADIUS_KM)) {
            return@get call.respond(
                HttpStatusCode.BadRequest,
                "radiusKm must be greater than 0 and at most $MAX_RADIUS_KM",
            )
        }

        // valueOf baca izuzetak za nepoznato ime, bio bi 500
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
        val userId = call.userIdOrNull()
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        // Privatni bez pristupa izgleda kao da ne postoji
        val event = eventService.findById(id)
        if (event != null && userDataService.canAccess(event, userId)) {
            call.respond(HttpStatusCode.OK, event)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    /** F-21: kod otvara privatni dogadjaj i pamti clanstvo */
    post("/events/join") {
        val userId = call.userIdOrNull()
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        // Kodovi su uppercase, prihvatamo bilo koja slova
        val code = call.receive<JoinRequest>().accessCode.trim().uppercase()
        val event = eventService.findByAccessCode(code)
            ?: return@post call.respond(HttpStatusCode.NotFound)

        if (event.ownerId != userId) userDataService.join(event.id, userId)
        call.respond(HttpStatusCode.OK, event)
    }

    /** Samo vlasnik */
    put("/events/{id}") {
        val id = call.parameters["id"]
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing id")
        val userId = call.userIdOrNull()
            ?: return@put call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        val existing = eventService.findById(id)
            ?: return@put call.respond(HttpStatusCode.NotFound)

        if (existing.ownerId != userId) {
            return@put call.respond(HttpStatusCode.Forbidden, "You do not own this event")
        }

        val incoming = call.receive<ExposedEvent>()
        val now = System.currentTimeMillis()

        // ---- F-12: provera ogranicenja i na serveru ----

        // Zapoceti dogadjaj se ne menja
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

        // Najvise dve nedelje pomeranja
        val shift = kotlin.math.abs(incoming.startTime - existing.startTime)
        if (shift > MAX_RESCHEDULE_MS) {
            return@put call.respond(
                HttpStatusCode.BadRequest,
                "An event cannot be moved more than $MAX_RESCHEDULE_DAYS days from its original time",
            )
        }

        // Blizu pocetka sme samo odlaganje
        val startsSoon = existing.startTime - now <= SHORT_NOTICE_MS
        if (startsSoon && incoming.startTime < existing.startTime) {
            return@put call.respond(
                HttpStatusCode.Conflict,
                "Within $SHORT_NOTICE_HOURS hours of the start, an event can only be postponed",
            )
        }

        // Ne sme premestanje u drugi grad
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
        if (incoming.durationMinutes != null && incoming.durationMinutes !in 1..MAX_DURATION_MINUTES) {
            return@put call.respond(
                HttpStatusCode.BadRequest,
                "Duration must be between 1 and $MAX_DURATION_MINUTES minutes",
            )
        }

        if (!eventService.update(id, incoming)) {
            return@put call.respond(
                HttpStatusCode.Conflict,
                "Capacity cannot be lower than the number of people already registered",
            )
        }
        call.respond(HttpStatusCode.OK, eventService.findById(id)!!)
    }

    /** Samo vlasnik i samo pre pocetka */
    delete("/events/{id}") {
        val id = call.parameters["id"]
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing id")
        val userId = call.userIdOrNull()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        val existing = eventService.findById(id)
            ?: return@delete call.respond(HttpStatusCode.NotFound)

        if (existing.ownerId != userId) {
            return@delete call.respond(HttpStatusCode.Forbidden, "You do not own this event")
        }

        // Brisanje bi odnelo dolaske i ocene gostiju, a time i njihovu istoriju
        if (existing.startTime <= System.currentTimeMillis()) {
            return@delete call.respond(
                HttpStatusCode.Conflict,
                "An event that has already started cannot be deleted",
            )
        }

        eventService.delete(id)
        call.respond(HttpStatusCode.NoContent)
    }
}
