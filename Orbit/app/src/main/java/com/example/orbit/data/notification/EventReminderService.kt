package com.example.orbit.data.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "EventReminder"
private const val SERVICE_NOTIFICATION_ID = 1

/**
 * F-25 / F-26 - runs a reminder check as a foreground service.
 *
 * Structured as the course material's foreground service (slides 28-30):
 * onStartCommand builds a notification, calls startForeground within the
 * required five seconds, then does the work off the main thread.
 *
 * The decision of which events are due lives in [ReminderChecker], not here.
 * A Service is a way of getting execution time from Android; it is not a good
 * home for a business rule, and separating the two is what lets the rule be
 * exercised without starting a service at all.
 *
 * @AndroidEntryPoint is the one structural addition to the material - it
 * constructs its dependencies inline, whereas this needs the same repository as
 * the rest of the app, and Hilt cannot inject a Service without it.
 */
@AndroidEntryPoint
class EventReminderService : Service() {

    @Inject lateinit var checker: ReminderChecker
    @Inject lateinit var notifier: EventNotifier

    /**
     * Tied to the service rather than created loose in onStartCommand: a bare
     * CoroutineScope(Dispatchers.IO) outlives onDestroy and keeps working after
     * the service is gone.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Both channels first: a notification posted to a channel that does not
        // exist is dropped without a word, and on a fresh install this is the
        // first notification code to run.
        notifier.createChannels()

        // A foreground service must show its notification within five seconds of
        // starting or the system kills it, so this happens before any work.
        startForeground(SERVICE_NOTIFICATION_ID, notifier.buildServiceNotification())

        scope.launch {
            // check() reports its own outcome and never throws.
            checker.check()

            // Unlike the material's long-running example this has a finite job,
            // and stopping when done is what keeps it out of the status bar.
            stopSelf(startId)
        }

        // NOT START_STICKY: if the system kills this mid-check there is nothing
        // worth resuming - the next run finds the same events.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /**
         * Must be called while the app is in the foreground.
         *
         * Android 12 forbids starting a foreground service from the background,
         * which is also why a scheduled job cannot simply start this one.
         *
         * ContextCompat.startForegroundService rather than the Context method:
         * that one arrived in API 26 and minSdk here is 24, so calling it
         * directly would throw NoSuchMethodError on Android 7.
         */
        fun start(context: Context) {
            val intent = Intent(context, EventReminderService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // Android 12+ throws ForegroundServiceStartNotAllowedException (a
                // subclass) if the app is judged to be in the background by the
                // time this lands. Nothing is broken; the check simply did not
                // run, and swallowing it is better than crashing the app.
                Log.w(TAG, "Could not start reminder service: ${e.message}")
            }
        }
    }
}
