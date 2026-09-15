package com.example.orbit.domain.model

/** F-36: red istorije posecenih dogadjaja */
data class AttendedEvent(
    val event: Event,
    val checkedInAt: Long,
    val myRating: Int?,
)
