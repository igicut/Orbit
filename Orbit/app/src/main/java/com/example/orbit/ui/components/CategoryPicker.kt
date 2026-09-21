package com.example.orbit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.ui.common.iconRes
import com.example.orbit.ui.common.labelRes
import com.example.orbit.ui.theme.orbitAccents

/**
 * F-10: izbor kategorije. Svih osam staje odjednom u vise redova, bez skrola u stranu.
 * Izabrana je puna boja kategorije, ista pilula kao na kartici dogadjaja.
 */
@Composable
fun CategoryPicker(
    selected: EventCategory,
    onSelect: (EventCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EventCategory.entries.forEach { category ->
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
                        tint = if (isSelected) MaterialTheme.orbitAccents.onCategory else accent,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent,
                    selectedLabelColor = MaterialTheme.orbitAccents.onCategory,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}
