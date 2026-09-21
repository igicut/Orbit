package com.example.orbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.orbit.R
import com.example.orbit.data.remote.ImageUrls

/** Jedna fotografija zauzima celu sirinu; vise njih ide u red iste velicine */
private val SINGLE_PHOTO_HEIGHT = 200.dp
private val TILE_WIDTH = 240.dp
private val TILE_HEIGHT = 180.dp

/** Isti zaobljeni oblik kao cipovi filtera */
private val PHOTO_SHAPE = RoundedCornerShape(16.dp)

/** Pozadina pregleda; tamna da slika bude u prvom planu, ali se vidi da je iznad ekrana */
private const val VIEWER_SCRIM_ALPHA = 0.92f

/**
 * Fotografije dogadjaja. Plocice imaju stalnu velicinu, pa uspravan plakat i siroka
 * panorama zauzimaju isto mesto i red ne skace dok se slike ucitavaju.
 * Dodir otvara celu, neisecenu fotografiju.
 */
@Composable
fun EventPhotoRow(paths: List<String>) {
    var opened by remember { mutableStateOf<String?>(null) }

    if (paths.size == 1) {
        PhotoTile(
            path = paths.first(),
            onClick = { opened = paths.first() },
            modifier = Modifier
                .fillMaxWidth()
                .height(SINGLE_PHOTO_HEIGHT),
        )
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(paths) { path ->
                PhotoTile(
                    path = path,
                    onClick = { opened = path },
                    modifier = Modifier.size(width = TILE_WIDTH, height = TILE_HEIGHT),
                )
            }
        }
    }

    opened?.let { PhotoViewerDialog(path = it, onDismiss = { opened = null }) }
}

/** Isecena plocica; pozadina drzi mesto dok slika ne stigne */
@Composable
fun PhotoTile(path: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageUrls.model(path),
        contentDescription = stringResource(R.string.photo_open),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(PHOTO_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    )
}

/**
 * Cela fotografija, bez secenja. Obican Dialog umesto novog ekrana: nema rute u navigaciji
 * i nema biblioteke za galeriju. Dodir bilo gde zatvara.
 */
@Composable
fun PhotoViewerDialog(path: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = VIEWER_SCRIM_ALPHA))
                .clickable(onClick = onDismiss),
        ) {
            AsyncImage(
                model = ImageUrls.model(path),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.photo_close),
                    tint = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }
}
