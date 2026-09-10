package com.example.orbit.data.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.orbit.data.local.CurrentUser
import com.example.orbit.data.repository.EventRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToLong

private const val TAG = "EventReminder"
private const val SERVICE_NOTIFICATION_ID = 1

/** How far ahead an event counts as "soon". */
private const val REMINDER_WINDOW_MS = 2L * 60 * 60 * 1000   // 2 hours

/**
 * F-25 / F-26 - checks saved events and announces the ones starting soon.
 *
 * Structured as the course material's foreground service (slides 28-30):
 * onStartCommand builds a notification, calls startForeground within the
 * required five seconds, then does the actual work on Dispatchers.IO.
 *
 * @AndroidEntryPoint is the one structural addition - the material constructs
 * its dependencies inline, whereas this needs the same repository the rest of
 * the app uses, and Hilt cannot inject a Service without it.
 */
@AndroidEntryPoint
class EventReminderService : Service() {

    @Inject lateinit var repository: EventRepository
    @Inject lateinit var notifier: EventNotifier
    @Inject lateinit var history: ReminderHistory
    @Inject lateinit var currentUser: CurrentUser

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notifier.createChannels()

        // A foreground service must show a notification within five seconds of
        // starting, or the system kills it with a ForegroundServiceDidNotStart
        // exception. So this happens before any work.
        startForeground(SERVICE_NOTIFICATION_ID, notifier.buildServiceNotification())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                notifyUpcomingSavedEvents()
            } catch (e: Exception) {
                Log.e(TAG, "Reminder check failed: ${e.message}", e)
            } finally {
                // Unlike the material's long-running example, this service has a
                // finite job. Stopping when done is what keeps it from sitting in
                // the status bar forever.
                stopSelf(startId)
            }
        }

        // NOT START_STICKY: if the system kills this mid-check there is nothing
        // worth resuming - the next run will find the same events.
        return START_NOT_STICKY
    }

    private suspend fun notifyUpcomingSavedEvents() {
        val saved = repository.observeSavedEvents().first()
        val now = System.currentTimeMillis()

        // Forget events that are no longer saved, so the "already told you" set
        // does not grow without bound.
        history.retainOnly(saved.map { it.id }.toSet())

        saved.forEach { event ->
            val untilStart = event.startTime - now

            val isSoon = untilStart in 0..REMINDER_WINDOW_MS
            if (!isSoon || history.wasNotified(event.id)) return@forEach

            val minutes = (untilStart / 60_000.0).roundToLong()
            if (notifier.notifyEventSoon(event.id, event.title, minutes)) {
                history.markNotified(event.id)
                Log.d(TAG, "Reminded about ${event.title} in $minutes min")
            }
        }
    }

    companion object {
        /**
         * Must be called while the app is in the foreground.
         *
         * Android 12 forbids starting a foreground service from the background,
         * which is also why a WorkManager job cannot simply start this one - see
         * the note in the README of this feature.
         */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, EventReminderService::class.java))
        }
    }
}
