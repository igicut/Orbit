package com.example.orbit.data.local

import androidx.room.TypeConverter
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.Visibility


class Converters {


    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString("\n")

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("\n")


    @TypeConverter
    fun fromCategory(value: EventCategory): String = value.name

    @TypeConverter
    fun toCategory(value: String): EventCategory =
        runCatching { EventCategory.valueOf(value) }.getOrDefault(EventCategory.OTHER)

    @TypeConverter
    fun fromVisibility(value: Visibility): String = value.name

    @TypeConverter
    fun toVisibility(value: String): Visibility =
        runCatching { Visibility.valueOf(value) }.getOrDefault(Visibility.PUBLIC)
}
