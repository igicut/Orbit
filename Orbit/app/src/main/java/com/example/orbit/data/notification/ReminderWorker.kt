package com.example.orbit.data.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * F-25 - the scheduled reminder check.
 *
 * Why a Worker and not the foreground service: since Android 12 an app in the
 * background may not start a foreground service at all, so a scheduled job that
 * tried to launch [EventReminderService] would simply be refused. A Worker has
 * no such restriction, and posting a notification does not require a foreground
 * service in the first place - the service exists to satisfy the course
 * requirement and to give the manual "check now" button something visible to do.
 *
 * Both paths call the same [ReminderChecker], so the rule about which events are
 * due is defined once.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val checker: ReminderChecker,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // check() catches its own failures and reports the outcome, so there is
        // nothing here worth retrying - a failed run is not fixed by repeating
        // it sooner, and the next scheduled pass finds the same events anyway.
        checker.check()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "orbit_event_reminders"

        /**
         * Repeat interval.
         *
         * WorkManager's floor is 15 minutes, but the reminder window is a whole
         * day, so checking four times an hour would burn battery to discover the
         * same answer. Hourly is frequent enough that a reminder still arrives
         * with useful notice.
         *
         * Android batches this with other work and may run it late; that is
         * acceptable for a reminder measured in hours, and fighting it would
         * cost far more power than it is worth.
         */
        private const val INTERVAL_HOURS = 1L

        /**
         * KEEP, not REPLACE: called on every launch, and REPLACE would restart
         * the period each time - so an app opened often would never actually
         * reach the end of an interval and the check would never run.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
