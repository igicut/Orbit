package com.example.orbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.orbit.R

/** Traka je tanka, a dodirna meta oko nje puna visina */
private val TRACK_HEIGHT = 4.dp
private val TAP_TARGET = 44.dp

/** Predjeni koraci su popunjeni, ali tise od tekuceg, da se tekuci i dalje izdvaja */
private const val DONE_ALPHA = 0.40f

/**
 * Napredak kroz korake forme: tanke trake umesto Material stepper-a.
 * `onStepSelected` je null kad se koraci prelaze redom; tada trake nisu dodirljive.
 */
@Composable
fun FormStepIndicator(
    stepLabels: List<String>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
    onStepSelected: ((Int) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stepLabels.forEachIndexed { index, label ->
                val color = when {
                    index == currentIndex -> MaterialTheme.colorScheme.primary
                    index < currentIndex -> MaterialTheme.colorScheme.primary.copy(alpha = DONE_ALPHA)
                    else -> MaterialTheme.colorScheme.outlineVariant
                }

                val jumpLabel = stringResource(R.string.create_step_go_to, index + 1, label)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(TAP_TARGET)
                        .then(
                            if (onStepSelected == null) {
                                Modifier
                            } else {
                                Modifier
                                    .clickable { onStepSelected(index) }
                                    .semantics { contentDescription = jumpLabel }
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TRACK_HEIGHT)
                            .background(color, CircleShape)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stepLabels.getOrElse(currentIndex) { "" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    R.string.create_step_progress,
                    currentIndex + 1,
                    stepLabels.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (onStepSelected != null) {
            Text(
                text = stringResource(R.string.create_step_jump_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
