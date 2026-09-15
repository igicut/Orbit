package com.example.orbit.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.example.orbit.domain.model.UserLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** F-17: lokacija uredjaja preko Google Play Services */
@Singleton
class LocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** Dovoljna je i priblizna lokacija (COARSE) */
    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    /** Jedna pozicija, ili null ako nije dostupna */
    @SuppressLint("MissingPermission") // provereno odmah ispod u hasPermission()
    suspend fun currentLocation(): UserLocation? {
        if (!hasPermission()) return null

        return suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationTokenSource()

            client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellation.token,
            )
                .addOnSuccessListener { location ->
                    continuation.resume(
                        location?.let {
                            UserLocation(it.latitude, it.longitude, it.accuracy)
                        }
                    )
                }
                .addOnFailureListener { continuation.resume(null) }

            // Otkazi zahtev ako se ekran zatvori
            continuation.invokeOnCancellation { cancellation.cancel() }
        }
    }

    /** Prati poziciju uzivo, kesiranu salje samo ako je sveza */
    @SuppressLint("MissingPermission") // provereno odmah ispod u hasPermission()
    fun locationUpdates(): Flow<UserLocation> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }

        client.lastLocation.addOnSuccessListener { location ->
            if (location != null && location.isRecent()) trySend(location.toUserLocation())
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toUserLocation()) }
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_M)
            .build()

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { close() }

        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun Location.isRecent(): Boolean =
        SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos <= MAX_CACHED_FIX_AGE_NS

    private fun Location.toUserLocation() = UserLocation(latitude, longitude, accuracy)

    private companion object {
        const val UPDATE_INTERVAL_MS = 5_000L
        const val MIN_UPDATE_DISTANCE_M = 10f
        const val MAX_CACHED_FIX_AGE_NS = 2 * 60 * 1_000_000_000L
    }
}
