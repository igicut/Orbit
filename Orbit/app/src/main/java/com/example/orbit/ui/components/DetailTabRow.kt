package com.example.orbit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.orbit.R
import com.example.orbit.ui.theme.orbitAccents
import com.example.orbit.ui.theme.warmShadow

/** Visina jednog taba; sa okvirom od 4 dp daje 48 dp za prst */
private val TAB_HEIGHT = 40.dp

/** Tabovi na detalju dogadjaja, u redosledu kojim stoje na ekranu */
enum class DetailTab {
    OVERVIEW,
    REVIEWS,
}

/**
 * Dva taba kao pilula: obojena pilula klizi ispod izabranog naziva.
 * Izabran tab cuva ekran; ova funkcija samo crta i javlja klik.
 */
@Composable
fun DetailTabRow(
    selected: DetailTab,
    reviewCount: Int,
    accent: Color,
    onSelect: (DetailTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .warmShadow(elevation = 2.dp, shape = CircleShape),
    ) {
        BoxWithConstraints(modifier = Modifier.padding(4.dp)) {
            val tabWidth = maxWidth / DetailTab.entries.size

            // Pilula ide do izabranog taba umesto da skoci
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selected.ordinal,
                label = "tabIndicator",
            )
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .height(TAB_HEIGHT)
                    .background(color = accent, shape = CircleShape),
            )

            Row(modifier = Modifier.selectableGroup()) {
                DetailTab.entries.forEach { tab ->
                    val isSelected = tab == selected
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.orbitAccents.onCategory
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        label = "tabText",
                    )

                    Box(
                        modifier = Modifier
                            .width(tabWidth)
                            .height(TAB_HEIGHT)
                            .clip(CircleShape)
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(tab) },
                                role = Role.Tab,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tabLabel(tab, reviewCount),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun tabLabel(tab: DetailTab, reviewCount: Int): String = when (tab) {
    DetailTab.OVERVIEW -> stringResource(R.string.detail_tab_overview)
    DetailTab.REVIEWS ->
        if (reviewCount > 0) stringResource(R.string.detail_tab_reviews_count, reviewCount)
        else stringResource(R.string.reviews_title)
}
