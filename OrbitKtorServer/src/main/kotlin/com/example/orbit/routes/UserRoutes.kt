package com.example.orbit.routes

import com.example.orbit.service.ExposedUserService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Tudji profili; nalog pravi /auth/signup, izmenu /users/me */
fun Route.userRoutes(userService: ExposedUserService) {

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
