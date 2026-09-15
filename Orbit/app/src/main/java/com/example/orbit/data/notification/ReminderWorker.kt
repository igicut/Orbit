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

/** F-25: zakazana provera podsetnika preko WorkManager-a */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val checker: ReminderChecker,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // check() sam hvata greske, nema potrebe za retry
        checker.check()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "orbit_event_reminders"

        /** Svakih sat vremena, prozor je ceo dan */
        private const val INTERVAL_HOURS = 1L

        /** KEEP da pokretanje aplikacije ne restartuje period */
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
