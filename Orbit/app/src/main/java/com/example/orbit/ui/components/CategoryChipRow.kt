package com.example.orbit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.ui.common.iconRes
import com.example.orbit.ui.common.labelRes
import com.example.orbit.ui.theme.orbitAccents

/** F-10: izbor kategorije; izabran cip nosi boju svoje kategorije, ne podrazumevanu Material plavu */
@Composable
fun CategoryChipRow(
    selected: EventCategory,
    onSelect: (EventCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Skroluj do izabranog cipa ako nije vidljiv
    LaunchedEffect(selected) {
        val layout = listState.layoutInfo
        val chip = layout.visibleItemsInfo.firstOrNull { it.index == selected.ordinal }
        val fullyVisible = chip != null &&
            chip.offset >= layout.viewportStartOffset &&
            chip.offset + chip.size <= layout.viewportEndOffset
        if (!fullyVisible) listState.animateScrollToItem(selected.ordinal)
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(EventCategory.entries) { category ->
            val accent = MaterialTheme.orbitAccents.forCategory(category)
            val isSelected = category == selected

            FilterChip(
                selected = isSelected,
                onClick = { onSelect(category) },
                label = { Text(stringResource(category.labelRes())) },
                // Ikonica nosi znacenje i kad boja nije dovoljna
                leadingIcon = {
                    Icon(
                        painter = painterResource(category.iconRes()),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = FILL_ALPHA),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        accent.copy(alpha = BORDER_ALPHA)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            )
        }
    }
}
