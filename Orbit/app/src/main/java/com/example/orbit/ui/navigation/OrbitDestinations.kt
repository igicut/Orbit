package com.example.orbit.ui.navigation

/** Route names in one place so no screen hardcodes a string. */
object OrbitDestinations {

    // Bottom-bar destinations, in the order they appear.
    const val SEARCH = "search"
    const val MAP = "map"
    const val ACCOUNT = "account"

    // Full-screen destinations - the bottom bar hides on these.
    const val CREATE_EVENT = "create_event"
    const val EDIT_EVENT = "edit_event/{eventId}"

    fun editEvent(eventId: String) = "edit_event/" + eventId

    const val EVENT_ID_ARG = "eventId"
    const val EVENT_DETAIL = "event_detail/{eventId}"

    fun eventDetail(eventId: String) = "event_detail/" + eventId

    /** Membership of this set is what decides whether the bottom bar shows. */
    val bottomBarRoutes = setOf(SEARCH, MAP, ACCOUNT)
}
