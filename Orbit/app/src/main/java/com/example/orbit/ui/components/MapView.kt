package com.example.orbit.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView


@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current

    return remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = "Orbit/1.0 (+https://github.com/igicut/Orbit)"
        }
        MapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(13.0)
        }
    }
}
