package com.example.orbit

import com.example.orbit.data.notification.EventNotifier

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.example.orbit.data.notification.ReminderWorker
import com.example.orbit.data.repository.EventRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

/** F-13: pri pokretanju salje ime promenjeno bez mreze */
@HiltAndroidApp
class OrbitApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var repository: EventRepository

    @Inject
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var notifier: EventNotifier

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var okHttpClient: OkHttpClient

    /**
     * F-37: slike su iza tokena, pa Coil mora da koristi nas klijent.
     * Podrazumevani loader nema ni mrezni fetcher ni Authorization zaglavlje.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient })) }
            // Fotografija na kartici se pojavljuje postepeno umesto da iskoci
            .crossfade(true)
            .build()

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

        applicationScope.launch { repository.publishDisplayName() }
    }
}
