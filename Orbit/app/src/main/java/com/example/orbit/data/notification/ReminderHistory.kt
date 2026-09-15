package com.example.orbit.data.notification

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Pamti za koje dogadjaje je podsetnik vec poslat */
@Singleton
class ReminderHistory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("orbit_reminders", Context.MODE_PRIVATE)

    fun wasNotified(eventId: String): Boolean =
        prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty().contains(eventId)

    fun markNotified(eventId: String) {
        val current = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
        // Novi set, izmena vracenog seta se ne cuva pouzdano
        prefs.edit { putStringSet(KEY_NOTIFIED, current + eventId) }
    }

    /** Brise nebitne id-jeve da set ne raste */
    fun retainOnly(eventIds: Set<String>) {
        val current = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
        prefs.edit { putStringSet(KEY_NOTIFIED, current intersect eventIds) }
    }

    /** Novi nalog dobija podsetnike od pocetka */
    fun clear() {
        prefs.edit { remove(KEY_NOTIFIED) }
    }

    private companion object {
        const val KEY_NOTIFIED = "notified_event_ids"
    }
}
