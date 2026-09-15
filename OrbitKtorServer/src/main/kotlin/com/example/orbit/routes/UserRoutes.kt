package com.example.orbit.routes

import com.example.orbit.model.ExposedUser
import com.example.orbit.service.ExposedUserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** REST za korisnike, samo registracija i citanje */
fun Route.userRoutes(userService: ExposedUserService) {

    /** Registracija ili izmena profila, vraca 200 */
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
