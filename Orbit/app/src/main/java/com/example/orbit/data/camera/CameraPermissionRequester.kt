package com.example.orbit.data.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

/**
 * From the course material (slide 8).
 *
 * hasPermissions is used exactly as presented. The companion requestPermissions
 * from the same slide is NOT reproduced: it calls registerForActivityResult at
 * the moment of asking, and that throws
 *
 *     IllegalStateException: LifecycleOwner is attempting to register while
 *     current state is RESUMED
 *
 * because a launcher has to be registered before the activity starts. In Compose
 * the equivalent is rememberLauncherForActivityResult, which registers during
 * composition - see CreateEventScreen.
 */
object CameraPermissionRequester {

    fun hasPermissions(context: Context): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        )
    }
}
