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

    /** F-39: otkazivanje ima svoj set, da ne ponisti podsetnik i obrnuto */
    fun wasCancelNotified(eventId: String): Boolean =
        prefs.getStringSet(KEY_CANCELLED, emptySet()).orEmpty().contains(eventId)

    fun markCancelNotified(eventId: String) {
        val current = prefs.getStringSet(KEY_CANCELLED, emptySet()).orEmpty()
        prefs.edit { putStringSet(KEY_CANCELLED, current + eventId) }
    }

    /** Brise nebitne id-jeve da setovi ne rastu */
    fun retainOnly(eventIds: Set<String>) {
        val notified = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
        val cancelled = prefs.getStringSet(KEY_CANCELLED, emptySet()).orEmpty()
        prefs.edit {
            putStringSet(KEY_NOTIFIED, notified intersect eventIds)
            putStringSet(KEY_CANCELLED, cancelled intersect eventIds)
        }
    }

    /** Novi nalog dobija podsetnike od pocetka */
    fun clear() {
        prefs.edit {
            remove(KEY_NOTIFIED)
            remove(KEY_CANCELLED)
        }
    }

    private companion object {
        const val KEY_NOTIFIED = "notified_event_ids"
        const val KEY_CANCELLED = "cancelled_event_ids"
    }
}
