package com.example.orbit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.orbit.R
import com.example.orbit.domain.model.AttendanceRules
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.EventStatus
import com.example.orbit.ui.theme.orbitAccents
import com.example.orbit.ui.theme.warmShadow
import com.example.orbit.ui.util.formatEventDate
import com.example.orbit.ui.util.formatEventDateTimeShort
import com.example.orbit.ui.util.formatEventTime
import java.util.Locale

/** Krug iza ikonice u boji kategorije; ikonica ostaje puna boja */
private const val ICON_CIRCLE_ALPHA = 0.15f

/**
 * Tab "Pregled": prvo cinjenice (kada, gde, cena, mesta), pa opis, pa organizator.
 * Vlasnik ovde, na dnu, otkazuje dogadjaj; retka je i opasna radnja, pa ne stoji gore.
 */
@Composable
fun EventOverviewTab(
    event: Event,
    accent: Color,
    now: Long,
    isOwner: Boolean,
    organiserName: String?,
    isOrganiserBlocked: Boolean,
    onToggleBlock: () -> Unit,
    onOrganiserClick: () -> Unit,
    onCancelEvent: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        FactsCard(event = event, accent = accent)

        if (event.description.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.detail_about),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = event.description, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // F-28: organizator i blokiranje, ne za svoje dogadjaje
        if (!isOwner) {
            OrganiserCard(
                // Skraceni id ako profil nije preuzet
                name = organiserName ?: event.ownerId.take(8),
                isBlocked = isOrganiserBlocked,
                onOpen = onOrganiserClick,
                onToggleBlock = onToggleBlock,
            )
        }

        // F-39: samo vlasnik, dok dogadjaj traje i dok nije vec otkazan
        if (isOwner && event.status == EventStatus.ACTIVE && !AttendanceRules.hasEnded(event, now)) {
            OutlinedButton(
                onClick = onCancelEvent,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.orbitAccents.noSpots),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.orbitAccents.noSpots,
                ),
            ) {
                Text(stringResource(R.string.detail_cancel))
            }
        }
    }
}

/** Kada, gde, cena, mesta i kod, svaki u svom redu sa ikonicom u boji kategorije */
@Composable
private fun FactsCard(event: Event, accent: Color) {
    val capacity = event.capacity
    val price = event.price

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .warmShadow(elevation = 2.dp, shape = MaterialTheme.shapes.large),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FactRow(
                icon = rememberVectorPainter(Icons.Filled.DateRange),
                label = stringResource(R.string.detail_when),
                value = eventDateRange(event),
                accent = accent,
            )

            event.address?.let { address ->
                FactRow(
                    icon = rememberVectorPainter(Icons.Filled.Place),
                    label = stringResource(R.string.detail_where),
                    value = address,
                    accent = accent,
                )
            }

            FactRow(
                icon = painterResource(R.drawable.ic_price_tag),
                label = stringResource(R.string.detail_price_label),
                // Isti zapis kao na kartici: "Besplatno" ili "700 RSD"
                value = if (price == null || price == 0.0) {
                    stringResource(R.string.detail_price_free)
                } else {
                    stringResource(R.string.card_price, formatPrice(price))
                },
                accent = accent,
            )

            FactRow(
                icon = rememberVectorPainter(Icons.Filled.Person),
                label = stringResource(R.string.detail_spots),
                value = if (capacity == null) {
                    pluralStringResource(R.plurals.registration_count, event.registeredCount, event.registeredCount)
                } else {
                    stringResource(R.string.registration_count_limited, event.registeredCount, capacity)
                },
                accent = accent,
            ) {
                if (capacity != null) CapacityBar(taken = event.registeredCount, capacity = capacity)
            }

            event.accessCode?.let { code ->
                FactRow(
                    icon = rememberVectorPainter(Icons.Filled.Lock),
                    label = stringResource(R.string.detail_access_code_label),
                    value = code,
                    accent = accent,
                )
            }
        }
    }
}

/** Ikonica u krugu, naziv sitno iznad i vrednost krupnije; vrednost je vaznija od naziva */
@Composable
private fun FactRow(
    icon: Painter,
    label: String,
    value: String,
    accent: Color,
    extra: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color = accent.copy(alpha = ICON_CIRCLE_ALPHA), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
            extra?.invoke()
        }
    }
}

/** Traka zauzetih mesta; ide ka terakoti kako se puni */
@Composable
private fun CapacityBar(taken: Int, capacity: Int) {
    val fraction = (taken.toFloat() / capacity).coerceIn(0f, 1f)

    LinearProgressIndicator(
        progress = { fraction },
        color = lerp(MaterialTheme.colorScheme.primary, MaterialTheme.orbitAccents.noSpots, fraction),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(8.dp),
    )
}

/** Avatar sa inicijalom, ime i strelica vode na profil; blokiranje je posebno, ispod crte */
@Composable
private fun OrganiserCard(
    name: String,
    isBlocked: Boolean,
    onOpen: () -> Unit,
    onToggleBlock: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .warmShadow(elevation = 2.dp, shape = MaterialTheme.shapes.large),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = stringResource(R.string.detail_organiser_open),
                        onClick = onOpen,
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Isti avatar kao na profilu organizatora
                UserAvatar(name = name, size = 48.dp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.detail_organised_by),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            TextButton(
                onClick = onToggleBlock,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.orbitAccents.noSpots),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Text(stringResource(if (isBlocked) R.string.detail_unblock else R.string.detail_block))
            }

            if (isBlocked) {
                Text(
                    text = stringResource(R.string.detail_blocked_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }
}

/** Pocetak i kraj u jednom redu; bez trajanja ostaje samo pocetak */
private fun eventDateRange(event: Event): String {
    val start = formatEventDateTimeShort(event.startTime)
    if (event.durationMinutes == null) return start

    val endTime = AttendanceRules.endTime(event)
    val end = if (formatEventDate(endTime) == formatEventDate(event.startTime)) {
        formatEventTime(endTime)
    } else {
        formatEventDateTimeShort(endTime)
    }
    return "$start  →  $end"
}

/** Cela cena bez decimala; 500.0 je izgledalo kao greska */
private fun formatPrice(price: Double): String =
    if (price % 1.0 == 0.0) {
        String.format(Locale.getDefault(), "%.0f", price)
    } else {
        String.format(Locale.getDefault(), "%.2f", price)
    }
