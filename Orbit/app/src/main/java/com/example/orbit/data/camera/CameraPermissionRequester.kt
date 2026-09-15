package com.example.orbit.data.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

/** slajd 8, bez requestPermissions jer puca */
object CameraPermissionRequester {

    fun hasPermissions(context: Context): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        )
    }
}
