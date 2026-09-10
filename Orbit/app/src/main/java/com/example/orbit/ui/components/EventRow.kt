package com.example.orbit.ui.components

import androidx.compose.ui.Alignment

import androidx.compose.material3.IconButton

import androidx.compose.material3.Icon

import androidx.compose.material.icons.filled.Delete

import androidx.compose.material.icons.Icons

import androidx.annotation.StringRes

import com.example.orbit.ui.common.labelRes

import androidx.compose.ui.res.stringResource

import com.example.orbit.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.Visibility
import com.example.orbit.ui.util.formatEventDateTime

@Composable
fun EventRow(
    event: Event,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Optional trailing action. Null for ordinary lists; the saved list passes
     * a remove handler so an event can be un-saved without opening it.
     */
    /** Organiser's name, when this device knows it. Hidden when null. */
    organiserName: String? = null,
    onRemove: (() -> Unit)? = null,
    @StringRes removeContentDescription: Int = R.string.saved_remove,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatEventDateTime(event.startTime),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (organiserName != null) {
                Text(
                    text = stringResource(R.string.event_organised_by, organiserName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (event.address != null && event.address.isNotBlank()) {
                Text(
                    text = event.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onClick, label = { Text(stringResource(event.category.labelRes())) })
                if (event.visibility == Visibility.PRIVATE) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text(stringResource(event.visibility.labelRes())) },
                    )
                }
            }
        }

        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(removeContentDescription),
                )
            }
        }
        }
    }
}
