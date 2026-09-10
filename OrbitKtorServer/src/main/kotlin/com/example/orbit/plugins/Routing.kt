package com.example.orbit.plugins

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Routes that need no database.
 *
 * Feature routes live under routes/ and are registered from the module that owns
 * their dependencies - see Databases.kt. /health is there too, because it needs
 * the database connection to be worth anything.
 */
fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Orbit server is running")
        }
    }
}
