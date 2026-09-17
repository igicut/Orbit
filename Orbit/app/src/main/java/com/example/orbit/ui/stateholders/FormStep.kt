package com.example.orbit.ui.stateholders

import androidx.annotation.StringRes
import com.example.orbit.R

/** Tri koraka forme za pravljenje i izmenu dogadjaja, redom */
enum class FormStep(@param:StringRes val labelRes: Int) {
    BASICS(R.string.create_step_basics),
    WHEN_WHERE(R.string.create_step_when_where),
    /** Poslednji korak nosi dugme za cuvanje */
    DETAILS(R.string.create_step_photos_details),
}
