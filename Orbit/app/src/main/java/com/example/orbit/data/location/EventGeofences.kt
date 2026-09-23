package com.example.orbit.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.orbit.data.repository.EventRepository
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.Event
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EventGeofences"

/** Koliko unapred se prati dogadjaj; isti prozor kao podsetnici (F-25) */
private const val WINDOW_MS = 24L * 60 * 60 * 1000

/** Android dozvoljava 100 zona po aplikaciji; lista se sece i pre te granice */
private const val MAX_GEOFENCES = 20

/**
 * F-42: zone oko dogadjaja sa prijavom, za automatsku potvrdu dolaska.
 * Poluprecnik je isti kao kod rucne potvrde (`AttendanceRules.CHECK_IN_RADIUS_METERS`),
 * da se dva pravila ne razidju.
 */
@Singleton
class EventGeofences @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: EventRepository,
) {

    private val client = LocationServices.getGeofencingClient(context)

    /**
     * Brise stare zone i upisuje nove. Zove se pri pokretanju aplikacije,
     * jer sistem gubi zone pri restartu telefona i one se same ne vracaju.
     */
    suspend fun refresh() {
        if (!hasPermission()) {
            Log.i(TAG, "No background location permission, geofences skipped")
            return
        }

        val now = System.currentTimeMillis()
        val geofences = repository.observeRegisteredEvents().first()
            .filter { isSoon(it, now) }
            .take(MAX_GEOFENCES)
            .map { geofenceFor(it, now) }

        // Brisanje je asinhrono; upis ceka njegov kraj, inace brisanje pojede nove zone
        client.removeGeofences(pendingIntent).addOnCompleteListener {
            if (geofences.isNotEmpty()) register(geofences)
        }
    }

    /** Dogadjaj koji pocinje u narednih 24 h ili vec traje */
    private fun isSoon(event: Event, now: Long): Boolean =
        event.startTime <= now + WINDOW_MS && !AttendanceRules.hasEnded(event, now)

    private fun geofenceFor(event: Event, now: Long): Geofence =
        Geofence.Builder()
            // Id zone je id dogadjaja, pa prijemnik odmah zna na sta se odnosi
            .setRequestId(event.id)
            .setCircularRegion(
                event.latitude,
                event.longitude,
                AttendanceRules.CHECK_IN_RADIUS_METERS.toFloat(),
            )
            // Zona nestaje kad se dogadjaj zavrsi, pa se ne brise rucno
            .setExpirationDuration(AttendanceRules.endTime(event) - now)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

    @SuppressLint("MissingPermission") // dozvola je proverena u refresh()
    private fun register(geofences: List<Geofence>) {
        val request = GeofencingRequest.Builder()
            // Ko je vec u krugu dobija okidac odmah, bez izlaska i ponovnog ulaska
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()

        client.addGeofences(request, pendingIntent)
            .addOnSuccessListener { Log.i(TAG, "Registered " + geofences.size + " geofences") }
            .addOnFailureListener { error -> Log.w(TAG, "Geofences not registered", error) }
    }

    /** FLAG_MUTABLE je obavezan: sistem u ovaj intent upisuje koja je zona okinula */
    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /**
     * Play servisi od Androida 10 odbijaju upis zone bez dozvole "Uvek dozvoli",
     * i to izuzetkom, pa se bez nje ne pokusava. Ekran Nalog trazi obe dozvole.
     */
    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return fine

        val background = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return fine && background
    }
}
