package com.example.orbit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.orbit.R
import com.example.orbit.domain.model.DateWindow
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.EventFilters
import com.example.orbit.domain.model.EventSort
import com.example.orbit.domain.model.PriceLimit
import com.example.orbit.domain.model.SearchRadius
import com.example.orbit.ui.common.iconRes
import com.example.orbit.ui.common.labelRes
import com.example.orbit.ui.theme.orbitAccents

/** Podloga izabranog cipa; tekst ostaje u boji teksta */
private const val SELECTED_FILL_ALPHA = 0.15f

/**
 * F-29: dugme za filtere uz polje pretrage. Broj aktivnih filtera stoji na njemu,
 * pa se stanje vidi i dok je panel zatvoren.
 */
@Composable
fun EventFilterButton(
    activeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(R.drawable.ic_filters),
                contentDescription = stringResource(R.string.filters_expand),
            )
        }
        if (activeCount > 0) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp),
            ) {
                Text(activeCount.toString())
            }
        }
    }
}

/**
 * F-29: filteri u panelu preko liste. Panel ne gura listu, pa se rezultat
 * vidi odmah po zatvaranju, na istom mestu gde je i bio.
 * Izbor se primenjuje odmah; zato nema dugmeta za potvrdu, samo zatvaranje.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventFilterSheet(
    filters: EventFilters,
    locationKnown: Boolean,
    onFiltersChange: (EventFilters) -> Unit,
    onDismiss: () -> Unit,
    /** Mapa vec pokazuje udaljenost i nema listu za sortiranje */
    showDistance: Boolean = true,
    showSort: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Naslov panela je veci od naslova grupa ispod njega
                Text(
                    text = stringResource(R.string.filters_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (filters.activeCount > 0) {
                    // Brisanje filtera cuva tekst pretrage
                    TextButton(onClick = { onFiltersChange(EventFilters(query = filters.query)) }) {
                        Text(stringResource(R.string.filters_clear))
                    }
                }
            }

            if (showDistance) FilterGroup(
                titleRes = R.string.filters_distance,
                // Radijus se meri od uredjaja, treba lokacija
                enabled = locationKnown,
                disabledNoteRes = R.string.filters_needs_location,
            ) {
                SearchRadius.entries.forEach { radius ->
                    OrbitFilterChip(
                        // ANYWHERE radi i bez lokacije
                        enabled = locationKnown || radius == SearchRadius.ANYWHERE,
                        selected = filters.radius == radius,
                        onClick = { onFiltersChange(filters.copy(radius = radius)) },
                        label = { Text(stringResource(radius.labelRes())) },
                    )
                }
            }

            FilterGroup(titleRes = R.string.filters_category) {
                OrbitFilterChip(
                    selected = filters.category == null,
                    onClick = { onFiltersChange(filters.copy(category = null)) },
                    label = { Text(stringResource(R.string.filters_all_categories)) },
                )
                EventCategory.entries.forEach { category ->
                    CategoryFilterChip(
                        category = category,
                        selected = filters.category == category,
                        onClick = {
                            // Klik na izabrani cip ga ponistava
                            onFiltersChange(
                                filters.copy(
                                    category = if (filters.category == category) null else category
                                )
                            )
                        },
                    )
                }
            }

            FilterGroup(titleRes = R.string.filters_when) {
                DateWindow.entries.forEach { window ->
                    OrbitFilterChip(
                        selected = filters.dateWindow == window,
                        onClick = { onFiltersChange(filters.copy(dateWindow = window)) },
                        label = { Text(stringResource(window.labelRes())) },
                    )
                }
            }

            FilterGroup(titleRes = R.string.filters_price) {
                PriceLimit.entries.forEach { limit ->
                    OrbitFilterChip(
                        selected = filters.price == limit,
                        onClick = { onFiltersChange(filters.copy(price = limit)) },
                        label = { Text(stringResource(limit.labelRes())) },
                    )
                }
            }

            if (showSort) FilterGroup(titleRes = R.string.filters_sort) {
                EventSort.entries.forEach { sort ->
                    OrbitFilterChip(
                        // NEAREST trazi lokaciju
                        enabled = locationKnown || sort != EventSort.NEAREST,
                        selected = filters.sort == sort,
                        onClick = { onFiltersChange(filters.copy(sort = sort)) },
                        label = { Text(stringResource(sort.labelRes())) },
                    )
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            ) {
                Text(stringResource(R.string.filters_done))
            }
        }
    }
}

/** Red cipova sa naslovom, skroluje horizontalno */
@Composable
private fun FilterGroup(
    titleRes: Int,
    enabled: Boolean = true,
    disabledNoteRes: Int? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )

        if (!enabled && disabledNoteRes != null) {
            Text(
                text = stringResource(disabledNoteRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            content()
        }
    }
}

/**
 * Pilula kao cipovi u formi: izabrana ima blagu brend zelenu i zelenu ivicu.
 * Material bi sam uzeo `secondaryContainer`, prasnjavu plavu.
 */
@Composable
private fun OrbitFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    val accent = MaterialTheme.orbitAccents.brandStart

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        enabled = enabled,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = SELECTED_FILL_ALPHA),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/** Kategorija sa ikonicom; izabrana je puna boja kategorije, kao pilula na kartici */
@Composable
private fun CategoryFilterChip(
    category: EventCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.orbitAccents.forCategory(category)

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(category.labelRes())) },
        leadingIcon = {
            Icon(
                painter = painterResource(category.iconRes()),
                contentDescription = null,
                tint = if (selected) MaterialTheme.orbitAccents.onCategory else accent,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent,
            selectedLabelColor = MaterialTheme.orbitAccents.onCategory,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}
