package com.example.orbit.ui.stateholders

import androidx.annotation.StringRes
import com.example.orbit.R

/** Tri liste na ekranu dogadjaja */
enum class EventsTab(@param:StringRes val labelRes: Int) {
    ALL(R.string.events_tab_all),
    REGISTERED(R.string.events_tab_registered),
    /** F-36: poseceni dogadjaji */
    HISTORY(R.string.events_tab_history),
}
