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

/** Traze se obe, dovoljna je bilo koja */
private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** F-16: zajednicki tok dozvole za lokaciju */
class LocationPermissionState(
    val granted: Boolean,
    /** false posle trajnog odbijanja, ostaju samo podesavanja */
    val canAskAgain: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

/** onGranted se zove kad dozvola postoji; mapa pita odmah */
@Composable
fun rememberLocationPermissionState(
    onGranted: () -> Unit = {},
    askOnFirstAppearance: Boolean = true,
): LocationPermissionState {
    val context = LocalContext.current

    var granted by remember { mutableStateOf(context.hasLocationPermission()) }

    // rememberSaveable da rotacija ne otvori dijalog ponovo
    var alreadyAsked by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        // Dovoljna je i priblizna lokacija (COARSE)
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
        // Rationale ima smisla tek posle prvog odbijanja
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

/** Odmotava Context dok ne nadje Activity */
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
