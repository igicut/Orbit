package com.example.orbit.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.example.orbit.domain.model.UserLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * F-17 - reads the device location through Google Play Services.
 *
 * Fused location is used rather than the raw LocationManager because it merges
 * GPS, Wi-Fi and cell signals and returns whichever is available fastest, which
 * matters a great deal indoors.
 *
 * Permission is NOT requested here. Asking requires an Activity, so that belongs
 * to the UI; this class only checks whether permission was granted and refuses
 * to call the API otherwise.
 */
@Singleton
class LocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Either permission is enough.
     *
     * Since Android 12 the user can grant approximate location only, which comes
     * back as COARSE granted and FINE denied. A city-block-accurate marker is
     * still perfectly useful here, so treating that as failure would reject a
     * choice the user deliberately made.
     */
    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    /**
     * One position, or null if it cannot be determined.
     *
     * getCurrentLocation rather than lastLocation: lastLocation returns whatever
     * some other app happened to request recently and is null on a fresh device
     * or emulator, which is exactly the case being tested. getCurrentLocation
     * actively asks for a fix.
     *
     * Null covers three real situations that need no distinction here: permission
     * missing, location services switched off, and no fix obtainable.
     */
    @SuppressLint("MissingPermission") // guarded by hasPermission() immediately below
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

            // If the screen goes away mid-request, stop asking for a fix.
            continuation.invokeOnCancellation { cancellation.cancel() }
        }
    }
}
