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
import com.example.orbit.service.EMBEDDING_BATCH_SIZE
import com.example.orbit.service.EmbeddingService
import com.example.orbit.service.ExposedEmbeddingService
import com.example.orbit.service.ExposedEventService
import com.example.orbit.service.ExposedRatingService
import com.example.orbit.service.ExposedRegistrationService
import com.example.orbit.service.ExposedUserDataService
import com.example.orbit.service.ExposedUserService
import com.example.orbit.service.ImageStorage
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.routing
import kotlinx.coroutines.launch
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
    // F-37: brisanje dogadjaja nosi i njegove slike
    val imageStorage = ImageStorage.fromEnvironment()
    // F-32: vektori za semanticku pretragu
    val embeddingService = EmbeddingService.fromEnvironment()
    val embeddingStore = ExposedEmbeddingService(database)
    backfillEmbeddings(embeddingService, embeddingStore)

    routing {
        healthRoutes(database)
        // Prijava i registracija ogranicene po IP adresi, protiv pogadjanja lozinke
        rateLimit(AUTH_RATE_LIMIT) {
            authRoutes(authService, tokenService)
        }

        // Sve ostalo samo sa vazecim tokenom
        authenticate(JWT_AUTH) {
            userRoutes(userService, eventService, userDataService)
            meRoutes(eventService, userService, ratingService, registrationService, userDataService)
            eventRoutes(eventService, userDataService, imageStorage, embeddingService, embeddingStore)
            ratingRoutes(ratingService, eventService, userDataService, registrationService, imageStorage)
            registrationRoutes(eventService, registrationService, userDataService)
        }
    }
}

/**
 * F-32: javni dogadjaji bez vektora (seed podaci, ili pad Gemini-ja pri pravljenju)
 * dobijaju ga u pozadini, da pretraga ne bi cekala na pokretanje.
 */
private fun Application.backfillEmbeddings(
    embeddingService: EmbeddingService,
    embeddingStore: ExposedEmbeddingService,
) {
    if (!embeddingService.isConfigured) {
        log.warn("GEMINI_API_KEY is not set - search falls back to keyword matching only")
        return
    }

    launch {
        var embedded = 0

        // Vise krugova, jer jedan batch pokriva samo EMBEDDING_BATCH_SIZE dogadjaja
        while (true) {
            val missing = embeddingStore.withoutEmbedding(EMBEDDING_BATCH_SIZE)
            if (missing.isEmpty()) break

            val vectors = embeddingService.embedDocuments(missing.map { it.text })
            var saved = 0
            missing.forEachIndexed { index, input ->
                vectors.getOrNull(index)?.let { vector ->
                    embeddingStore.save(input.eventId, vector)
                    saved++
                }
            }
            embedded += saved

            // Neuspeh znaci da Gemini ne odgovara; bez prekida bi se isti dogadjaji vrteli u krug
            if (saved < missing.size) {
                log.warn("Embedded $embedded events, ${missing.size - saved} failed - stopping")
                return@launch
            }
        }

        if (embedded > 0) log.info("Embedded $embedded events without a vector")
    }
}
