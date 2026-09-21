package com.example.orbit.data.remote

import com.example.orbit.data.remote.dto.RatingDto
import com.example.orbit.domain.model.Rating

/** F-40: utisak sa servera u domen */
fun RatingDto.toRating(): Rating = Rating(
    id = id,
    eventId = eventId,
    userId = userId,
    value = value,
    comment = comment,
    createdAt = createdAt,
    imagePath = imagePath,
    authorName = authorName,
)
