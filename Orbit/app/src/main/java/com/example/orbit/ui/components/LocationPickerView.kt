package com.example.orbit.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.orbit.R
import com.example.orbit.data.remote.AddressHit
import com.example.orbit.data.remote.searchAddresses
import com.example.orbit.domain.model.UserLocation
import com.example.orbit.ui.theme.WarmCharcoal
import com.example.orbit.ui.theme.warmShadow
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import kotlinx.coroutines.launch

private val PIN_SIZE = 48.dp

/** Senka ispod vrha pina; nagovestava da pin lebdi nad mapom */
private val PIN_SHADOW_WIDTH = 16.dp
private val PIN_SHADOW_HEIGHT = 6.dp

/** Trake nad mapom moraju da se odvoje od podloge, ali toplom senkom, ne crnom */
private val BAR_ELEVATION = 6.dp

/** Sirina para dugmadi za zum; bez nje `HorizontalDivider` razvuce panel preko ekrana */
private val ZOOM_STACK_WIDTH = 48.dp

/** Koliko jedan dodir dugmeta menja zum */
private const val ZOOM_STEP = 1.0

/** Zum na koji se mapa namesti posle izabrane adrese; ulica se jasno vidi */
private const val ADDRESS_ZOOM = 16.0

/** F-17: izbor lokacije dogadjaja na mapi */
@Composable
fun LocationPickerView(
    initialLatitude: Double?,
    initialLongitude: Double?,
    deviceLocation: suspend () -> UserLocation?,
    onConfirm: (latitude: Double, longitude: Double) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var touched by remember { mutableStateOf(false) }

    val hasInitial = initialLatitude != null && initialLongitude != null
    val viewportState = rememberMapViewportState {
        setCameraOptions {
            center(
                if (hasInitial) pointOf(initialLatitude, initialLongitude)
                else DEFAULT_MAP_CENTRE
            )
            zoom(DEFAULT_MAP_ZOOM)
        }
    }

    // Back zatvara samo mapu, ne celu formu
    BackHandler(onBack = onCancel)

    rememberLocationPermissionState(
        onGranted = {
            if (!hasInitial) scope.launch {
                val fix = deviceLocation() ?: return@launch
                // Ne pomeraj mapu ako ju je korisnik vec pomerio
                if (!touched) {
                    viewportState.setCameraOptions { center(pointOf(fix.latitude, fix.longitude)) }
                }
            }
        },
        askOnFirstAppearance = !hasInitial,
    )

    // F-17: pretraga adresa; rezultat pomera mapu, potvrda i dalje ide preko centra
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<AddressHit>()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    val accessToken = stringResource(R.string.mapbox_access_token)
    val keyboard = LocalSoftwareKeyboardController.current

    val runSearch = {
        keyboard?.hide()
        scope.launch {
            searching = true
            val centre = viewportState.cameraState?.center
            results = searchAddresses(
                query = query,
                accessToken = accessToken,
                nearLatitude = centre?.latitude(),
                nearLongitude = centre?.longitude(),
            )
            searched = true
            searching = false
        }
        Unit
    }

    // Edge to edge, drzi mapu dalje od sistemskih traka
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = viewportState,
        ) {
            MapEffect(Unit) { mapView ->
                mapView.mapboxMap.loadStyle(ORBIT_MAP_STYLE)
                // Prvi pomeraj rukom iskljucuje naknadno centriranje na poziciju uredjaja
                mapView.gestures.addOnMoveListener(object : OnMoveListener {
                    override fun onMoveBegin(detector: MoveGestureDetector) {
                        touched = true
                    }

                    override fun onMove(detector: MoveGestureDetector): Boolean = false

                    override fun onMoveEnd(detector: MoveGestureDetector) = Unit
                })
            }
        }

        // Elipsa tacno na centru mape, tamo gde pin pokazuje
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(PIN_SHADOW_WIDTH)
                .height(PIN_SHADOW_HEIGHT)
                .background(WarmCharcoal.copy(alpha = 0.22f), CircleShape),
        )

        // Pomereno za pola visine da vrh oznacava centar
        Icon(
            imageVector = Icons.Filled.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Center)
                .size(PIN_SIZE)
                .offset(y = -PIN_SIZE / 2),
        )

        AddressSearchBar(
            query = query,
            results = results,
            searching = searching,
            showEmptyNote = searched && !searching && results.isEmpty() && query.isNotBlank(),
            onQueryChange = {
                query = it
                searched = false
                results = emptyList()
            },
            onSearch = runSearch,
            onHitClick = { hit ->
                // Mapa je pomerena pretragom, pa pozicija uredjaja vise ne sme da je vrati
                touched = true
                query = hit.name
                results = emptyList()
                searched = false
                keyboard?.hide()
                viewportState.setCameraOptions {
                    center(pointOf(hit.latitude, hit.longitude))
                    zoom(ADDRESS_ZOOM)
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
        )

        ZoomControls(
            onZoomIn = {
                viewportState.setCameraOptions {
                    zoom((viewportState.cameraState?.zoom ?: DEFAULT_MAP_ZOOM) + ZOOM_STEP)
                }
            },
            onZoomOut = {
                viewportState.setCameraOptions {
                    zoom((viewportState.cameraState?.zoom ?: DEFAULT_MAP_ZOOM) - ZOOM_STEP)
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.warmShadow(elevation = BAR_ELEVATION, shape = MaterialTheme.shapes.medium),
            ) {
                Text(
                    text = stringResource(R.string.location_picker_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .warmShadow(elevation = BAR_ELEVATION, shape = CircleShape),
                ) { Text(stringResource(R.string.common_cancel)) }

                Button(
                    onClick = {
                        val centre = viewportState.cameraState?.center ?: return@Button
                        onConfirm(centre.latitude(), centre.longitude())
                    },
                    modifier = Modifier
                        .weight(1f)
                        .warmShadow(elevation = BAR_ELEVATION, shape = CircleShape),
                ) { Text(stringResource(R.string.location_picker_confirm)) }
            }
        }
    }
}

/** Polje za adresu i lista pogodaka, oboje preko mape */
@Composable
private fun AddressSearchBar(
    query: String,
    results: List<AddressHit>,
    searching: Boolean,
    showEmptyNote: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onHitClick: (AddressHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.warmShadow(elevation = BAR_ELEVATION, shape = MaterialTheme.shapes.medium),
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.location_search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    when {
                        searching -> CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )

                        query.isNotEmpty() -> IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.search_clear),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (results.isNotEmpty() || showEmptyNote) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.warmShadow(elevation = BAR_ELEVATION, shape = MaterialTheme.shapes.medium),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (showEmptyNote) {
                        Text(
                            text = stringResource(R.string.location_search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }

                    results.forEachIndexed { index, hit ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = hit.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onHitClick(hit) }
                                .padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Par dugmadi za zum uz desnu ivicu; mapa se cesto podesava jednom rukom */
@Composable
private fun ZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.warmShadow(elevation = BAR_ELEVATION, shape = MaterialTheme.shapes.medium),
    ) {
        Column(modifier = Modifier.width(ZOOM_STACK_WIDTH)) {
            IconButton(onClick = onZoomIn) {
                Icon(
                    painter = painterResource(R.drawable.ic_zoom_in),
                    contentDescription = stringResource(R.string.location_picker_zoom_in),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            IconButton(onClick = onZoomOut) {
                Icon(
                    painter = painterResource(R.drawable.ic_zoom_out),
                    contentDescription = stringResource(R.string.location_picker_zoom_out),
                )
            }
        }
    }
}
