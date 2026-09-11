package com.example.orbit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.orbit.ui.components.EventPreviewCard
import com.example.orbit.ui.components.rememberLocationPermissionState
import com.example.orbit.ui.components.rememberMapView
import com.example.orbit.ui.stateholders.MapViewModel
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
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
 *
 * F-18 - tapping a marker opens a preview card over the map rather than jumping
 * straight to the detail screen. Navigating away from a map to read three lines
 * about a pin loses the very context the pin was giving you, and getting back
 * costs a press of Back.
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
    val selectedEvent by viewModel.selectedEvent.collectAsStateWithLifecycle()

    // Back closes the preview before it leaves the screen, which is what the
    // gesture means while something is open on top of the map.
    BackHandler(enabled = selectedEvent != null) { viewModel.dismissPreview() }

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
                selectedEvent = selectedEvent,
                onMarkerClick = viewModel::onMarkerSelected,
                onDismissPreview = viewModel::dismissPreview,
                onViewDetails = onEventClick,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

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
    selectedEvent: Event?,
    onMarkerClick: (String) -> Unit,
    onDismissPreview: () -> Unit,
    onViewDetails: (String) -> Unit,
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

    // Tapping bare map closes the preview - the same gesture that dismisses a
    // popup anywhere else. osmdroid reports it through a receiver overlay
    // rather than a plain click listener.
    //
    // The handler is held in a ref so the overlay itself can be created once:
    // rebuilding it on every recomposition would make it the newest overlay and
    // change which one wins a tap.
    val dismissHandler = rememberUpdatedState(onDismissPreview)
    val mapEventsOverlay = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                dismissHandler.value()
                // false: not consumed, so a marker under the tap still wins.
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
    }

    // The map is recentred only when there is a new reason to, not on every
    // redraw. Recentring unconditionally meant any state change - opening this
    // very card - yanked the map back and undid the user's panning.
    var centredOn by remember { mutableStateOf<String?>(null) }

    // AnimatedVisibility keeps drawing its content while sliding out, but
    // selectedEvent is already null by then - so the last one is retained.
    var lastShownEvent by remember { mutableStateOf<Event?>(null) }
    if (selectedEvent != null) lastShownEvent = selectedEvent

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

        Box(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                // clear() removes every overlay, attribution included, so the
                // copyright notice goes back on before the markers each time.
                map.overlays.clear()
                // First in the list, so every other overlay sits above it and
                // gets first refusal on a tap.
                map.overlays.add(mapEventsOverlay)
                map.overlays.add(copyrightOverlay)

                events.forEach { event ->
                    val marker = Marker(map)
                    marker.position = GeoPoint(event.latitude, event.longitude)
                    marker.title = event.title
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.setOnMarkerClickListener { _, _ ->
                        onMarkerClick(event.id)
                        // Consumed, and the default info bubble is suppressed:
                        // the Compose card replaces it.
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
                //
                // Keyed so this happens once per reason - on the first fix, or on
                // the first batch of events - instead of on every redraw. The old
                // unconditional setCenter fought the user: any recomposition
                // snapped the map back and threw away their panning.
                val reason = userLocation?.let { "user" }
                    ?: events.firstOrNull()?.id
                if (reason != null && reason != centredOn) {
                    val centre = userLocation?.let { GeoPoint(it.latitude, it.longitude) }
                        ?: events.first().let { GeoPoint(it.latitude, it.longitude) }
                    map.controller.setCenter(centre)
                    centredOn = reason
                }

                map.invalidate()
            },
            // Releases the tile cache. Without it a MapView leaks on every visit,
            // which on a tab you switch to repeatedly adds up quickly.
            onRelease = { map -> map.onDetach() },
        )

        // F-18 - the preview, over the map rather than instead of it.
        EventPreviewOverlay(
            selectedEvent = selectedEvent,
            // Kept through the exit animation, so the card does not blank out
            // halfway through sliding away.
            lastShownEvent = lastShownEvent,
            userLocation = userLocation,
            onViewDetails = onViewDetails,
            onDismiss = onDismissPreview,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Clear of the OpenStreetMap attribution in the bottom-right,
                // which the tile usage policy requires to stay visible.
                .padding(start = 12.dp, end = 12.dp, bottom = 28.dp),
        )
        }
    }
}

/**
 * The sliding preview, in its own composable for a reason that is not cosmetic.
 *
 * Written inline it sat inside a Box nested in a Column, so ColumnScope was
 * still an implicit receiver - and Kotlin prefers the ColumnScope overload of
 * AnimatedVisibility over the top-level one, which then fails to resolve
 * because ColumnScope is not the innermost receiver. Lifting it out leaves only
 * the overload that was wanted, and the alignment is applied by the caller,
 * where BoxScope actually is in scope.
 */
@Composable
private fun EventPreviewOverlay(
    selectedEvent: Event?,
    lastShownEvent: Event?,
    userLocation: UserLocation?,
    onViewDetails: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = selectedEvent != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        (selectedEvent ?: lastShownEvent)?.let { event ->
            EventPreviewCard(
                event = event,
                distanceFrom = userLocation,
                onViewDetails = { onViewDetails(event.id) },
                onDismiss = onDismiss,
            )
        }
    }
}

// ---------------------------------------------------------------- helpers
