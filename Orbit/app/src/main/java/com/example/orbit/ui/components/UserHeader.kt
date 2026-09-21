package com.example.orbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.orbit.R
import com.example.orbit.domain.model.OrganiserRating
import com.example.orbit.ui.theme.orbitAccents
import com.example.orbit.ui.theme.warmShadow
import java.util.Locale

/** Koliko brend boja ulazi u pozadinu zaglavlja; isti odnos kao kod kartice dogadjaja */
private const val SOFT_TINT = 0.12f
private const val STRONG_TINT = 0.30f

/** Plocica sa brojem je skoro bela, kao bedzevi na kartici */
private const val TILE_ALPHA = 0.85f

/** Sporedni tekst na obojenoj pozadini; proveren na najjacoj boji (4.9:1) */
private const val SECONDARY_ALPHA = 0.8f

/**
 * Inicijal na prelazu brend boja. Profil nema fotografiju, pa ista osoba
 * svuda izgleda isto: na nalogu, na profilu i na kartici organizatora.
 */
@Composable
fun UserAvatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    val accents = MaterialTheme.orbitAccents

    Box(
        modifier = modifier
            .size(size)
            .border(width = 2.dp, color = MaterialTheme.colorScheme.surface, shape = CircleShape)
            .background(
                brush = Brush.linearGradient(listOf(accents.brandStart, accents.brandEnd)),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase(Locale.getDefault()),
            // Veci avatar nosi i vece slovo
            style = if (size >= 56.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accents.onCategory,
        )
    }
}

/**
 * Prelaz od plave ka zelenoj, kao okean i kopno na logotipu; zaglavlje naloga i AI kartica.
 * Boja je najjaca dole levo, kao na kartici dogadjaja.
 */
@Composable
internal fun brandBackground(): Brush {
    val accents = MaterialTheme.orbitAccents
    val surface = MaterialTheme.colorScheme.surface
    return Brush.linearGradient(
        colors = listOf(lerp(surface, accents.brandEnd, SOFT_TINT), lerp(surface, accents.brandStart, STRONG_TINT)),
        start = Offset(Float.POSITIVE_INFINITY, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY),
    )
}

/**
 * Zaglavlje naloga i profila: avatar, ime, podnaslov i red sa brojevima.
 * Pozadina je prelaz od zelene ka plavoj, kao kopno i okean na logotipu.
 *
 * @param onEdit olovka pored imena; samo na sopstvenom nalogu
 * @param stats plocice sa brojevima, npr. StatTile sa Modifier.weight(1f)
 */
@Composable
fun UserHeaderCard(
    name: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onEdit: (() -> Unit)? = null,
    stats: @Composable RowScope.() -> Unit,
) {
    val accents = MaterialTheme.orbitAccents
    val surface = MaterialTheme.colorScheme.surface
    val shape = MaterialTheme.shapes.large

    Column(
        modifier = modifier
            .fillMaxWidth()
            .warmShadow(elevation = 6.dp, shape = shape, color = lerp(accents.shadow, accents.brandStart, 0.5f))
            .clip(shape)
            .background(brandBackground())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UserAvatar(name = name, size = 64.dp)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (onEdit != null) {
                FilledTonalIconButton(
                    onClick = onEdit,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.account_edit_name))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = stats,
        )
    }
}

/** Ocena organizatora kao plocica; bez ocena stoji crtica, ne nula */
@Composable
fun RatingStatTile(rating: OrganiserRating?, modifier: Modifier = Modifier) {
    StatTile(
        value = rating?.let { String.format(Locale.getDefault(), "%.1f", it.average) } ?: "–",
        label = if (rating == null) {
            stringResource(R.string.stat_rating_none)
        } else {
            pluralStringResource(R.plurals.rating_count, rating.count, rating.count)
        },
        icon = Icons.Filled.Star,
        modifier = modifier,
    )
}

/** Jedan broj u zaglavlju: vrednost krupno, naziv sitno ispod; vrednost je vaznija */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = TILE_ALPHA),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
