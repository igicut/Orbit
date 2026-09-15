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

/** F-25/F-26: provera podsetnika kao foreground servis (slajdovi 28-30) */
@AndroidEntryPoint
class EventReminderService : Service() {

    @Inject lateinit var checker: ReminderChecker
    @Inject lateinit var notifier: EventNotifier

    /** Scope vezan za servis, gasi se u onDestroy */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Kanali prvo, inace se obavestenje tiho odbaci
        notifier.createChannels()

        // startForeground mora u roku od pet sekundi
        startForeground(SERVICE_NOTIFICATION_ID, notifier.buildServiceNotification())

        scope.launch {
            // check() sam javlja ishod i ne baca izuzetak
            checker.check()

            // Posao je konacan, pa se servis gasi
            stopSelf(startId)
        }

        // Nema smisla nastavljati prekinutu proveru
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Samo iz foregrounda; ContextCompat zbog minSdk 24 */
        fun start(context: Context) {
            val intent = Intent(context, EventReminderService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // Android 12+ odbija start iz pozadine, samo logujemo
                Log.w(TAG, "Could not start reminder service: ${e.message}")
            }
        }
    }
}
