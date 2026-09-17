package com.example.orbit.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.ui.common.labelRes
import com.example.orbit.ui.theme.orbitAccents

/** Ispuna i ivica nose boju kategorije; obojen tekst bi pao na 2.6-4.1:1 */
internal const val FILL_ALPHA = 0.14f
internal const val BORDER_ALPHA = 0.40f

/** Cip kategorije, isti na kartici, na mapi i na detalju */
@Composable
fun CategoryChip(
    category: EventCategory,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val accent = MaterialTheme.orbitAccents.forCategory(category)

    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = { Text(stringResource(category.labelRes())) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = accent.copy(alpha = FILL_ALPHA),
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = BORDER_ALPHA)),
    )
}
