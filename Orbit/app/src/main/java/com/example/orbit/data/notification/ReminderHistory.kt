package com.example.orbit.data.notification

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which events have already been announced.
 *
 * SharedPreferences rather than a Room column: this is a handful of ids that
 * only matter until the event has passed, and adding a column would mean a
 * schema migration for something that is not really part of the data model.
 */
@Singleton
class ReminderHistory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("orbit_reminders", Context.MODE_PRIVATE)

    fun wasNotified(eventId: String): Boolean =
        prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty().contains(eventId)

    fun markNotified(eventId: String) {
        val current = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
        // A new set, not a mutation: SharedPreferences does not reliably persist
        // an edit made to the set instance it handed back.
        prefs.edit { putStringSet(KEY_NOTIFIED, current + eventId) }
    }

    /** Drops ids that are no longer relevant, so the set cannot grow forever. */
    fun retainOnly(eventIds: Set<String>) {
        val current = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
        prefs.edit { putStringSet(KEY_NOTIFIED, current intersect eventIds) }
    }

    private companion object {
        const val KEY_NOTIFIED = "notified_event_ids"
    }
}
