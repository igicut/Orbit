package com.example.orbit.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.orbit.R
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.EventFilters
import com.example.orbit.domain.model.Geo
import com.example.orbit.domain.model.UserLocation
import com.example.orbit.ui.common.UiState
import com.example.orbit.ui.components.DEFAULT_MAP_CENTRE
import com.example.orbit.ui.components.DEFAULT_MAP_ZOOM
import com.example.orbit.ui.navigation.orbitBottomBarSpace
import com.example.orbit.ui.components.EventFilterButton
import com.example.orbit.ui.components.EventFilterSheet
import com.example.orbit.ui.components.EventCard
import com.example.orbit.ui.components.ORBIT_MAP_STYLE
import com.example.orbit.ui.components.pinBitmap
import com.example.orbit.ui.components.pointOf
import com.example.orbit.ui.components.rememberLocationPermissionState
import com.example.orbit.ui.components.rememberPinBitmap
import com.example.orbit.ui.stateholders.MapViewModel
import com.example.orbit.ui.theme.PinOrange
import com.example.orbit.ui.theme.PinTerracotta
import com.example.orbit.ui.theme.SoftOffWhite
import com.example.orbit.ui.theme.orbitAccents
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotationGroup
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.AnnotationSourceOptions
import com.mapbox.maps.plugin.annotation.ClusterOptions
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.locationcomponent.location

/** Hodanje ne pomera mapu, veliki skok da */
private const val RECENTRE_DISTANCE_M = 1_000.0

/** Do ovog zuma se pinovi grupisu; iznad njega se razdvajaju pojedinacno */
private const val CLUSTER_MAX_ZOOM = 14L

