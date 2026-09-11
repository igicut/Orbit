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

/**
 * F-18 - the card shown when a map marker is tapped.
 *
 * Deliberately not a summary of the event. It answers only the questions someone
 * looking at a pin is actually asking - what is this, when is it, how far away -
 * and then gets out of the way. Everything else is one tap behind "View details",
 * which is what the full screen is for.
 *
 * Non-modal on purpose: it floats over the map rather than covering it, so the
 * pin stays visible, the map stays pannable, and tapping a different marker
 * swaps the card instead of forcing a dismiss first. A modal sheet would dim the
 * very thing the preview exists to keep you looking at.
 *
 * @param distanceFrom where the user is, or null when unknown. The distance line
 *   is dropped entirely rather than shown as a guess.
 */
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
        // Lifted well clear of the map: without a shadow the card reads as part
        // of the tiles behind it.
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
                    // A long title must not push the close button off the card.
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

                // The one fact a map can offer that a list cannot.
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
                    // Only once somebody has actually rated it - "0.0 (0)" says
                    // nothing useful and reads as a bad score.
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

/**
 * Metres below a kilometre, one decimal above it.
 *
 * "0.1 km" is a worse answer than "80 m" at walking range, and "1234.5 m" is a
 * worse answer than "1.2 km" beyond it.
 */
@Composable
private fun formatDistance(km: Double): String =
    if (km < 1.0) {
        stringResource(R.string.map_preview_distance_m, (km * 1000).toInt())
    } else {
        stringResource(R.string.map_preview_distance_km, String.format("%.1f", km))
    }
