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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.orbit.R
import com.example.orbit.domain.model.UserLocation
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.CopyrightOverlay

private val PIN_SIZE = 48.dp

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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
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
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.common_cancel)) }

                Button(
                    onClick = {
                        val centre = mapView.mapCenter
                        onConfirm(centre.latitude, centre.longitude)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.location_picker_confirm)) }
            }
        }
    }
}
