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

/** F-43: recenica korisnika prevedena u filtere; null polje znaci da ga recenica ne pominje */
@Serializable
data class ParsedSearch(
    val keywords: String = "",
    val category: EventCategory? = null,
    val radius: String? = null,
    val dateWindow: String? = null,
    val sort: String? = null,
    val price: String? = null,
)

/**
 * F-43: imena enuma iz aplikacije (SearchRadius, DateWindow, EventSort u EventFilters.kt).
 * Menjaju se zajedno; podrazumevane vrednosti (ANYWHERE, ANY, SOONEST) nisu tu jer ih
 * oznacava izostanak polja.
 */
private val RADIUS_NAMES = listOf("WALK", "NEARBY", "CITY", "REGION")
private val DATE_WINDOW_NAMES = listOf("TODAY", "THIS_WEEK", "THIS_MONTH", "WEEKEND")
private val SORT_NAMES = listOf("NEAREST")
private val PRICE_NAMES = listOf("FREE", "UP_TO_1000", "UP_TO_5000")

/** Odgovor prati semu, ali visak polja ne sme da obori parsiranje */
private val lenientJson = Json { ignoreUnknownKeys = true }

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

    /** F-43: sema dozvoljava samo poznata imena, pa model ne moze da izmisli filter */
    private val searchConfig = GenerateContentConfig.builder()
        .systemInstruction(Content.fromParts(Part.fromText(SEARCH_PROMPT)))
        .responseMimeType("application/json")
        // Ista recenica treba da da iste filtere
        .temperature(0f)
        .responseSchema(
            Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(
                    mapOf(
                        "keywords" to Schema.builder().type(Type.Known.STRING).build(),
                        "category" to Schema.builder()
                            .type(Type.Known.STRING)
                            .enum_(EventCategory.entries.map { it.name })
                            .build(),
                        "radius" to Schema.builder().type(Type.Known.STRING).enum_(RADIUS_NAMES).build(),
                        "dateWindow" to Schema.builder().type(Type.Known.STRING).enum_(DATE_WINDOW_NAMES).build(),
                        "sort" to Schema.builder().type(Type.Known.STRING).enum_(SORT_NAMES).build(),
                        "price" to Schema.builder().type(Type.Known.STRING).enum_(PRICE_NAMES).build(),
                    )
                )
                .required(listOf("keywords"))
                .build()
        )
        .build()

    /** F-43: null ako model nije dostupan ili odgovor ne valja; aplikacija tada pretrazuje tekstom */
    suspend fun parseSearch(text: String): ParsedSearch? {
        val client = client ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val reply = client.models.generateContent(model, text, searchConfig).text()
                    ?: return@withContext null.also { log.warn("Gemini returned no text for a search") }
                lenientJson.decodeFromString<ParsedSearch>(reply)
            } catch (e: ApiException) {
                log.warn("Gemini API error ${e.code()}: ${e.message()}")
                null
            } catch (e: GenAiIOException) {
                log.warn("Could not reach Gemini", e)
                null
            } catch (e: SerializationException) {
                log.warn("Gemini search reply did not match the schema", e)
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

        // Primeri su na srpskom jer tako korisnici kucaju; "nedelja" je i sedmica i dan
        val SEARCH_PROMPT = """
            You turn one search sentence from an event discovery app into filters.
            Set a field only when the sentence clearly asks for it. Leave every other field out.

            category: ${EventCategory.entries.joinToString { "${it.name} (${it.label})" }}.
            radius: WALK = walking distance, about 1 km. NEARBY = "blizu", "u blizini", "near me",
              about 5 km. CITY = "u gradu", about 25 km. REGION = about 100 km.
            dateWindow: TODAY = "danas", "veceras", "tonight". THIS_WEEK = "ove nedelje", "this week".
              THIS_MONTH = "ovog meseca". WEEKEND = "vikend", "krajem nedelje", "subota", "u nedelju".
              In Serbian "nedelja" means both "week" and "Sunday": "ove nedelje" is THIS_WEEK,
              "u nedelju" and "krajem nedelje" are WEEKEND.
            sort: NEAREST only for "najblize" or "closest first".
            price: FREE = "besplatno", "free", "bez ulaznice", "besplatan ulaz".
              UP_TO_1000 = up to about 1000 dinars, UP_TO_5000 = up to about 5000 dinars.
              Prices are in Serbian dinars (RSD); round the sentence's budget up to the nearer
              of the two limits.
            keywords: the topic words that the fields above do not already express, in the
              user's own language and alphabet. Drop filler such as "zelim", "hocu", "nesto",
              "dogadjaj". Do not repeat the category as a keyword. Empty string if nothing is left.

            Examples:
            "zelim da slusam muziku blizu mene krajem nedelje"
              -> {"keywords":"","category":"MUSIC","radius":"NEARBY","dateWindow":"WEEKEND"}
            "dzez koncert veceras" -> {"keywords":"dzez","category":"MUSIC","dateWindow":"TODAY"}
            "muzika sa besplatnim ulazom" -> {"keywords":"","category":"MUSIC","price":"FREE"}
            "radionica do 3000 dinara" -> {"keywords":"radionica","price":"UP_TO_5000"}
            "running this week, closest first"
              -> {"keywords":"running","category":"SPORT","dateWindow":"THIS_WEEK","sort":"NEAREST"}
            "kviz" -> {"keywords":"kviz"}
        """.trimIndent()
    }
}
