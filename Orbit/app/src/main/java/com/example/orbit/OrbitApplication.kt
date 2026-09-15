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

/** F-13: registruje profil uredjaja pri pokretanju */
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

    /** F-25: omogucava Worker sa zavisnostima u konstruktoru */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Kanali moraju postojati pre prvog obavestenja
        notifier.createChannels()

        // F-25: periodicna provera, KEEP ne restartuje raspored
        ReminderWorker.schedule(this)

        applicationScope.launch { repository.registerCurrentUser() }
    }
}
