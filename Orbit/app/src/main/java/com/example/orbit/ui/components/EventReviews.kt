package com.example.orbit.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.orbit.R
import com.example.orbit.data.remote.ImageUrls
import com.example.orbit.domain.model.Event
import com.example.orbit.domain.model.Rating
import com.example.orbit.ui.theme.warmShadow
import com.example.orbit.ui.util.formatEventDate
import java.util.Locale
import kotlin.math.ceil

/** Isto kao MAX_COMMENT_LENGTH na serveru, da se duzi tekst ne sece bez upozorenja */
private const val MAX_COMMENT_LENGTH = 1000

/** Sporedni tekst (datum) je ista boja teksta, samo tisa */
private const val SECONDARY_ALPHA = 0.6f

/**
 * F-40: forma za sopstveni utisak. Zvezdice, komentar i slika se salju jednim dugmetom,
 * jer server menja sva tri polja odjednom.
 */
@Composable
fun ReviewForm(
    myReview: Rating?,
    myStars: Int,
    isPending: Boolean,
    error: Int?,
    onSubmit: (value: Int, comment: String, image: String?) -> Unit,
) {
    // Kljuc je sacuvano stanje: kad utisak stigne sa servera, forma se popuni njime.
    // rememberSaveable cuva zapocet komentar i dok je tab Utisci sakriven
    var stars by rememberSaveable(myStars) { mutableIntStateOf(myStars) }
    var comment by rememberSaveable(myReview?.comment) { mutableStateOf(myReview?.comment.orEmpty()) }
    var image by rememberSaveable(myReview?.imagePath) { mutableStateOf(myReview?.imagePath) }

    // Photo Picker, bez dozvole za skladiste; slika se salje tek na Objavi
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) image = uri.toString()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.review_your),
            style = MaterialTheme.typography.titleSmall,
        )

        RatingBar(rating = stars, onRatingChange = { stars = it }, enabled = !isPending)

        OutlinedTextField(
            value = comment,
            onValueChange = { if (it.length <= MAX_COMMENT_LENGTH) comment = it },
            label = { Text(stringResource(R.string.review_comment_hint)) },
            minLines = 2,
            maxLines = 5,
            enabled = !isPending,
            modifier = Modifier.fillMaxWidth(),
        )

        val shownImage = image
        if (shownImage != null) {
            Box {
                AsyncImage(
                    model = ImageUrls.model(shownImage),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                IconButton(
                    onClick = { image = null },
                    enabled = !isPending,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.review_remove_photo))
                }
            }
        } else {
            OutlinedButton(
                onClick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !isPending,
            ) {
                Text(stringResource(R.string.review_add_photo))
            }
        }

        error?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Bez zvezdice nema utiska; komentar i slika su dodatak oceni
        Button(
            onClick = { onSubmit(stars, comment, image) },
            enabled = stars > 0 && !isPending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(if (myReview != null) R.string.review_update else R.string.review_submit)
            )
        }
    }
}

/** F-40: utisci drugih gostiju; tab Utisci je njihovo mesto, pa se prikazuju svi */
@Composable
fun ReviewList(reviews: List<Rating>) {
    if (reviews.isEmpty()) {
        Text(
            text = stringResource(R.string.reviews_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        reviews.forEach { ReviewCard(it) }
    }
}

/** Jedan utisak: ko, kada, koliko zvezdica, pa komentar i slika ako postoje */
@Composable
private fun ReviewCard(review: Rating) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .warmShadow(elevation = 2.dp, shape = MaterialTheme.shapes.medium),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = review.authorName ?: stringResource(R.string.review_unknown_author),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatEventDate(review.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
                )
            }

            SmallStars(value = review.value)

            review.comment?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium)
            }

            review.imagePath?.let { path ->
                var showFull by remember { mutableStateOf(false) }
                PhotoTile(
                    path = path,
                    onClick = { showFull = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
                if (showFull) PhotoViewerDialog(path = path, onDismiss = { showFull = false })
            }
        }
    }
}

/** Sitne zvezdice samo za prikaz; namerno manje od RatingBar-a da se ne pomesaju sa unosom */
@Composable
internal fun SmallStars(value: Int) {
    Row {
        (1..5).forEach { star ->
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = if (star <= value) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Polovina se ne racuna kao puna zvezdica: 4.5 daje cetiri, 4.6 pet */
internal fun filledStars(average: Float): Int = ceil(average - 0.5f).toInt()

/**
 * Tab "Utisci": ukupna ocena, sopstveni utisak i utisci drugih gostiju.
 * Pre pocetka niko nije mogao da dodje, pa ni da oceni; tada stoji objasnjenje.
 */
@Composable
fun EventReviewsTab(
    event: Event,
    now: Long,
    isOwner: Boolean,
    hasAttended: Boolean,
    reviews: List<Rating>,
    currentUserId: String,
    myRating: Int,
    isReviewPending: Boolean,
    ratingError: Int?,
    onSubmitReview: (value: Int, comment: String, image: String?) -> Unit,
) {
    if (event.startTime > now) {
        EmptyView(
            title = stringResource(R.string.reviews_before_start_title),
            subtitle = stringResource(R.string.reviews_before_start_subtitle),
            modifier = Modifier.padding(vertical = 32.dp),
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RatingSummary(average = event.avgRating, count = event.ratingCount, reviews = reviews)

        // Ocenjuju samo potvrdjeni dolasci, server isto proverava; organizator samo cita
        if (!isOwner) {
            if (hasAttended) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .warmShadow(elevation = 2.dp, shape = MaterialTheme.shapes.large),
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        ReviewForm(
                            myReview = reviews.firstOrNull { it.userId == currentUserId },
                            myStars = myRating,
                            isPending = isReviewPending,
                            error = ratingError,
                            onSubmit = onSubmitReview,
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.rating_requires_attendance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Sopstveni utisak je vec u formi iznad, u listi bi stajao dvaput
        ReviewList(reviews = reviews.filter { it.userId != currentUserId })
    }
}

/**
 * Prosek krupno, zvezdice i broj ocena levo; desno koliko je bilo petica, cetvorki...
 * Raspodela se racuna iz utisaka koji su stigli, prosek i broj dolaze sa servera.
 */
@Composable
private fun RatingSummary(average: Float, count: Int, reviews: List<Rating>) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .warmShadow(elevation = 2.dp, shape = MaterialTheme.shapes.large),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (count == 0) "–" else String.format(Locale.getDefault(), "%.1f", average),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                SmallStars(value = filledStars(average))
                Text(
                    text = if (count == 0) {
                        stringResource(R.string.detail_rating_none)
                    } else {
                        pluralStringResource(R.plurals.rating_count, count, count)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                (5 downTo 1).forEach { star ->
                    val starCount = reviews.count { it.value == star }
                    StarBar(
                        star = star,
                        fraction = if (reviews.isEmpty()) 0f else starCount.toFloat() / reviews.size,
                    )
                }
            }
        }
    }
}

/** Jedan red raspodele: broj zvezdica i traka u boji zvezdica */
@Composable
private fun StarBar(star: Int, fraction: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = star.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { fraction },
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
        )
    }
}
