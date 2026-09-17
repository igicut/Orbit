package com.example.orbit.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.orbit.R
import com.example.orbit.domain.model.UserLocation
import com.example.orbit.ui.theme.WarmCharcoal
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.CopyrightOverlay

private val PIN_SIZE = 48.dp

/** Senka ispod vrha pina; nagovestava da pin lebdi nad mapom */
private val PIN_SHADOW_WIDTH = 16.dp
private val PIN_SHADOW_HEIGHT = 6.dp

/** Trake nad mapom moraju da se odvoje od podloge, ali toplom senkom, ne crnom */
private val BAR_ELEVATION = 6.dp

/** Sirina para dugmadi za zum; bez nje `HorizontalDivider` razvuce panel preko ekrana */
private val ZOOM_STACK_WIDTH = 48.dp

/** Pocetni centar kad nema pozicije ni lokacije */
private val DEFAULT_CENTRE = GeoPoint(44.8125, 20.4612)

/** F-17: izbor lokacije dogadjaja na mapi */
@Composable
fun LocationPickerView(
    initial: GeoPoint?,
    deviceLocation: suspend () -> UserLocation?,
    onConfirm: (latitude: Double, longitude: Double) -> Unit,
    onCancel: () -> Unit,
) {
    val mapView = rememberMapView()
    val scope = rememberCoroutineScope()
    var touched by remember { mutableStateOf(false) }

    // Back zatvara samo mapu, ne celu formu
    BackHandler(onBack = onCancel)

    rememberLocationPermissionState(
        onGranted = {
            if (initial == null) scope.launch {
                val fix = deviceLocation() ?: return@launch
                // Ne pomeraj mapu ako ju je korisnik vec pomerio
                if (!touched) mapView.controller.setCenter(GeoPoint(fix.latitude, fix.longitude))
            }
        },
        askOnFirstAppearance = initial == null,
    )

    // Edge to edge, drzi mapu dalje od sistemskih traka
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AndroidView(
            factory = { context ->
                mapView.apply {
                    controller.setCenter(initial ?: DEFAULT_CENTRE)
                    // false: samo belezimo dodir, mapa ga i dalje obradi
                    setOnTouchListener { _, _ ->
                        touched = true
                        false
                    }
                    // OSM uslovi traze vidljiv copyright
                    overlays.add(CopyrightOverlay(context).apply { setAlignRight(true) })
                }
            },
            // clipToBounds, inace osmdroid crta ispod status bara
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            onRelease = { map -> map.onDetach() },
        )

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

        ZoomControls(
            onZoomIn = { mapView.controller.zoomIn() },
            onZoomOut = { mapView.controller.zoomOut() },
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
                modifier = Modifier.shadow(
                    elevation = BAR_ELEVATION,
                    shape = MaterialTheme.shapes.medium,
                    ambientColor = WarmCharcoal,
                    spotColor = WarmCharcoal,
                ),
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
                        .shadow(
                            elevation = BAR_ELEVATION,
                            shape = CircleShape,
                            ambientColor = WarmCharcoal,
                            spotColor = WarmCharcoal,
                        ),
                ) { Text(stringResource(R.string.common_cancel)) }

                Button(
                    onClick = {
                        val centre = mapView.mapCenter
                        onConfirm(centre.latitude, centre.longitude)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .shadow(
                            elevation = BAR_ELEVATION,
                            shape = CircleShape,
                            ambientColor = WarmCharcoal,
                            spotColor = WarmCharcoal,
                        ),
                ) { Text(stringResource(R.string.location_picker_confirm)) }
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
        modifier = modifier.shadow(
            elevation = BAR_ELEVATION,
            shape = MaterialTheme.shapes.medium,
            ambientColor = WarmCharcoal,
            spotColor = WarmCharcoal,
        ),
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
