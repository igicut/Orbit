package com.example.orbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.orbit.R
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.EventStatus
import com.example.orbit.domain.model.Geo
import com.example.orbit.domain.model.UserLocation
import com.example.orbit.ui.theme.orbitAccents
import java.util.Locale

/**
 * Bedz je skoro bela pilula preko obojene kartice, pa tekst ostaje citljiv
 * na svakoj boji kategorije (najmanje 5.5:1).
 */
private const val BADGE_ALPHA = 0.85f

/**
 * Stanje, mesta, cena i udaljenost na kartici dogadjaja.
 * Boja nosi znacenje: zelena je dobra vest, terakota je prepreka, ostalo je neutralno.
 */
@Composable
fun EventMetaBadges(
    event: Event,
    modifier: Modifier = Modifier,
    distanceFrom: UserLocation? = null,
) {
    val now = System.currentTimeMillis()
    val capacity = event.capacity
    val isFull = capacity != null && event.registeredCount >= capacity
    val isCancelled = event.status == EventStatus.CANCELLED
    val isLive = !isCancelled && now >= event.startTime && !AttendanceRules.hasEnded(event, now)
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val positive = MaterialTheme.orbitAccents.registered
    val negative = MaterialTheme.orbitAccents.noSpots

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // F-39: otkazan dogadjaj ostaje u listi, oznaka ide prva da se vidi odmah
        if (isCancelled) {
            MetaBadge(text = stringResource(R.string.event_cancelled), color = negative)
        }

        // Dogadjaj koji traje jos prima upad bez prijave, zato se istice
        if (isLive) {
            MetaBadge(text = stringResource(R.string.card_live), color = positive, showDot = true)
        }

        MetaBadge(
            // Isti oblik "zauzeto/ukupno" i bez ogranicenja; samo "3" su ispitanici citali kao preostala mesta
            text = if (capacity == null) {
                stringResource(R.string.card_spots_unlimited, event.registeredCount)
            } else {
                stringResource(R.string.card_spots, event.registeredCount, capacity)
            },
            color = if (isFull) negative else neutral,
            icon = Icons.Filled.Person,
        )

        val price = event.price
        MetaBadge(
            text = if (price == null || price == 0.0) {
                stringResource(R.string.detail_price_free)
            } else {
                stringResource(R.string.card_price, formatPrice(price))
            },
            color = if (price == null || price == 0.0) positive else neutral,
        )

        distanceFrom?.let { origin ->
            MetaBadge(
                text = formatDistance(
                    Geo.distanceKm(origin.latitude, origin.longitude, event.latitude, event.longitude)
                ),
                color = neutral,
                icon = Icons.Filled.Place,
            )
        }
    }
}

/** Istorija: dolazak i ocena umesto mesta i cene */
@Composable
fun NoteBadge(note: String, modifier: Modifier = Modifier) {
    MetaBadge(
        text = note,
        color = MaterialTheme.orbitAccents.registered,
        icon = Icons.Filled.CheckCircle,
        modifier = modifier,
    )
}

@Composable
private fun MetaBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    showDot: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = BADGE_ALPHA),
        contentColor = color,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = color, shape = CircleShape),
                )
            }
            icon?.let {
                Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Cela cena bez decimala; `800.0` je izgledalo kao greska */
private fun formatPrice(price: Double): String =
    if (price % 1.0 == 0.0) {
        String.format(Locale.getDefault(), "%.0f", price)
    } else {
        String.format(Locale.getDefault(), "%.2f", price)
    }

/** Metri ispod kilometra, preko toga km sa decimalom */
@Composable
private fun formatDistance(km: Double): String =
    if (km < 1.0) {
        stringResource(R.string.card_distance_m, (km * 1000).toInt())
    } else {
        stringResource(R.string.card_distance_km, String.format(Locale.getDefault(), "%.1f", km))
    }
