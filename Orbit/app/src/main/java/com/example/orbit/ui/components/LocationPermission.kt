package com.example.orbit.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Both are requested together; either one is enough to place a marker. */
private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * F-16 - the location permission handshake, in one place.
 *
 * Both the map and the events screen need the device location, and asking for it
 * requires an Activity, so it cannot live in a ViewModel. Rather than repeat the
 * launcher, the "have we asked yet" flag and the permanent-refusal check on both
 * screens, they share this and render their own UI around it.
 */
class LocationPermissionState(
    val granted: Boolean,
    /**
     * False once the user has refused permanently ("don't ask again", or a
     * second refusal on newer Android). Asking again would then do nothing at
     * all, and the only route left is the system settings screen.
     */
    val canAskAgain: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * @param onGranted called when permission is held, both on first composition and
 *   immediately after the user grants it - the natural moment to fetch a fix.
 * @param askOnFirstAppearance whether to raise the system dialog unprompted. The
 *   map does (it is useless without a position); the events list does not, since
 *   it works perfectly well showing everything.
 */
@Composable
fun rememberLocationPermissionState(
    onGranted: () -> Unit = {},
    askOnFirstAppearance: Boolean = true,
): LocationPermissionState {
    val context = LocalContext.current

    var granted by remember { mutableStateOf(context.hasLocationPermission()) }

    // Saved across configuration changes, or rotating the device re-triggers the
    // system dialog.
    var alreadyAsked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        // Android 12 and later let the user pick "approximate" instead of
        // "precise", which grants COARSE and denies FINE. That is a deliberate
        // choice and is good enough here, so accept whichever came back.
        granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) onGranted()
    }

    androidx.compose.runtime.LaunchedEffect(granted) {
        when {
            granted -> onGranted()
            askOnFirstAppearance && !alreadyAsked -> {
                alreadyAsked = true
                launcher.launch(LOCATION_PERMISSIONS)
            }
        }
    }

    return LocationPermissionState(
        granted = granted,
        // shouldShowRequestPermissionRationale is false both before the first
        // request and after a permanent refusal, so this is only meaningful once
        // a refusal has actually happened.
        canAskAgain = context.findActivity()?.let { activity ->
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } ?: true,
        request = { launcher.launch(LOCATION_PERMISSIONS) },
        openSettings = { context.openAppSettings() },
    )
}

fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

/**
 * Compose gives a base Context, which may be a wrapper rather than the Activity
 * itself. Unwrap until one turns up.
 */
fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    )
}
