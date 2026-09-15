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

/** Hodanje ne pomera mapu, veliki skok da */
private const val RECENTRE_DISTANCE_M = 1_000.0

/** F-17/F-18: dogadjaji na mapi i pozicija korisnika */
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

    // Back prvo zatvara karticu
    BackHandler(enabled = selectedEvent != null) { viewModel.dismissPreview() }

    // Mapa bez pozicije nema smisla, pa odmah pita
    val permission = rememberLocationPermissionState(
        onGranted = viewModel::startLocationUpdates,
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

/** Umesto mape kad je dozvola odbijena */
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

    // OSM uslovi traze vidljiv copyright
    val copyrightOverlay = remember {
        CopyrightOverlay(context).apply {
            setAlignBottom(true)
            setAlignRight(true)
        }
    }

    // Tacka umesto pina da se ne pomesa sa dogadjajem
    val userIcon = remember {
        ContextCompat.getDrawable(context, R.drawable.ic_user_location)
    }

    // Klik na praznu mapu zatvara karticu; overlay pravimo jednom
    val dismissHandler = rememberUpdatedState(onDismissPreview)
    val mapEventsOverlay = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                dismissHandler.value()
                // false: marker ispod klika i dalje dobija klik
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
    }

    // Centriramo samo kad ima novog razloga, ne na svaki redraw
    var centredOn by remember { mutableStateOf<String?>(null) }
    var centredOnUserAt by remember { mutableStateOf<GeoPoint?>(null) }

    // Cuvamo poslednji dogadjaj za izlaznu animaciju
    var lastShownEvent by remember { mutableStateOf<Event?>(null) }
    if (selectedEvent != null) lastShownEvent = selectedEvent

    Column(modifier = modifier.fillMaxSize()) {

        // Dozvola postoji, ali nema pozicije; samo obavestenje
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
                // clear() brise i copyright, pa ga vracamo
                map.overlays.clear()
                // Prvi u listi, ostali overlay-i imaju prednost na klik
                map.overlays.add(mapEventsOverlay)
                map.overlays.add(copyrightOverlay)

                events.forEach { event ->
                    val marker = Marker(map)
                    marker.position = GeoPoint(event.latitude, event.longitude)
                    marker.title = event.title
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.setOnMarkerClickListener { _, _ ->
                        onMarkerClick(event.id)
                        // Obradjeno, bez podrazumevanog balona
                        true
                    }
                    map.overlays.add(marker)
                }

                userLocation?.let { location ->
                    val marker = Marker(map)
                    marker.position = GeoPoint(location.latitude, location.longitude)
                    marker.title = yourLocationLabel
                    marker.icon = userIcon
                    // Tacka se centrira, pin dogadjaja stoji na vrhu
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    map.overlays.add(marker)
                }

                // Centriraj na korisnika ili prvi dogadjaj; ponovo tek posle skoka
                val here = userLocation?.let { GeoPoint(it.latitude, it.longitude) }
                if (here != null) {
                    val last = centredOnUserAt
                    if (last == null || last.distanceToAsDouble(here) > RECENTRE_DISTANCE_M) {
                        map.controller.setCenter(here)
                        centredOnUserAt = here
                    }
                } else if (centredOnUserAt == null) {
                    events.firstOrNull()?.takeIf { it.id != centredOn }?.let { first ->
                        map.controller.setCenter(GeoPoint(first.latitude, first.longitude))
                        centredOn = first.id
                    }
                }

                map.invalidate()
            },
            // onDetach oslobadja kes plocica, inace curi memorija
            onRelease = { map -> map.onDetach() },
        )

        // F-18: kartica preko mape
        EventPreviewOverlay(
            selectedEvent = selectedEvent,
            // Ostaje tokom izlazne animacije
            lastShownEvent = lastShownEvent,
            userLocation = userLocation,
            onViewDetails = onViewDetails,
            onDismiss = onDismissPreview,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Dalje od OSM copyright-a dole desno
                .padding(start = 12.dp, end = 12.dp, bottom = 28.dp),
        )
        }
    }
}

/** Posebna funkcija da se koristi pravi AnimatedVisibility overload */
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

// ---- pomocne funkcije ----
