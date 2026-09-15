package com.example.orbit.data.local.dao

import androidx.room.Embedded
import com.example.orbit.data.local.entity.EventEntity

/** F-36: dogadjaj sa vremenom mog dolaska i mojom ocenom */
data class AttendedEventRow(
    @Embedded val event: EventEntity,
    val checkedInAt: Long,
    /** null dok dogadjaj nije ocenjen */
    val myRating: Int?,
)
