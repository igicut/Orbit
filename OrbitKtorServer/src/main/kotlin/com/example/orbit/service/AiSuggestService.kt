package com.example.orbit.service

import com.example.orbit.model.EventCategory
import com.google.genai.Client
import com.google.genai.errors.ApiException
import com.google.genai.errors.GenAiIOException
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.Schema
import com.google.genai.types.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(AiSuggestService::class.java)

@Serializable
data class AiSuggestion(
    val category: EventCategory,
    val description: String,
)

/** F-30: Gemini predlaze kategoriju i opis, kljuc ostaje na serveru */
class AiSuggestService(apiKey: String?, private val model: String) {

    private val client: Client? = apiKey?.let { Client.builder().apiKey(it).build() }

    /** false bez kljuca; ruta tada vraca 503 */
    val isConfigured: Boolean get() = client != null

    private val config = GenerateContentConfig.builder()
        .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_PROMPT)))
        .responseMimeType("application/json")
        .responseSchema(
            Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(
                    mapOf(
                        "category" to Schema.builder()
                            .type(Type.Known.STRING)
                            .enum_(EventCategory.entries.map { it.name })
                            .build(),
                        "description" to Schema.builder()
                            .type(Type.Known.STRING)
                            .build(),
                    )
                )
                .required(listOf("category", "description"))
                .build()
        )
        .build()

    /** null ako model nije dostupan ili odgovor ne valja */
    suspend fun suggest(title: String, notes: String): AiSuggestion? {
        val client = client ?: return null
        val prompt = "Title: $title\nNotes: ${notes.ifBlank { "(none)" }}"

        // SDK poziv blokira, zato Dispatchers.IO
        return withContext(Dispatchers.IO) {
            try {
                val text = client.models.generateContent(model, prompt, config).text()
                    ?: return@withContext null.also { log.warn("Gemini returned no text (blocked or empty)") }
                Json.decodeFromString<AiSuggestion>(text).takeIf { it.description.isNotBlank() }
            } catch (e: ApiException) {
                log.warn("Gemini API error ${e.code()}: ${e.message()}")
                null
            } catch (e: GenAiIOException) {
                log.warn("Could not reach Gemini", e)
                null
            } catch (e: SerializationException) {
                log.warn("Gemini reply did not match the schema", e)
                null
            }
        }
    }

    private companion object {
        val SYSTEM_PROMPT = """
            You help organisers describe local events in an event discovery app.
            From the event title and the organiser's notes, return:
            - category: the single best fit from ${EventCategory.entries.joinToString { "${it.name} (${it.label})" }}.
            - description: 2 to 4 friendly sentences for someone deciding whether to attend.
            Write the description in the same language and alphabet as the title and notes.
            Use only facts given in the input. Never invent dates, times, places, prices or names;
            leave out anything that is not known.
        """.trimIndent()
    }
}
