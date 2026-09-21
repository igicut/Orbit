package com.example.orbit.routes

import com.example.orbit.service.AiSuggestService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

// Ogranicava cenu jednog zahteva
private const val MAX_TITLE_LENGTH = 200
private const val MAX_NOTES_LENGTH = 2000
private const val MAX_SEARCH_LENGTH = 200

@Serializable
data class AiSuggestRequest(
    val title: String,
    val description: String = "",
)

@Serializable
data class SearchParseRequest(val text: String)

/** F-30: POST /events/ai-suggest; 503 bez kljuca, 502 kad AI padne */
fun Route.aiRoutes(aiService: AiSuggestService) {

    post("/events/ai-suggest") {
        if (!aiService.isConfigured) {
            return@post call.respond(
                HttpStatusCode.ServiceUnavailable,
                "AI suggestions are not configured on this server",
            )
        }

        val request = call.receive<AiSuggestRequest>()
        val title = request.title.trim()
        val notes = request.description.trim()

        if (title.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, "title must not be blank")
        }
        if (title.length > MAX_TITLE_LENGTH || notes.length > MAX_NOTES_LENGTH) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                "title is limited to $MAX_TITLE_LENGTH characters and description to $MAX_NOTES_LENGTH",
            )
        }

        val suggestion = aiService.suggest(title, notes)
            ?: return@post call.respond(
                HttpStatusCode.BadGateway,
                "The AI service could not produce a suggestion",
            )

        call.respond(HttpStatusCode.OK, suggestion)
    }

    /** F-43: recenica u filtere; isti ugovor kao ai-suggest, aplikacija na gresku pretrazuje tekstom */
    post("/search/parse") {
        if (!aiService.isConfigured) {
            return@post call.respond(HttpStatusCode.ServiceUnavailable, "AI pretraga nije podesena na serveru")
        }

        val text = call.receive<SearchParseRequest>().text.trim()
        if (text.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, "Tekst pretrage je prazan")
        }
        if (text.length > MAX_SEARCH_LENGTH) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                "Tekst pretrage moze imati najvise $MAX_SEARCH_LENGTH znakova",
            )
        }

        val parsed = aiService.parseSearch(text)
            ?: return@post call.respond(HttpStatusCode.BadGateway, "AI nije uspeo da razume pretragu")

        call.respond(HttpStatusCode.OK, parsed)
    }
}
