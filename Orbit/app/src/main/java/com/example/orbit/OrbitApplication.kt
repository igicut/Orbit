package com.example.orbit

import com.example.orbit.data.notification.EventNotifier

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.orbit.data.notification.ReminderWorker
import com.example.orbit.data.repository.EventRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * F-13 - publishes this device's profile on launch.
 *
 * Done here rather than from a screen because it is an app-level concern: no
 * particular screen owns it, and it should happen whether the user opens the
 * event list or is taken straight to a detail screen by a notification later.
 *
 * The call is fire-and-forget on an application-scoped coroutine. It must not
 * delay startup, and failing is not worth reporting - registerCurrentUser()
 * returns false, the flag stays unset, and the next launch tries again.
 */
@HiltAndroidApp
class OrbitApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var repository: EventRepository

    @Inject
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var notifier: EventNotifier

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * F-25 - lets WorkManager build a Worker that has constructor dependencies.
     * Without this, ReminderWorker could not be given the ReminderChecker and
     * WorkManager would fail to instantiate it at all.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Channels must exist before the first notification is posted, and
        // creating one that already exists does nothing.
        notifier.createChannels()

        // F-25 - the periodic reminder check. Enqueued on every launch, but
        // KEEP means an existing schedule is left running rather than restarted.
        ReminderWorker.schedule(this)

        applicationScope.launch { repository.registerCurrentUser() }
    }
}
