package com.example.orbit.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.orbit.R
import com.example.orbit.domain.model.DateWindow
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.EventSort
import com.example.orbit.domain.model.SearchRadius
import com.example.orbit.domain.model.Visibility

/** Prikazni nazivi za domenske enume */
@StringRes
fun EventCategory.labelRes(): Int = when (this) {
    EventCategory.MUSIC -> R.string.category_music
    EventCategory.SPORT -> R.string.category_sport
    EventCategory.FOOD -> R.string.category_food
    EventCategory.ART -> R.string.category_art
    EventCategory.TECH -> R.string.category_tech
    EventCategory.OUTDOOR -> R.string.category_outdoor
    EventCategory.SOCIAL -> R.string.category_social
    EventCategory.OTHER -> R.string.category_other
}

/** Ikonica uz boju kategorije; boja sama ne sme da bude jedini nosilac znacenja */
@DrawableRes
fun EventCategory.iconRes(): Int = when (this) {
    EventCategory.MUSIC -> R.drawable.ic_category_music
    EventCategory.SPORT -> R.drawable.ic_category_sport
    EventCategory.FOOD -> R.drawable.ic_category_food
    EventCategory.ART -> R.drawable.ic_category_art
    EventCategory.TECH -> R.drawable.ic_category_tech
    EventCategory.OUTDOOR -> R.drawable.ic_category_outdoor
    EventCategory.SOCIAL -> R.drawable.ic_category_social
    EventCategory.OTHER -> R.drawable.ic_category_other
}

@StringRes
fun Visibility.labelRes(): Int = when (this) {
    Visibility.PUBLIC -> R.string.visibility_public
    Visibility.PRIVATE -> R.string.visibility_private
}

// ---- F-29: traka filtera ----

@StringRes
fun SearchRadius.labelRes(): Int = when (this) {
    SearchRadius.WALK -> R.string.radius_walk
    SearchRadius.NEARBY -> R.string.radius_nearby
    SearchRadius.CITY -> R.string.radius_city
    SearchRadius.REGION -> R.string.radius_region
    SearchRadius.ANYWHERE -> R.string.radius_anywhere
}

@StringRes
fun DateWindow.labelRes(): Int = when (this) {
    DateWindow.ANY -> R.string.date_any
    DateWindow.TODAY -> R.string.date_today
    DateWindow.THIS_WEEK -> R.string.date_this_week
    DateWindow.THIS_MONTH -> R.string.date_this_month
}

@StringRes
fun EventSort.labelRes(): Int = when (this) {
    EventSort.SOONEST -> R.string.sort_soonest
    EventSort.NEAREST -> R.string.sort_nearest
    EventSort.TOP_RATED -> R.string.sort_top_rated
}
