package com.example.orbit.ui.stateholders

import androidx.annotation.StringRes
import com.example.orbit.R

/** The two lists on the Events screen. */
enum class EventsTab(@param:StringRes val labelRes: Int) {
    ALL(R.string.events_tab_all),
    SAVED(R.string.events_tab_saved),
}
