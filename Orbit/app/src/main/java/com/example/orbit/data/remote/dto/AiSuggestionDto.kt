package com.example.orbit.data.remote.dto

import com.example.orbit.domain.model.EventCategory
import kotlinx.serialization.Serializable

/** F-31: telo zahteva za POST /events/ai-suggest */
@Serializable
data class AiSuggestRequestDto(
    val title: String,
    val description: String,
)

/** F-31: predlog servera, kategorija je vec validna */
@Serializable
data class AiSuggestionDto(
    val category: EventCategory,
    val description: String,
)

/** F-43: telo zahteva za POST /search/parse */
@Serializable
data class SearchParseRequestDto(
    val text: String,
)

/**
 * F-43: filteri koje je AI procitao iz recenice. Namerno tekst, a ne enumi:
 * nepoznato ime ne sme da obori parsiranje, domen ga sam preskace.
 */
@Serializable
data class ParsedSearchDto(
    val keywords: String = "",
    val category: String? = null,
    val radius: String? = null,
    val dateWindow: String? = null,
    val sort: String? = null,
)
