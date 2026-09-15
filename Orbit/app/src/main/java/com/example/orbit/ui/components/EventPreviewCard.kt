package com.example.orbit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import com.example.orbit.domain.model.Visibility
import com.example.orbit.ui.common.labelRes
import com.example.orbit.ui.util.formatEventDateTime

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
        modifier = modifier.fillMaxWidth(),
        // Senka da se kartica odvoji od mape
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {

            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    // Dug naslov ne sme da izgura dugme za zatvaranje
                    maxLines = 2,
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

            Column(
                modifier = Modifier.padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = formatEventDateTime(event.startTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Udaljenost, ako znamo lokaciju
                distanceFrom?.let { origin ->
                    Text(
                        text = formatDistance(
                            Geo.distanceKm(
                                origin.latitude, origin.longitude,
                                event.latitude, event.longitude,
                            )
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = onViewDetails,
                        label = { Text(stringResource(event.category.labelRes())) },
                    )
                    if (event.visibility == Visibility.PRIVATE) {
                        AssistChip(
                            onClick = onViewDetails,
                            label = { Text(stringResource(event.visibility.labelRes())) },
                        )
                    }
                    // Samo ako ga je neko ocenio
                    if (event.ratingCount > 0) {
                        AssistChip(
                            onClick = onViewDetails,
                            label = {
                                Text(
                                    stringResource(
                                        R.string.map_preview_rating,
                                        String.format("%.1f", event.avgRating),
                                    )
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(),
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onViewDetails,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.map_preview_details))
                }
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
        stringResource(R.string.map_preview_distance_km, String.format("%.1f", km))
    }
