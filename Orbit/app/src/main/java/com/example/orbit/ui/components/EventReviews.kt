package com.example.orbit.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.orbit.R
import com.example.orbit.data.remote.ImageUrls
import com.example.orbit.domain.model.Rating
import com.example.orbit.ui.theme.warmShadow
import com.example.orbit.ui.util.formatEventDate

/** Koliko utisaka stoji direktno na detalju; ostali se otvaraju u listi odozdo */
private const val INLINE_REVIEWS = 3

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
    // Kljuc je sacuvano stanje: kad utisak stigne sa servera, forma se popuni njime
    var stars by remember(myStars) { mutableIntStateOf(myStars) }
    var comment by remember(myReview?.comment) { mutableStateOf(myReview?.comment.orEmpty()) }
    var image by remember(myReview?.imagePath) { mutableStateOf(myReview?.imagePath) }

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

/**
 * F-40: utisci drugih gostiju. Na detalju stoje samo najnoviji, jer lista moze da naraste;
 * ostali se otvaraju u listi odozdo, kao spisak gostiju.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewList(reviews: List<Rating>) {
    var showAll by remember { mutableStateOf(false) }

    if (reviews.isEmpty()) {
        Text(
            text = stringResource(R.string.reviews_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA),
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        reviews.take(INLINE_REVIEWS).forEach { ReviewCard(it) }

        if (reviews.size > INLINE_REVIEWS) {
            TextButton(onClick = { showAll = true }) {
                Text(stringResource(R.string.reviews_show_all, reviews.size))
            }
        }
    }

    if (showAll) {
        ModalBottomSheet(onDismissRequest = { showAll = false }) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = reviews, key = { it.id }) { ReviewCard(it) }
            }
        }
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
private fun SmallStars(value: Int) {
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
