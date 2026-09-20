package com.example.orbit.ui.stateholders

import androidx.annotation.StringRes
import com.example.orbit.R

/** Dve liste na ekranu Plans: ono sto tek dolazi i ono sto je vec poseceno */
enum class PlansTab(@param:StringRes val labelRes: Int) {
    UPCOMING(R.string.plans_tab_upcoming),
    /** F-36: poseceni dogadjaji */
    ATTENDED(R.string.plans_tab_attended),
}
