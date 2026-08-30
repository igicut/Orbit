package com.example.orbit.ui.navigation

object OrbitDestinations {
    const val EVENT_LIST = "event_list"
    const val CREATE_EVENT = "create_event"
    const val MAP = "map"

    const val EVENT_ID_ARG = "eventId"
    const val EVENT_DETAIL = "event_detail/{eventId}"

    fun eventDetail(eventId: String) = "event_detail/" + eventId
}
