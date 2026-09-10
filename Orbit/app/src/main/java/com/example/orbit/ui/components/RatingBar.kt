package com.example.orbit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.orbit.R

/**
 * F-27 - a five-star input.
 *
 * All five stars use the filled icon, distinguished by colour rather than by a
 * filled/outline pair: material-icons-core ships Star but not StarBorder, and
 * pulling in material-icons-extended for one glyph is not worth ~10 MB.
 *
 * Every star is its own button with a spoken label, so the control is usable
 * with TalkBack instead of being five unlabelled shapes.
 */
@Composable
fun RatingBar(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        (1..5).forEach { star ->
            IconButton(
                onClick = { onRatingChange(star) },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(R.string.rating_stars, star),
                    tint = if (star <= rating) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}