/** Poluprecnik grupisanja u pikselima; sirina pina je oko 30 dp */
private const val CLUSTER_RADIUS = 60L

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
    val filters by viewModel.filters.collectAsStateWithLifecycle()

    var showFilters by remember { mutableStateOf(false) }

    // Back prvo zatvara karticu
    BackHandler(enabled = selectedEvent != null) { viewModel.dismissPreview() }

    // Mapa bez pozicije nema smisla, pa odmah pita
    val permission = rememberLocationPermissionState(
        onGranted = viewModel::startLocationUpdates,
        askOnFirstAppearance = true,
    )

    // Bez gornje trake: mapa dobija svu visinu, a tab dole vec kaze gde smo
    if (!permission.granted) {
        LocationUnavailable(
            canAskAgain = permission.canAskAgain,
            onAskAgain = permission.request,
            onOpenSettings = permission.openSettings,
        )
    } else {
        EventMap(
            events = (uiState as? UiState.Success)?.data.orEmpty(),
            userLocation = userLocation,
            locationNotice = locationNotice,
            selectedEvent = selectedEvent,
            activeFilters = filters.activeCount,
            onFiltersClick = { showFilters = true },
            onMarkerClick = viewModel::onMarkerSelected,
            onDismissPreview = viewModel::dismissPreview,
            onViewDetails = onEventClick,
        )
    }

    if (showFilters) {
        // Mapa vec pokazuje gde je sta, pa udaljenost i sortiranje nemaju smisla
        EventFilterSheet(
            filters = filters,
            locationKnown = userLocation != null,
            onFiltersChange = viewModel::onFiltersChange,
            onDismiss = { showFilters = false },
            showDistance = false,
            showSort = false,
        )
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
    activeFilters: Int,
    onFiltersClick: () -> Unit,
    onMarkerClick: (String) -> Unit,
    onDismissPreview: () -> Unit,
    onViewDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pin nosi boju kategorije, isto kao blok na kartici; izabrani se izdvaja narandzastim
    val accents = MaterialTheme.orbitAccents
    val context = LocalContext.current
    val pinByCategory = remember(accents) {
        EventCategory.entries.associateWith { context.pinBitmap(accents.forCategory(it)) }
    }
    val selectedPin = rememberPinBitmap(PinOrange)

    val viewportState = rememberMapViewportState {
        setCameraOptions {
            center(DEFAULT_MAP_CENTRE)
            zoom(DEFAULT_MAP_ZOOM)
        }
    }

    // Jedan izvor sa svim tackama; grupisanje radi Mapbox, pa se gusti pinovi ne sliju
    val annotations = remember(events, selectedEvent?.id, pinByCategory, selectedPin) {
        events.mapNotNull { event ->
            val icon =
                if (event.id == selectedEvent?.id) selectedPin else pinByCategory[event.category]
            icon?.let {
                PointAnnotationOptions()
                    .withPoint(pointOf(event.latitude, event.longitude))
                    .withIconImage(it)
                    // Vrh pina pokazuje tacku, isto kao ranije
                    .withIconAnchor(IconAnchor.BOTTOM)
            }
        }
    }

    // Klik vraca anotaciju, ne dogadjaj; tacka je napravljena od istih brojeva, pa je kljuc pouzdan
    val idByPoint = remember(events) {
        events.associate { pointOf(it.latitude, it.longitude) to it.id }
    }

    val clusterConfig = remember {
        AnnotationConfig(
            annotationSourceOptions = AnnotationSourceOptions(
                clusterOptions = ClusterOptions(
                    clusterRadius = CLUSTER_RADIUS,
                    clusterMaxZoom = CLUSTER_MAX_ZOOM,
                    circleRadius = 18.0,
                    textColor = SoftOffWhite.toArgb(),
                    textSize = 13.0,
                    // Krug grupe raste sa brojem dogadjaja: terakota, pa narandzasta
                    colorLevels = listOf(
                        20 to PinOrange.toArgb(),
                        0 to PinTerracotta.toArgb(),
                    ),
                ),
            ),
        )
    }

    // Centriramo samo kad ima novog razloga, ne na svaki redraw
    var centredOn by remember { mutableStateOf<String?>(null) }
    var centredOnUserAt by remember { mutableStateOf<UserLocation?>(null) }

    LaunchedEffect(userLocation, events.firstOrNull()?.id) {
        if (userLocation != null) {
            val last = centredOnUserAt
            val jumped = last == null || Geo.distanceKm(
                last.latitude, last.longitude, userLocation.latitude, userLocation.longitude,
            ) * 1000 > RECENTRE_DISTANCE_M
            if (jumped) {
                viewportState.setCameraOptions {
                    center(pointOf(userLocation.latitude, userLocation.longitude))
                }
                centredOnUserAt = userLocation
            }
        } else if (centredOnUserAt == null) {
            events.firstOrNull()?.takeIf { it.id != centredOn }?.let { first ->
                viewportState.setCameraOptions {
                    center(pointOf(first.latitude, first.longitude))
                }
                centredOn = first.id
            }
        }
    }

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

            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = viewportState,
                // false: pin ispod klika i dalje dobija svoj klik
                onMapClickListener = OnMapClickListener {
                    onDismissPreview()
                    false
                },
                style = { MapStyle(style = ORBIT_MAP_STYLE) }
            ) {
                MapEffect(Unit) { mapView ->
                    // Plava tacka je Mapbox-ov ugradjeni puck
                    mapView.location.updateSettings {
                        enabled = true
                        puckBearingEnabled = true
                    }
                }

                PointAnnotationGroup(
                    annotations = annotations,
                    annotationConfig = clusterConfig,
                    onClick = { annotation ->
                        idByPoint[annotation.point]?.let(onMarkerClick)
                        true
                    },
                )
            }

            // F-29: filteri preko mape, u krugu da se vide i na svetloj podlozi.
            // Dok je kartica otvorena dugme se skriva, inace viri ispod nje
            if (selectedEvent == null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // Mapa ide ispod plutajuce trake, pa se dugme dize iznad nje
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + orbitBottomBarSpace),
                ) {
                    EventFilterButton(activeCount = activeFilters, onClick = onFiltersClick)
                }
            }

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
                    // Dalje od Mapbox natpisa dole levo i iznad plutajuce trake
                    .padding(start = 12.dp, end = 12.dp, bottom = 28.dp + orbitBottomBarSpace),
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
            // Ista kartica kao u listi; dodir otvara detalj, ✕ zatvara pregled
            EventCard(
                event = event,
                onClick = { onViewDetails(event.id) },
                distanceFrom = userLocation,
                onDismiss = onDismiss,
            )
        }
    }
}
