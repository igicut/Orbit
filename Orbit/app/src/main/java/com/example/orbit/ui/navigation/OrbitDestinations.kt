package com.example.orbit.ui.navigation

/** Sve rute na jednom mestu */
object OrbitDestinations {

    // Rute donje navigacije, redom
    const val SEARCH = "search"
    const val MAP = "map"
    const val PLANS = "plans"
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

    /** Profil organizatora; ulaz je red "Organizuje" na detalju */
    const val USER_ID_ARG = "userId"
    const val USER_PROFILE = "user_profile/{userId}"

    fun userProfile(userId: String) = "user_profile/" + userId

    /** Samo na ovim rutama se vidi donja navigacija */
    val bottomBarRoutes = setOf(SEARCH, MAP, PLANS, ACCOUNT)

    /**
     * Ekrani sa sopstvenom donjom trakom (dugmad koraka u formi).
     * Samo oni rezervisu mesto na dnu; svuda drugde sadrzaj ide do ivice.
     */
    val ownBottomBarRoutes = setOf(CREATE_EVENT, EDIT_EVENT)
}
