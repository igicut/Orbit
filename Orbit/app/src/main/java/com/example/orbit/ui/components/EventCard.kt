package com.example.orbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.orbit.R
import com.example.orbit.data.remote.ImageUrls
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.EventCategory
import com.example.orbit.domain.model.EventStatus
import com.example.orbit.domain.model.UserLocation
import com.example.orbit.domain.model.Visibility
import com.example.orbit.ui.common.iconRes
import com.example.orbit.ui.common.labelRes
import com.example.orbit.ui.theme.orbitAccents
import com.example.orbit.ui.theme.warmShadow
import com.example.orbit.ui.util.formatDayOfMonth
import com.example.orbit.ui.util.formatMonthShort
import com.example.orbit.ui.util.formatWeekdayTime

/** Oko 2.6:1 na telefonu; dovoljno da fotografija nosi karticu, a da lista ne postane galerija */
private val PHOTO_HEIGHT = 144.dp

/** Pozadina kartice vidi se i kao ram oko fotografije */
private val FRAME = 8.dp

/** Unutrasnji ugao prati spoljni: 20 dp kartice minus ram od 8 dp */
private val PHOTO_SHAPE = RoundedCornerShape(12.dp)

/** Koliko boje kategorije ulazi u prelaz pozadine; tekst je proveren i na jacem kraju */
private const val SOFT_TINT = 0.08f
private const val STRONG_TINT = 0.30f

/** Pozadina dok fotografija stize, ili ako je nema */
private const val PLACEHOLDER_TINT = 0.45f

/** Sporedni tekst je ista boja kao naslov, samo tisa */
private const val META_ALPHA = 0.8f

/** Otkazan dogadjaj gubi boje, da se razlika vidi i bez citanja bedza */
internal val GRAYSCALE_FILTER = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Boja kartice i detalja: boja kategorije, a za otkazan dogadjaj neutralna */
@Composable
internal fun eventAccent(event: Event): Color =
    if (event.status == EventStatus.CANCELLED) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.orbitAccents.forCategory(event.category)
    }

/** Pozadina kartice i vrha detalja; boja je najjaca dole levo, ispod teksta, ne iza fotografije */
@Composable
internal fun eventBackground(accent: Color): Brush {
    val surface = MaterialTheme.colorScheme.surface
    return Brush.linearGradient(
        colors = listOf(lerp(surface, accent, SOFT_TINT), lerp(surface, accent, STRONG_TINT)),
        start = Offset(Float.POSITIVE_INFINITY, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY),
    )
}

/**
 * Jedina kartica dogadjaja u aplikaciji: lista, planovi, profil i mapa.
 * Fotografija je gore, a pozadina je prelaz u boji kategorije.
 *
 * @param note zamenjuje bedzeve, npr. dolazak i ocena u istoriji
 * @param distanceFrom lokacija korisnika; dodaje bedz sa udaljenoscu
 * @param onDismiss dugme ✕ na fotografiji, samo na mapi
 */
@Composable
fun EventCard(
    event: Event,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    organiserName: String? = null,
    note: String? = null,
    distanceFrom: UserLocation? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val isCancelled = event.status == EventStatus.CANCELLED
    val accent = eventAccent(event)
    val shape = MaterialTheme.shapes.large

    Column(
        modifier = modifier
            .fillMaxWidth()
            .warmShadow(
                elevation = 6.dp,
                shape = shape,
                color = lerp(MaterialTheme.orbitAccents.shadow, accent, 0.5f),
            )
            .clip(shape)
            .background(eventBackground(accent))
            .clickable(onClickLabel = stringResource(R.string.card_open), onClick = onClick)
            .padding(FRAME),
    ) {
        CardPhoto(event = event, accent = accent, isCancelled = isCancelled, onDismiss = onDismiss)

        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CardTitle(event)
            CardMeta(event = event, organiserName = organiserName)

            Spacer(modifier = Modifier.height(4.dp))

            if (note != null) {
                NoteBadge(note)
            } else {
                EventMetaBadges(event = event, distanceFrom = distanceFrom)
            }
        }
    }
}

/**
 * Fotografija sa datumom i kategorijom preko nje. Kutija ima stalnu visinu,
 * pa lista ne skace dok slike stizu.
 */
@Composable
private fun CardPhoto(
    event: Event,
    accent: Color,
    isCancelled: Boolean,
    onDismiss: (() -> Unit)?,
) {
    val surface = MaterialTheme.colorScheme.surface
    val photo = event.imageUris.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PHOTO_HEIGHT)
            .clip(PHOTO_SHAPE),
    ) {
        // Ispod fotografije; vidi se dok ona stize ili ako ucitavanje ne uspe
        PhotoPlaceholder(category = event.category, accent = accent, modifier = Modifier.matchParentSize())

        if (photo != null) {
            AsyncImage(
                model = ImageUrls.model(photo),
                // Ukras u listi; fotografije sa opisom su na detalju
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = if (isCancelled) GRAYSCALE_FILTER else null,
                modifier = Modifier.matchParentSize(),
            )
        }

        DateTile(
            millis = event.startTime,
            accent = accent,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        )

        CategoryPill(
            category = event.category,
            color = accent,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
        )

        if (onDismiss != null) {
            Surface(
                shape = CircleShape,
                color = surface.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.map_preview_close),
                    )
                }
            }
        }
    }
}

/** Prelaz u boji kategorije sa velikom ikonicom; stoji umesto fotografije */
@Composable
internal fun PhotoPlaceholder(category: EventCategory, accent: Color, modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(lerp(surface, accent, PLACEHOLDER_TINT), accent))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(category.iconRes()),
            contentDescription = null,
            tint = MaterialTheme.orbitAccents.onCategory,
            modifier = Modifier.size(48.dp),
        )
    }
}

/** Dan i mesec kao na ulaznici; godina se ne pise, dogadjaji su blizu */
@Composable
internal fun DateTile(millis: Long, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 48.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatDayOfMonth(millis),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatMonthShort(millis),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

/** Puna boja samo na maloj povrsini; tekst nosi kategoriju i za citac ekrana */
@Composable
internal fun CategoryPill(category: EventCategory, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = color,
        contentColor = MaterialTheme.orbitAccents.onCategory,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(category.iconRes()),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(category.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CardTitle(event: Event) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (event.visibility == Visibility.PRIVATE) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = stringResource(event.visibility.labelRes()),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = META_ALPHA),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp),
            )
        }
    }
}

/** Dan u nedelji, sat i organizator; adresa je na detalju */
@Composable
private fun CardMeta(event: Event, organiserName: String?) {
    val metaColor = MaterialTheme.colorScheme.onSurface.copy(alpha = META_ALPHA)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.DateRange,
            contentDescription = null,
            tint = metaColor,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = formatWeekdayTime(event.startTime),
            style = MaterialTheme.typography.bodySmall,
            color = metaColor,
            maxLines = 1,
        )
        organiserName?.let { name ->
            Text(
                text = "· " + stringResource(R.string.event_organised_by, name),
                style = MaterialTheme.typography.bodySmall,
                color = metaColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
