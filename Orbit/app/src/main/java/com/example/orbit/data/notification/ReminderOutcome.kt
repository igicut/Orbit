package com.example.orbit.data.notification

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-25 / F-26 - what a reminder check actually did.
 *
 * The check used to finish silently. Every possible ending looked identical
 * from the outside - no notification - so "nothing was due", "you never granted
 * permission" and "it crashed" were indistinguishable. Naming the outcomes is
 * what makes the feature diagnosable instead of merely quiet.
 */
sealed interface ReminderOutcome {

    /** Reminders were shown for [count] events. */
    data class Posted(val count: Int) : ReminderOutcome

    /** Saved events exist, but none start inside the reminder window. */
    data object NothingSoon : ReminderOutcome

    /**
     * Something is due, but a reminder for it was already shown and dismissed.
     * Reporting this separately keeps the app from claiming "nothing soon"
     * about an event that starts within the hour.
     */
    data object AlreadyNotified : ReminderOutcome

    /** Nothing is saved, so there is nothing to be reminded about. */
    data object NoSavedEvents : ReminderOutcome

    /** Android 13+ and POST_NOTIFICATIONS was never granted. */
    data object PermissionMissing : ReminderOutcome

    /** The check itself failed - the database or the repository threw. */
    data object Failed : ReminderOutcome
}

/**
 * Carries the outcome from wherever the check ran back to the screen.
 *
 * A SharedFlow rather than a StateFlow: an outcome is an event to announce once,
 * not a state to hold. With replay = 0 a rotation does not re-show a toast for a
 * check that finished before it.
 */
@Singleton
class ReminderResults @Inject constructor() {

    private val _outcomes = MutableSharedFlow<ReminderOutcome>(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val outcomes: SharedFlow<ReminderOutcome> = _outcomes.asSharedFlow()

    /** tryEmit, not emit: reporting must never suspend or block the caller. */
    fun report(outcome: ReminderOutcome) {
        _outcomes.tryEmit(outcome)
    }
}
