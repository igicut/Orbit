package com.example.orbit.data.remote.dto

import kotlinx.serialization.Serializable

/** F-13: podaci naloga sa GET /users/me/sync */
@Serializable
data class UserSyncDto(
    val ownEvents: List<EventDto> = emptyList(),
    val joinedEvents: List<EventDto> = emptyList(),
    val registeredEvents: List<EventDto> = emptyList(),
    val attendances: List<AttendanceRecordDto> = emptyList(),
    val blockedUsers: List<UserDto> = emptyList(),
    val ratings: List<RatingDto> = emptyList(),
)
