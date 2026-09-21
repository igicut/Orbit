package com.example.orbit.routes

import com.example.orbit.model.Visibility
import com.example.orbit.service.ExposedEventService
import com.example.orbit.service.ExposedUserDataService
import com.example.orbit.service.ExposedUserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Tudji profili; nalog pravi /auth/signup, izmenu /users/me */
fun Route.userRoutes(
    userService: ExposedUserService,
    eventService: ExposedEventService,
    userDataService: ExposedUserDataService,
) {

    get("/users/{id}") {
        val id = call.parameters["id"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
        val user = userService.read(id)
        if (user != null) {
            call.respond(HttpStatusCode.OK, user)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    /**
     * Profil organizatora: njegovi javni dogadjaji, i buduci i prosli.
     * Prosli nisu u pretrazi, pa je ovo jedini put do njihovih utisaka za one koji nisu bili.
     */
    get("/users/{id}/events") {
        val ownerId = call.parameters["id"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing id")
        val userId = call.userIdOrNull()
            ?: return@get call.respond(HttpStatusCode.Unauthorized, "Not logged in")

        // F-28: blokada u bilo kom smeru sakriva profil, isto kao dogadjaje tog naloga
        if (ownerId in userDataService.hiddenOwnerIds(userId)) {
            return@get call.respond(HttpStatusCode.NotFound)
        }

        // findByOwner vraca i privatne; na tudjem profilu se vide samo javni
        val events = eventService.findByOwner(ownerId).filter { it.visibility == Visibility.PUBLIC }
        call.respond(HttpStatusCode.OK, events)
    }
}
