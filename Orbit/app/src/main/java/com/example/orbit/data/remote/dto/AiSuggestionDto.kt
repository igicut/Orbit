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
