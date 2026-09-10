package com.example.orbit.routes

import com.example.orbit.model.ExposedUser
import com.example.orbit.service.ExposedUserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * REST endpoints for users.
 *
 * Deliberately only two. A user profile here exists so an event can be shown as
 * "Organised by <name>" rather than a UUID - there is no profile editing and no
 * account deletion, so PUT and DELETE would be endpoints with no feature behind
 * them. Listing every user was also a needless disclosure.
 *
 * Declared as an extension on Route rather than Application, so whoever owns the
 * service decides where they are mounted - see plugins/Databases.kt.
 * This is the shape the /events endpoints (F-12) should follow.
 */
fun Route.userRoutes(userService: ExposedUserService) {

    /**
     * Register this device, or update its profile.
     *
     * Responds 200 with the stored user rather than 201, because it is not
     * strictly a creation - calling it twice is normal and expected.
     */
    post("/users") {
        val user = call.receive<ExposedUser>()
        if (user.id.isBlank() || user.displayName.isBlank()) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                "id and displayName are required",
            )
        }
        call.respond(HttpStatusCode.OK, userService.register(user))
    }

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

}
