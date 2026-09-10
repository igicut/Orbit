package com.example.orbit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.R
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.UserLocation
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.rememberLocationPermissionState
import com.example.orbit.ui.components.rememberMapView
import com.example.orbit.ui.stateholders.MapViewModel
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

/**
 * F-17 / F-18 - events as pins on OpenStreetMap, plus where the user is.
 *
 * Permission handling lives in the UI rather than the ViewModel because asking
 * needs an Activity; the handshake itself is shared with the events screen in
 * rememberLocationPermissionState. The flow is:
 *
 *   already granted  -> fetch a position straight away
 *   not yet asked    -> ask once, on first open
 *   refused          -> replace the map entirely with an explanation
 *
 * The map is replaced rather than merely annotated, because a map that cannot
 * show you where you are is not much use for finding events near you, and a
 * small warning over a working map invites people to ignore it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onEventClick: (String) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val locationNotice by viewModel.locationNotice.collectAsStateWithLifecycle()

    // The map is useless without a position, so it asks unprompted on first open.
    val permission = rememberLocationPermissionState(
        onGranted = viewModel::refreshLocation,
        askOnFirstAppearance = true,
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.map_title)) }) },
    ) { innerPadding ->

        if (!permission.granted) {
            LocationUnavailable(
                canAskAgain = permission.canAskAgain,
                onAskAgain = permission.request,
                onOpenSettings = permission.openSettings,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            EventMap(
                events = (uiState as? UiState.Success)?.data.orEmpty(),
                userLocation = userLocation,
                locationNotice = locationNotice,
                onEventClick = onEventClick,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    // TODO(F-18): show a preview card on marker tap instead of opening detail.
}

/**
 * Shown instead of the map when location permission was refused.
 */
@Composable
private fun LocationUnavailable(
    canAskAgain: Boolean,
    onAskAgain: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.location_unavailable),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.location_permission_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = if (canAskAgain) onAskAgain else onOpenSettings) {
                Text(
                    stringResource(
                        if (canAskAgain) R.string.location_grant
                        else R.string.location_open_settings
                    )
                )
            }
        }
    }
}

@Composable
private fun EventMap(
    events: List<Event>,
    userLocation: UserLocation?,
    locationNotice: Int?,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = rememberMapView()
    val yourLocationLabel = stringResource(R.string.map_your_location)

    // Licence attribution. OpenStreetMap's tile usage policy requires the credit
    // to be visible and not hidden behind other UI.
    val copyrightOverlay = remember {
        CopyrightOverlay(context).apply {
            setAlignBottom(true)
            setAlignRight(true)
        }
    }

    // A dot rather than the default teardrop pin, so it cannot be mistaken for
    // an event. Loaded once instead of on every redraw.
    val userIcon = remember {
        ContextCompat.getDrawable(context, R.drawable.ic_user_location)
    }

    Column(modifier = modifier.fillMaxSize()) {

        // Permission was granted but no position came back - location services
        // off, or no fix yet. The map still works, so this is a notice.
        locationNotice?.let { noticeRes ->
            Text(
                text = stringResource(noticeRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                // clear() removes every overlay, attribution included, so the
                // copyright notice goes back on before the markers each time.
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

                userLocation?.let { location ->
                    val marker = Marker(map)
                    marker.position = GeoPoint(location.latitude, location.longitude)
                    marker.title = yourLocationLabel
                    marker.icon = userIcon
                    // Centre-anchored: a dot marks the point it sits on, whereas
                    // a pin's tip does, which is why events use ANCHOR_BOTTOM.
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    map.overlays.add(marker)
                }

                // Prefer the user's own position; fall back to the first event so
                // the map never opens in the middle of the ocean.
                val centre = userLocation?.let { GeoPoint(it.latitude, it.longitude) }
                    ?: events.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
                centre?.let { map.controller.setCenter(it) }

                map.invalidate()
            },
            // Releases the tile cache. Without it a MapView leaks on every visit,
            // which on a tab you switch to repeatedly adds up quickly.
            onRelease = { map -> map.onDetach() },
        )
    }
}

// ---------------------------------------------------------------- helpers
