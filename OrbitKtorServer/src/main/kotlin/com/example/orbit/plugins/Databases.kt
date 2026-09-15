package com.example.orbit.plugins

import com.example.orbit.db.createSchema
import com.example.orbit.routes.authRoutes
import com.example.orbit.routes.eventRoutes
import com.example.orbit.routes.healthRoutes
import com.example.orbit.routes.meRoutes
import com.example.orbit.routes.ratingRoutes
import com.example.orbit.routes.registrationRoutes
import com.example.orbit.routes.userRoutes
import com.example.orbit.service.AuthService
import com.example.orbit.service.ExposedEventService
import com.example.orbit.service.ExposedRatingService
import com.example.orbit.service.ExposedRegistrationService
import com.example.orbit.service.ExposedUserDataService
import com.example.orbit.service.ExposedUserService
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

/** Konekcija na bazu (R2DBC), tabele i rute sa bazom */
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
    val userDataService = ExposedUserDataService(database)
    val registrationService = ExposedRegistrationService(database)
    val authService = AuthService(database, userService)
    // Van routing bloka, unutra attributes pripada ruti
    val tokenService = attributes[TokenServiceKey]

    routing {
        healthRoutes(database)
        // Prijava i registracija ogranicene po IP adresi, protiv pogadjanja lozinke
        rateLimit(AUTH_RATE_LIMIT) {
            authRoutes(authService, tokenService)
        }

        // Sve ostalo samo sa vazecim tokenom
        authenticate(JWT_AUTH) {
            userRoutes(userService)
            meRoutes(eventService, userService, ratingService, registrationService, userDataService)
            eventRoutes(eventService, userDataService)
            ratingRoutes(ratingService, eventService, userDataService, registrationService)
            registrationRoutes(eventService, registrationService, userDataService)
        }
    }
}
