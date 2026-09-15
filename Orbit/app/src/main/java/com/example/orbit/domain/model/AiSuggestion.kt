package com.example.orbit.domain.model

/** F-31: AI predlog, organizator moze da izmeni oba */
data class AiSuggestion(
    val category: EventCategory,
    val description: String,
)
