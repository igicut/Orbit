package com.example.orbit.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.rememberMapView
import com.example.orbit.ui.stateholders.EventDetailViewModel
import com.example.orbit.ui.stateholders.EventListViewModel
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onEventClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: EventListViewModel = hiltViewModel<EventListViewModel>(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapView = rememberMapView()
    val context = LocalContext.current

    // licence
    val copyrightOverlay = remember {
        CopyrightOverlay(context).apply {
            setAlignBottom(true)
            setAlignRight(true)
        }
    }

    val events = (uiState as? UiState.Success)?.data.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->

        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            update = { map ->
                map.overlays.clear()
                map.overlays.add(copyrightOverlay)

                events.forEach { event ->
                    val marker = Marker(map)
                    marker.position = GeoPoint(event.latitude, event.longitude)
                    marker.title = event.title
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.setOnMarkerClickListener { _, _ ->
                        onEventClick(event.id)
                        true
                    }
                    map.overlays.add(marker)
                }

                events.firstOrNull()?.let { first ->
                    map.controller.setCenter(GeoPoint(first.latitude, first.longitude))
                }

                map.invalidate()
            },
            onRelease = { map ->
                map.onDetach()
            }
        )
    }

}
