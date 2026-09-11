package com.example.orbit.data.notification

import android.util.Log
import com.example.orbit.data.repository.EventRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

private const val TAG = "EventReminder"

/**
 * How far ahead an event counts as "starting soon".
 *
 * This was two hours, which is why the check appeared to do nothing: an event
 * has to be saved AND fall inside the window, and almost nothing sits in a
 * two-hour slot at the moment you happen to press the button. A day is the
 * span over which a reminder is actually useful - long enough to change your
 * plans, short enough that it is still news.
 */
const val REMINDER_WINDOW_HOURS = 24
private const val REMINDER_WINDOW_MS = REMINDER_WINDOW_HOURS * 60L * 60 * 1000

/**
 * F-25 / F-26 - decides which saved events deserve a reminder and posts them.
 *
 * Deliberately not a Service. The rule "which events are due" has nothing to do
 * with Android's process lifecycle, and keeping it separate means the foreground
 * service is reduced to a wrapper - and that any future scheduled trigger can
 * call exactly the same code rather than reimplementing the rule.
 */
@Singleton
class ReminderChecker @Inject constructor(
    private val repository: EventRepository,
    private val notifier: EventNotifier,
    private val history: ReminderHistory,
    private val results: ReminderResults,
) {

    /**
     * Runs one check and reports what happened.
     *
     * Never throws: a reminder check failing is not worth taking anything else
     * down, and the caller learns about it through [ReminderOutcome.Failed].
     */
    suspend fun check(now: Long = System.currentTimeMillis()): ReminderOutcome {
        val outcome = try {
            runCheck(now)
        } catch (e: Exception) {
            Log.e(TAG, "Reminder check failed: ${e.message}", e)
            ReminderOutcome.Failed
        }

        Log.d(TAG, "Reminder check finished: $outcome")
        results.report(outcome)
        return outcome
    }

    private suspend fun runCheck(now: Long): ReminderOutcome {
        // Channels are created here as well as in the service. Creating one that
        // already exists is a no-op, and a notification posted to a channel that
        // does not exist is silently dropped by Android - which is exactly the
        // kind of invisible failure this whole class exists to remove.
        notifier.createChannels()

        val saved = repository.observeSavedEvents().first()
        if (saved.isEmpty()) return ReminderOutcome.NoSavedEvents

        // Checked before posting rather than per event, so the user is told the
        // permission is missing instead of just seeing nothing happen.
        if (!notifier.hasPermission()) return ReminderOutcome.PermissionMissing

        // Forget events that are no longer saved, so the "already told you" set
        // cannot grow without bound.
        history.retainOnly(saved.map { it.id }.toSet())

        var posted = 0
        var anyDue = false

        saved.forEach { event ->
            val untilStart = event.startTime - now
            if (untilStart !in 0..REMINDER_WINDOW_MS) return@forEach

            anyDue = true
            if (history.wasNotified(event.id)) return@forEach

            val minutes = (untilStart / 60_000.0).roundToLong()
            if (notifier.notifyEventSoon(event.id, event.title, minutes)) {
                history.markNotified(event.id)
                posted++
            }
        }

        return when {
            posted > 0 -> ReminderOutcome.Posted(posted)
            anyDue -> ReminderOutcome.AlreadyNotified
            else -> ReminderOutcome.NothingSoon
        }
    }
}
