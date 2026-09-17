package com.example.orbit.ui.navigation

/** Sve rute na jednom mestu */
object OrbitDestinations {

    // Rute donje navigacije, redom
    const val SEARCH = "search"
    const val MAP = "map"
    const val ACCOUNT = "account"

    // Rute preko celog ekrana, bez donje navigacije
    const val CREATE_EVENT = "create_event"
    const val EDIT_EVENT = "edit_event/{eventId}"

    // Liste sa naloga; svaka ima svoj ekran jer moze da naraste
    const val MY_EVENTS = "my_events"
    const val JOINED_EVENTS = "joined_events"
    const val BLOCKED_USERS = "blocked_users"

    fun editEvent(eventId: String) = "edit_event/" + eventId

    const val EVENT_ID_ARG = "eventId"
    const val EVENT_DETAIL = "event_detail/{eventId}"

    fun eventDetail(eventId: String) = "event_detail/" + eventId

    /** Samo na ovim rutama se vidi donja navigacija */
    val bottomBarRoutes = setOf(SEARCH, MAP, ACCOUNT)
}
