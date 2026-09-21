package com.example.orbit.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.orbit.R
import com.example.orbit.domain.model.DateWindow
import com.example.orbit.domain.model.EventFilters
import com.example.orbit.domain.model.EventSort
import com.example.orbit.domain.model.PriceLimit
import com.example.orbit.ui.common.labelRes

/** Sporedni tekst je ista boja teksta, samo tisa */
private const val SECONDARY_ALPHA = 0.6f

/**
 * F-43: cip za svaki aktivan filter, bilo da ga je korisnik izabrao ili AI; ✕ vraca taj filter
 * na podrazumevanu vrednost. Posle AI pretrage iznad cipova stoji recenica i Ponisti,
 * da se vidi sta je AI razumeo i da se greska ispravi jednim dodirom.
 */
@Composable
fun ActiveFilterChips(
    filters: EventFilters,
    aiSentence: String?,
    onFiltersChange: (EventFilters) -> Unit,
    onUndoAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (filters.activeCount == 0 && aiSentence == null) return

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        if (aiSentence != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.search_ai_understood, aiSentence),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onUndoAi) {
                    Text(stringResource(R.string.search_ai_undo))
                }
            }
        }

        // Vodoravni skrol: pet cipova ne staje u sirinu telefona
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.category?.let { category ->
                RemovableChip(
                    label = stringResource(category.labelRes()),
                    onRemove = { onFiltersChange(filters.copy(category = null)) },
                )
            }
            if (filters.radius != EventFilters.DEFAULT_RADIUS) {
                RemovableChip(
                    label = stringResource(filters.radius.labelRes()),
                    onRemove = { onFiltersChange(filters.copy(radius = EventFilters.DEFAULT_RADIUS)) },
                )
            }
            if (filters.dateWindow != DateWindow.ANY) {
                RemovableChip(
                    label = stringResource(filters.dateWindow.labelRes()),
                    onRemove = { onFiltersChange(filters.copy(dateWindow = DateWindow.ANY)) },
                )
            }
            if (filters.price != PriceLimit.ANY) {
                RemovableChip(
                    label = stringResource(filters.price.labelRes()),
                    onRemove = { onFiltersChange(filters.copy(price = PriceLimit.ANY)) },
                )
            }
            if (filters.sort != EventSort.SOONEST) {
                RemovableChip(
                    label = stringResource(filters.sort.labelRes()),
                    onRemove = { onFiltersChange(filters.copy(sort = EventSort.SOONEST)) },
                )
            }
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.search_filter_remove, label),
                modifier = Modifier.size(16.dp),
            )
        },
    )
}
