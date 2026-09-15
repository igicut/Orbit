package com.example.orbit.plugins

import com.example.orbit.routes.aiRoutes
import com.example.orbit.service.AiSuggestService
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/** Besplatan model; GEMINI_MODEL ga menja ako ga ugase */
private const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash-lite"

/** Rute bez baze */
fun Application.configureRouting() {
    // Kljuc samo iz env promenljive; bez njega AI je iskljucen
    val aiService = AiSuggestService(
        apiKey = System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() },
        model = System.getenv("GEMINI_MODEL")?.takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_MODEL,
    )
    if (!aiService.isConfigured) {
        log.warn("GEMINI_API_KEY is not set - POST /events/ai-suggest will answer 503")
    }

    routing {
        get("/") {
            call.respondText("Orbit server is running")
        }
        // Bez tokena bi bilo ko trosio Gemini kvotu
        authenticate(JWT_AUTH) {
            aiRoutes(aiService)
        }
    }
}
