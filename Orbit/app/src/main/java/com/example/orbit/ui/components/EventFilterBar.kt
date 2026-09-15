package com.example.orbit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.orbit.R
import com.example.orbit.domain.model.DateWindow
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.EventFilters
import com.example.orbit.domain.model.EventSort
import com.example.orbit.domain.model.SearchRadius
import com.example.orbit.ui.common.labelRes

/** F-29: filteri iznad liste; bez lokacije opcije udaljenosti su iskljucene */
@Composable
fun EventFilterBar(
    filters: EventFilters,
    locationKnown: Boolean,
    onFiltersChange: (EventFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {

        // ---- red sa sazetkom ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
        ) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(R.string.filters_title))
                if (filters.activeCount > 0) {
                    Badge(modifier = Modifier.padding(start = 6.dp)) {
                        Text(filters.activeCount.toString())
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.filters_collapse else R.string.filters_expand
                    ),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            // Sta je aktivno, bez otvaranja panela
            Text(
                text = stringResource(filters.radius.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )

            if (filters.activeCount > 0) {
                // Brisanje filtera cuva tekst pretrage
                TextButton(onClick = { onFiltersChange(EventFilters(query = filters.query)) }) {
                    Text(stringResource(R.string.filters_clear))
                }
            }
        }

        // ---- panel ----
        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {

                FilterGroup(
                    titleRes = R.string.filters_distance,
                    // Radijus se meri od uredjaja, treba lokacija
                    enabled = locationKnown,
                    disabledNoteRes = R.string.filters_needs_location,
                ) {
                    SearchRadius.entries.forEach { radius ->
                        FilterChip(
                            // ANYWHERE radi i bez lokacije
                            enabled = locationKnown || radius == SearchRadius.ANYWHERE,
                            selected = filters.radius == radius,
                            onClick = { onFiltersChange(filters.copy(radius = radius)) },
                            label = { Text(stringResource(radius.labelRes())) },
                        )
                    }
                }

                FilterGroup(titleRes = R.string.filters_category) {
                    FilterChip(
                        selected = filters.category == null,
                        onClick = { onFiltersChange(filters.copy(category = null)) },
                        label = { Text(stringResource(R.string.filters_all_categories)) },
                    )
                    EventCategory.entries.forEach { category ->
                        FilterChip(
                            selected = filters.category == category,
                            onClick = {
                                // Klik na izabrani cip ga ponistava
                                onFiltersChange(
                                    filters.copy(
                                        category = if (filters.category == category) null
                                        else category
                                    )
                                )
                            },
                            label = { Text(stringResource(category.labelRes())) },
                        )
                    }
                }

                FilterGroup(titleRes = R.string.filters_when) {
                    DateWindow.entries.forEach { window ->
                        FilterChip(
                            selected = filters.dateWindow == window,
                            onClick = { onFiltersChange(filters.copy(dateWindow = window)) },
                            label = { Text(stringResource(window.labelRes())) },
                        )
                    }
                }

                FilterGroup(titleRes = R.string.filters_sort) {
                    EventSort.entries.forEach { sort ->
                        FilterChip(
                            // NEAREST trazi lokaciju
                            enabled = locationKnown || sort != EventSort.NEAREST,
                            selected = filters.sort == sort,
                            onClick = { onFiltersChange(filters.copy(sort = sort)) },
                            label = { Text(stringResource(sort.labelRes())) },
                        )
                    }
                }
            }
        }

        HorizontalDivider()
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
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
