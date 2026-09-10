package com.example.orbit.plugins

import com.example.orbit.db.createSchema
import com.example.orbit.routes.eventRoutes
import com.example.orbit.routes.healthRoutes
import com.example.orbit.routes.ratingRoutes
import com.example.orbit.routes.userRoutes
import com.example.orbit.service.ExposedEventService
import com.example.orbit.service.ExposedRatingService
import com.example.orbit.service.ExposedUserService
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

/**
 * Opens the database connection, creates any missing tables, then registers every
 * route that needs database access.
 *
 * Credentials come from application.yaml, which reads them from environment
 * variables with a fallback - so nothing secret is committed.
 *
 * This is Exposed's R2DBC driver, not JDBC. Most Exposed examples online use JDBC
 * (Database.connect / transaction { }); the equivalents here are
 * R2dbcDatabase.connect and suspendTransaction { }.
 */
suspend fun Application.configureDatabases() {
    val database = R2dbcDatabase.connect(
        url = environment.config.property("storage.url").getString(),
        user = environment.config.property("storage.user").getString(),
        password = environment.config.property("storage.password").getString(),
    )

    createSchema(database)

    val userService = ExposedUserService(database)
    val eventService = ExposedEventService(database)
    val ratingService = ExposedRatingService(database)

    routing {
        healthRoutes(database)
        userRoutes(userService)
        eventRoutes(eventService)
        ratingRoutes(ratingService, eventService)
    }
}
