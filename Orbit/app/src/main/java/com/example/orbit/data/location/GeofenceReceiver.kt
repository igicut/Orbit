package com.example.orbit.data.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.orbit.data.notification.EventNotifier
import com.example.orbit.data.repository.CheckInResult
import com.example.orbit.data.repository.EventRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "GeofenceReceiver"

/**
 * F-42: sistem javlja da je korisnik usao u krug oko dogadjaja, pa aplikacija
 * salje istu potvrdu dolaska kao kad se dugme pritisne rucno. Server ne zna razliku.
 */
@AndroidEntryPoint
class GeofenceReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: EventRepository

    @Inject lateinit var locationProvider: LocationProvider

    @Inject lateinit var notifier: EventNotifier

    @Inject lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Geofence error " + event.errorCode)
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val eventIds = event.triggeringGeofences.orEmpty().map { it.requestId }
        if (eventIds.isEmpty()) return

        // Prijemnik zivi kratko; goAsync drzi proces dok potvrda ne ode na server
        val pending = goAsync()
        applicationScope.launch {
            try {
                eventIds.forEach { checkIn(it) }
            } finally {
                pending.finish()
            }
        }
    }

    /** Ista provera kao rucna potvrda: server meri vreme i udaljenost */
    private suspend fun checkIn(eventId: String) {
        val event = repository.getEvent(eventId) ?: return
        val location = locationProvider.preciseLocation() ?: return
        if (location.isMock) return

        when (repository.checkIn(eventId, location)) {
            CheckInResult.Success -> notifier.notifyCheckedIn(eventId, event.title)
            // Ostali ishodi (van prozora, popunjeno, bez mreze) se ne javljaju,
            // jer korisnik nije ni trazio potvrdu; rucno dugme i dalje radi
            else -> Log.i(TAG, "Automatic check-in refused for " + eventId)
        }
    }
}
