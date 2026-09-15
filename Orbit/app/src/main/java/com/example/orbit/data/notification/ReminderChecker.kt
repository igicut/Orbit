package com.example.orbit.data.notification

import android.util.Log
import com.example.orbit.data.repository.EventRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

private const val TAG = "EventReminder"

/** Prozor za podsetnik, prosiren sa 2 na 24 sata */
const val REMINDER_WINDOW_HOURS = 24
private const val REMINDER_WINDOW_MS = REMINDER_WINDOW_HOURS * 60L * 60 * 1000

/** F-25/F-26: bira sacuvane dogadjaje za podsetnik i salje */
@Singleton
class ReminderChecker @Inject constructor(
    private val repository: EventRepository,
    private val notifier: EventNotifier,
    private val history: ReminderHistory,
    private val results: ReminderResults,
) {

    /** Jedna provera; greske vraca kao ReminderOutcome.Failed */
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
        // Kanali i ovde, bez njih obavestenje tiho nestaje
        notifier.createChannels()

        val saved = repository.observeSavedEvents().first()
        if (saved.isEmpty()) return ReminderOutcome.NoSavedEvents

        // Dozvola se proverava jednom, da korisnik dobije poruku
        if (!notifier.hasPermission()) return ReminderOutcome.PermissionMissing

        // Zaboravi dogadjaje koji vise nisu sacuvani
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
