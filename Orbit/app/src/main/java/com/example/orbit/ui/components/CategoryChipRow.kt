package com.example.orbit.ui.components

import com.example.orbit.ui.common.labelRes

import androidx.compose.ui.res.stringResource

import com.example.orbit.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.orbit.domain.model.EventCategory


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
            FilterChip(
                selected = category == selected,
                onClick = { onSelect(category) },
                label = { Text(stringResource(category.labelRes())) },
            )
        }
    }
}
