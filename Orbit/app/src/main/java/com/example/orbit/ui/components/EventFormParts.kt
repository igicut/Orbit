package com.example.orbit.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.orbit.R
import com.example.orbit.data.remote.ImageUrls
import com.example.orbit.ui.theme.warmShadow

/** Krug iza ikonice, isti kao na detalju dogadjaja */
private const val ICON_CIRCLE_ALPHA = 0.15f

/** Blaga podloga i ivica za polja koja se biraju dodirom, ne kucanjem */
private const val PICKER_FILL_ALPHA = 0.08f
private const val PICKER_BORDER_ALPHA = 0.40f

/** Tamni krug ispod belog ✕, da se vidi i na svetloj fotografiji */
private const val REMOVE_SCRIM_ALPHA = 0.6f

/** Plocica fotografije; ista velicina za slike i za dugmad za dodavanje */
private val PHOTO_TILE = 96.dp

/**
 * Jedna celina forme kao bela kartica: ikonica u boji forme, naslov i polja ispod.
 * `trailing` je mesto desno od naslova, npr. brojac fotografija.
 */
@Composable
fun FormSection(
    icon: Painter,
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .warmShadow(elevation = 2.dp, shape = MaterialTheme.shapes.large),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconCircle(icon = icon, color = accent)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

/** Ikonica u obojenom krugu; ikonica je puna boja, krug samo nagovestaj */
@Composable
fun IconCircle(icon: Painter, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(color = color.copy(alpha = ICON_CIRCLE_ALPHA), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
    }
}

/**
 * Polje koje se bira dodirom (datum, vreme): ikonica, naziv, vrednost i strelica.
 * Izgleda drugacije od polja za kucanje, da se ne ocekuje tastatura.
 */
@Composable
fun PickerRow(
    icon: Painter,
    label: String,
    value: String?,
    placeholder: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = accent.copy(alpha = PICKER_FILL_ALPHA),
        border = BorderStroke(
            width = 1.dp,
            color = if (isError) MaterialTheme.colorScheme.error else accent.copy(alpha = PICKER_BORDER_ALPHA),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painter = icon, contentDescription = null, tint = accent)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * F-31: AI predlog na brend pozadini, kao zaglavlje naloga; izdvaja se od obicnih polja.
 * Greska stoji ispod kartice, na kremu, gde je citljiva.
 */
@Composable
fun AiSuggestCard(
    isSuggesting: Boolean,
    @StringRes error: Int?,
    onSuggest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .warmShadow(elevation = 2.dp, shape = MaterialTheme.shapes.large)
                .clip(MaterialTheme.shapes.large)
                .background(brandBackground())
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.create_ai_suggest),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.create_ai_suggest_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }

            FilledTonalButton(
                onClick = onSuggest,
                enabled = !isSuggesting,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (isSuggesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.create_ai_suggesting))
                } else {
                    Text(stringResource(R.string.create_ai_suggest_action))
                }
            }
        }

        error?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Izabrane fotografije u redu, sa ✕ na uglu; prva je naslovna i nju pokazuje kartica.
 * Na kraju reda su plocice za galeriju i kameru, dok ima mesta.
 */
@Composable
fun PhotoStrip(
    uris: List<String>,
    canAddMore: Boolean,
    accent: Color,
    onRemove: (String) -> Unit,
    onAddFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(items = uris, key = { _, uri -> uri }) { index, uri ->
            PhotoThumb(uri = uri, isCover = index == 0, onRemove = { onRemove(uri) })
        }

        if (canAddMore) {
            item(key = "gallery") {
                AddPhotoTile(
                    icon = rememberVectorPainter(Icons.Filled.Add),
                    label = stringResource(R.string.create_add_from_gallery),
                    accent = accent,
                    onClick = onAddFromGallery,
                )
            }
            item(key = "camera") {
                AddPhotoTile(
                    icon = painterResource(R.drawable.ic_photo_camera),
                    label = stringResource(R.string.create_take_photo),
                    accent = accent,
                    onClick = onTakePhoto,
                )
            }
        }
    }
}

@Composable
private fun PhotoThumb(uri: String, isCover: Boolean, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(PHOTO_TILE)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = ImageUrls.model(uri),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )

        // Dodirna meta je veca od kruzica, da se ✕ lako pogodi
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(44.dp)
                .clickable(role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color = MaterialTheme.colorScheme.scrim.copy(alpha = REMOVE_SCRIM_ALPHA), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.create_remove_photo),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (isCover) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.create_photo_cover),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun AddPhotoTile(icon: Painter, label: String, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = accent.copy(alpha = PICKER_FILL_ALPHA),
        border = BorderStroke(1.dp, accent.copy(alpha = PICKER_BORDER_ALPHA)),
        modifier = Modifier.size(PHOTO_TILE),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(painter = icon, contentDescription = null, tint = accent)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Izbor vidljivosti kao kartica sa objasnjenjem; izabrana nosi boju i kvacicu.
 * Dve kartice stoje jedna pored druge, pa se razlika vidi odmah.
 */
@Composable
fun VisibilityOption(
    icon: Painter,
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) accent.copy(alpha = PICKER_FILL_ALPHA) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Mala pilula sa brojem, desno od naslova celine */
@Composable
fun CountPill(text: String, accent: Color) {
    Surface(shape = CircleShape, color = accent.copy(alpha = ICON_CIRCLE_ALPHA)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** Ikonica i tekst u dugmetu, da Nazad, Dalje i Sacuvaj izgledaju isto */
@Composable
fun ButtonIconLabel(icon: Painter, text: String, iconAfterText: Boolean = false) {
    if (!iconAfterText) {
        Icon(painter = icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
    }
    Text(text)
    if (iconAfterText) {
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Icon(painter = icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    }
}
