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

/**
 * F-29 - category, radius, date and sort, above the events list.
 *
 * Collapsed by default and opened by tapping the summary row. Four chip groups
 * open at once would push the list itself off the screen, which defeats the
 * point of filtering it; collapsed, the summary row still reports what is
 * active, so nothing is hidden - only folded.
 *
 * @param locationKnown false when the device location is unavailable. The
 *   distance-based options stay visible but are disabled, with a line saying
 *   why: dropping them silently would look like a bug, and leaving them enabled
 *   would let the user pick a filter that quietly does nothing.
 */
@Composable
fun EventFilterBar(
    filters: EventFilters,
    locationKnown: Boolean,
    onFiltersChange: (EventFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {

        // ---- summary row ---------------------------------------------------
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

            // What is active, without having to open the panel.
            Text(
                text = stringResource(filters.radius.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )

            if (filters.activeCount > 0) {
                // Clearing keeps the typed query: the text field is still on
                // screen, so emptying it from here would look like a glitch.
                TextButton(onClick = { onFiltersChange(EventFilters(query = filters.query)) }) {
                    Text(stringResource(R.string.filters_clear))
                }
            }
        }

        // ---- the panel -----------------------------------------------------
        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {

                FilterGroup(
                    titleRes = R.string.filters_distance,
                    // The radius is measured from the device, so with no fix
                    // there is no centre to measure from.
                    enabled = locationKnown,
                    disabledNoteRes = R.string.filters_needs_location,
                ) {
                    SearchRadius.entries.forEach { radius ->
                        FilterChip(
                            // ANYWHERE means "do not filter by distance", which
                            // is the one option that still works without a fix.
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
                                // Tapping the selected chip clears it, so there
                                // is always a way back without hunting for "All".
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
                            // Nearest needs somewhere to measure from.
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

/**
 * One labelled, horizontally scrolling row of chips.
 *
 * Scrolling rather than wrapping keeps each group on a single line, so the four
 * groups stay a predictable height however many categories exist.
 */
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
