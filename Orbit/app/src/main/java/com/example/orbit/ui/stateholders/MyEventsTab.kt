package com.example.orbit.ui.stateholders

import androidx.annotation.StringRes
import com.example.orbit.R

/** Dve liste na ekranu Moji dogadjaji, podeljene po vremenu zavrsetka */
enum class MyEventsTab(@param:StringRes val labelRes: Int) {
    /** Jos nije pocelo ili jos traje */
    ONGOING(R.string.my_events_tab_ongoing),
    COMPLETED(R.string.my_events_tab_completed),
}
