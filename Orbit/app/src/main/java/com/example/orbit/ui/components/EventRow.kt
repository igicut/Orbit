package com.example.orbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.orbit.R
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.Visibility
import com.example.orbit.ui.common.iconRes
import com.example.orbit.ui.common.labelRes
import com.example.orbit.ui.theme.orbitAccents
import com.example.orbit.ui.theme.warmShadow
import com.example.orbit.ui.util.formatEventDateTime

/** Blok kategorije levo; sirina je fiksna da se naslovi poravnaju kroz listu */
private val CATEGORY_BLOCK_WIDTH = 56.dp
private val SMALL_ICON = 14.dp

/**
 * Sve kartice imaju istu visinu, pa lista dobija ritam i skrol je predvidiv.
 * Visina drzi tri reda teksta: naslov, datum i red sa mestima i cenom.
 */
private val CARD_HEIGHT = 104.dp

/**
 * Kompaktan red liste: boja i ikonica kategorije levo, tekst desno.
 * Adresa se namerno ne prikazuje, ima je na detalju.
 */
@Composable
fun EventRow(
    event: Event,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Ime organizatora, skriveno ako je null */
    organiserName: String? = null,
    /** Zamenjuje red sa mestima i cenom, npr. dolazak i ocena u istoriji */
    note: String? = null,
) {
    val accent = MaterialTheme.orbitAccents.forCategory(event.category)

    Card(
        // Podrazumevani Card uzima `surfaceContainerHighest`, a to je pesak iz palete;
        // na kremu se skoro ne razlikuje od pozadine, pa kartica ide na `surface`
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .warmShadow(elevation = 2.dp, shape = MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Row {

            Box(
                modifier = Modifier
                    .width(CATEGORY_BLOCK_WIDTH)
                    .fillMaxHeight()
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(event.category.iconRes()),
                    // Boja sama ne nosi znacenje, citac ekrana dobija naziv kategorije
                    contentDescription = stringResource(event.category.labelRes()),
                    tint = MaterialTheme.orbitAccents.onCategory,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        // Jedan red: duga imena inace guraju karticu u razlicite visine
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (event.visibility == Visibility.PRIVATE) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = stringResource(event.visibility.labelRes()),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(SMALL_ICON),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(SMALL_ICON),
                    )
                    Text(
                        text = formatEventDateTime(event.startTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    organiserName?.let { name ->
                        Text(
                            text = stringResource(R.string.event_organised_by, name),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.orbitAccents.registered,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    EventMetaBadges(event)
                }
            }
        }
    }
}
