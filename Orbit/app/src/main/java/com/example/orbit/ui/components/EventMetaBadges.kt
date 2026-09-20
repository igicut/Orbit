package com.example.orbit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.dp
import com.example.orbit.R
import com.example.orbit.domain.model.Event
import com.example.orbit.ui.theme.orbitAccents
import java.util.Locale

/** Ispuna bedza je ista tinta kao kod cipa kategorije */
private const val BADGE_ALPHA = 0.14f

/**
 * Mesta i cena, isti par na kartici liste i na kartici mape.
 * Popunjen dogadjaj nosi terakotu, ostalo ostaje neutralno.
 */
@Composable
fun EventMetaBadges(event: Event, modifier: Modifier = Modifier) {
    val capacity = event.capacity
    val isFull = capacity != null && event.registeredCount >= capacity
    val spotsColor = if (isFull) {
        MaterialTheme.orbitAccents.noSpots
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetaBadge(
            text = if (capacity == null) {
                event.registeredCount.toString()
            } else {
                stringResource(R.string.card_spots, event.registeredCount, capacity)
            },
            color = spotsColor,
            icon = Icons.Filled.Person,
        )

        MetaBadge(
            text = event.price?.let { stringResource(R.string.card_price, formatPrice(it)) }
                ?: stringResource(R.string.detail_price_free),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetaBadge(text: String, color: Color, icon: ImageVector? = null) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = BADGE_ALPHA),
        contentColor = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.let {
                Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(12.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
