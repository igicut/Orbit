package com.example.orbit.routes

import com.example.orbit.model.AttendanceRecord
import com.example.orbit.model.ExposedEvent
import com.example.orbit.model.ExposedRating
import com.example.orbit.model.ExposedUser
import com.example.orbit.service.ExposedEventService
import com.example.orbit.service.ExposedRatingService
import com.example.orbit.service.ExposedRegistrationService
import com.example.orbit.service.ExposedUserDataService
import com.example.orbit.service.ExposedUserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable

private const val MAX_NAME_LENGTH = 100

@Serializable
data class ProfileUpdateRequest(val displayName: String)

/** Sve sto aplikacija posle prijave vraca u lokalnu bazu */
@Serializable
data class UserSyncResponse(
    val ownEvents: List<ExposedEvent>,
    val joinedEvents: List<ExposedEvent>,
    val registeredEvents: List<ExposedEvent>,
    val attendances: List<AttendanceRecord>,
    val blockedUsers: List<ExposedUser>,
    val ratings: List<ExposedRating>,
)

/** F-13: podaci prijavljenog naloga, /users/me/... */
fun Route.meRoutes(
    eventService: ExposedEventService,
    userService: ExposedUserService,
    ratingService: ExposedRatingService,
    registrationService: ExposedRegistrationService,
    userDataService: ExposedUserDataService,
) {

    get("/users/me/sync") {
        val userId = call.userIdOrNull()
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        // F-28: blokada u bilo kom smeru krije tudje dogadjaje i kad je prijava vec postojala
        val hiddenOwners = userDataService.hiddenOwnerIds(userId)

        call.respond(
            HttpStatusCode.OK,
            UserSyncResponse(
                ownEvents = eventService.findByOwner(userId),
                joinedEvents = eventService.findByIds(userDataService.joinedEventIds(userId))
                    .filterNot { it.ownerId in hiddenOwners },
                registeredEvents = eventService.findByIds(registrationService.registeredEventIds(userId))
                    .filterNot { it.ownerId in hiddenOwners },
                attendances = registrationService.attendances(userId),
                blockedUsers = userService.readAll(userDataService.blockedIds(userId)),
                ratings = ratingService.findByUser(userId),
            ),
        )
    }

    /** Menja samo ime; interesovanja se ne diraju */
    patch("/users/me") {
        val userId = call.userIdOrNull()
            ?: return@patch call.respond(HttpStatusCode.Unauthorized, "Not logged in")
        val displayName = call.receive<ProfileUpdateRequest>().displayName.trim()

        if (displayName.isBlank() || displayName.length > MAX_NAME_LENGTH) {
            return@patch call.respond(
                HttpStatusCode.BadRequest,
                "displayName is required and limited to $MAX_NAME_LENGTH characters",
            )
        }

        val updated = userService.updateDisplayName(userId, displayName)
            ?: return@patch call.respond(HttpStatusCode.NotFound)
        call.respond(HttpStatusCode.OK, updated)
    }

    /** F-28: blokiranje prati nalog */
    put("/users/me/blocked/{blockedId}") {
        val userId = call.userIdOrNull()
            ?: return@put call.respond(HttpStatusCode.Unauthorized, "Not logged in")
        val blockedId = call.parameters["blockedId"]
            ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing user id")

        if (blockedId == userId) {
            return@put call.respond(HttpStatusCode.BadRequest, "You cannot block yourself")
        }
        if (userService.read(blockedId) == null) {
            return@put call.respond(HttpStatusCode.NotFound)
        }

        userDataService.block(userId, blockedId)
        call.respond(HttpStatusCode.NoContent)
    }

    delete("/users/me/blocked/{blockedId}") {
        val userId = call.userIdOrNull()
            ?: return@delete call.respond(HttpStatusCode.Unauthorized, "Not logged in")
        val blockedId = call.parameters["blockedId"]
            ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing user id")

        userDataService.unblock(userId, blockedId)
        call.respond(HttpStatusCode.NoContent)
    }
}
