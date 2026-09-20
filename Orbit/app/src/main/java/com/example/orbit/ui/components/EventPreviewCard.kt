package com.example.orbit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.orbit.R
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.Geo
import com.example.orbit.domain.model.UserLocation
import com.example.orbit.ui.theme.warmShadow
import com.example.orbit.ui.util.formatEventDateTime
import java.util.Locale

/** F-18: kartica za kliknut marker na mapi */
@Composable
fun EventPreviewCard(
    event: Event,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    distanceFrom: UserLocation? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            // Topla senka umesto crne; kartica mora da se odvoji od mape
            .warmShadow(elevation = 8.dp, shape = MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // Senku crta modifikator, da bi bila u boji palete
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    // Jedan red: kartica mora da ostane niska, mapa je vaznija
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.map_preview_close),
                    )
                }
            }

            // Vreme i udaljenost u istom redu, umesto dva reda jedan ispod drugog
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatEventDateTime(event.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                distanceFrom?.let { origin ->
                    Text(
                        text = formatDistance(
                            Geo.distanceKm(
                                origin.latitude, origin.longitude,
                                event.latitude, event.longitude,
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }

            // Mesta i cena, isti bedzevi kao na kartici u listi
            EventMetaBadges(event = event, modifier = Modifier.padding(top = 2.dp))

            // Ista pilula kao Register i Navigate, ne tonalno dugme
            Button(
                onClick = onViewDetails,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.map_preview_details))
            }
        }
    }
}

/** Metri ispod kilometra, preko toga km sa decimalom */
@Composable
private fun formatDistance(km: Double): String =
    if (km < 1.0) {
        stringResource(R.string.map_preview_distance_m, (km * 1000).toInt())
    } else {
        stringResource(
            R.string.map_preview_distance_km,
            String.format(Locale.getDefault(), "%.1f", km),
        )
    }
